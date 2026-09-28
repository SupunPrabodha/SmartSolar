/*
 * File: ReservationReadRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Executes bounded reservation views and live counts in MongoDB without lifecycle writes.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Infrastructure.Persistence.Repositories;

public sealed class ReservationReadRepository(IMongoDatabase database) : IReservationReadRepository
{
    private readonly IMongoCollection<EnergyReservation> _reservations =
        database.GetCollection<EnergyReservation>(CollectionNames.Reservations);
    private static readonly FilterDefinitionBuilder<EnergyReservation> Filters = Builders<EnergyReservation>.Filter;

    public async Task<IReadOnlyList<EnergyReservation>> QueryAsync(ReservationReadFilter request, CancellationToken ct = default)
    {
        // Apply exact scope and category status before checking schedules, including legacy rows hidden by time filters.
        var filter = Scope(request.ProsumerNic);
        if (request.ReservationId is not null) filter &= Filters.Eq(x => x.ReservationId, request.ReservationId);
        if (request.StationId is not null) filter &= Filters.Eq(x => x.StationId, request.StationId);
        if (request.Status.HasValue) filter &= Filters.Eq(x => x.Status, request.Status.Value);
        if (request.View == ReservationReadView.Pending) filter &= Filters.Eq(x => x.Status, ReservationStatus.Pending);
        if (request.View == ReservationReadView.Current) filter &= Active();
        await EnsureSchedulesAsync(filter, ct);

        filter &= request.View switch
        {
            ReservationReadView.Current => Filters.Gt(x => x.ScheduledEndAtUtc, request.NowUtc),
            ReservationReadView.History => Filters.In(x => x.Status, new[]
                { ReservationStatus.Rejected, ReservationStatus.Cancelled, ReservationStatus.Completed })
                | (Active() & Filters.Lte(x => x.ScheduledEndAtUtc, request.NowUtc)),
            _ => Filters.Empty
        };
        if (request.FromUtc.HasValue) filter &= Filters.Gte(x => x.ScheduledStartAtUtc, request.FromUtc.Value);
        if (request.ToUtc.HasValue) filter &= Filters.Lte(x => x.ScheduledStartAtUtc, request.ToUtc.Value);

        var sort = request.View is ReservationReadView.Current or ReservationReadView.Pending
            ? Builders<EnergyReservation>.Sort.Ascending(x => x.ScheduledStartAtUtc).Ascending(x => x.ReservationId)
            : Builders<EnergyReservation>.Sort.Descending(x => x.ScheduledStartAtUtc).Ascending(x => x.ReservationId);
        // Mongo performs filtering, stable sorting and pagination; QR credentials are not fetched for booking views.
        return await _reservations.Find(filter).Sort(sort).Skip((request.Page - 1) * request.PageSize)
            .Limit(request.PageSize + 1)
            .Project<EnergyReservation>(Builders<EnergyReservation>.Projection.Exclude(x => x.QrToken).Exclude(x => x.QrTokenHash)).ToListAsync(ct);
    }

    public async Task<(long Pending, long ApprovedFuture)> CountAsync(string? prosumerNic, DateTime nowUtc, CancellationToken ct = default)
    {
        // Pending is status-only; an ambiguous Approved schedule must not silently undercount the future total.
        var scope = Scope(prosumerNic);
        var approved = scope & Filters.Eq(x => x.Status, ReservationStatus.Approved);
        await EnsureSchedulesAsync(approved, ct);
        var pendingCount = await _reservations.CountDocumentsAsync(
            scope & Filters.Eq(x => x.Status, ReservationStatus.Pending), cancellationToken: ct);
        var approvedCount = await _reservations.CountDocumentsAsync(
            approved & Filters.Gt(x => x.ScheduledStartAtUtc, nowUtc), cancellationToken: ct);
        return (pendingCount, approvedCount);
    }

    private async Task EnsureSchedulesAsync(FilterDefinition<EnergyReservation> scope, CancellationToken ct)
    {
        // Test only for existence, before pagination/time filtering; never rebuild snapshots from mutable slot data.
        var valid = Filters.Type(x => x.ScheduledStartAtUtc, BsonType.DateTime)
            & Filters.Type(x => x.ScheduledEndAtUtc, BsonType.DateTime)
            & new BsonDocument("$expr", new BsonDocument("$gt", new BsonArray { "$ScheduledEndAtUtc", "$ScheduledStartAtUtc" }));
        if (await _reservations.Find(scope & Filters.Not(valid)).Limit(1).Project(x => x.ReservationId).AnyAsync(ct))
            throw new ConflictException("Reservation schedule requires verified backfill before use.");
    }

    private static FilterDefinition<EnergyReservation> Scope(string? nic)
    {
        // Null is reserved for a service-authorized GridOperator view of all owners.
        return nic is null ? Filters.Empty : Filters.Eq(x => x.ProsumerNic, nic);
    }

    private static FilterDefinition<EnergyReservation> Active()
    {
        // Reuse the two existing active lifecycle values without introducing a new persisted status.
        return Filters.In(x => x.Status, new[] { ReservationStatus.Pending, ReservationStatus.Approved });
    }
}
