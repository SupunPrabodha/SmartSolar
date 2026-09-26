/*
 * File: ReservationRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Implements conditional Mongo reservation writes without multi-document transactions.
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

public sealed class ReservationRepository : IReservationRepository
{
    private readonly IMongoCollection<EnergyReservation> _reservations;
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<SolarStation> _stations;
    private readonly IMongoCollection<User> _users;

    public ReservationRepository(IMongoDatabase database)
    {
        // Acknowledged standalone writes are essential to distinguish confirmed CAS misses.
        _reservations = database.GetCollection<EnergyReservation>(CollectionNames.Reservations).WithWriteConcern(WriteConcern.WMajority);
        _slots = database.GetCollection<EnergyBookingSlot>(CollectionNames.BookingSlots).WithWriteConcern(WriteConcern.WMajority);
        _stations = database.GetCollection<SolarStation>(CollectionNames.Stations);
        _users = database.GetCollection<User>(CollectionNames.Users).WithWriteConcern(WriteConcern.WMajority);
    }

    public async Task<IReadOnlyList<EnergyReservation>> ListAsync(
        ReservationStatus? status, string? prosumerNic, string? stationId, CancellationToken ct = default)
    {
        // Combine exact optional filters; stable newest-first ordering has no dashboard aggregation.
        var filters = Builders<EnergyReservation>.Filter;
        var filter = filters.Empty;
        if (status.HasValue) filter &= filters.Eq(x => x.Status, status.Value);
        if (prosumerNic is not null) filter &= filters.Eq(x => x.ProsumerNic, prosumerNic);
        if (stationId is not null) filter &= filters.Eq(x => x.StationId, stationId);
        return await _reservations.Find(filter).SortByDescending(x => x.CreatedAtUtc)
            .ThenBy(x => x.ReservationId).ToListAsync(ct);
    }

    public async Task<EnergyReservation?> GetAsync(string id, CancellationToken ct = default)
    {
        // Read the existing reservation schema by its stable string identifier.
        return await _reservations.Find(x => x.ReservationId == id).FirstOrDefaultAsync(ct);
    }

    public async Task<IReadOnlyList<EnergyReservation>> GetActiveAsync(string nic, CancellationToken ct = default)
    {
        // Match the existing Prosumer index prefix and only capacity-consuming states.
        return await _reservations.Find(x => x.ProsumerNic == nic &&
            (x.Status == ReservationStatus.Pending || x.Status == ReservationStatus.Approved)).ToListAsync(ct);
    }

    public async Task<IReadOnlyList<EnergyReservation>> GetActiveBySlotAsync(string slotId, CancellationToken ct = default)
    {
        // Find active reservations consuming energy capacity on this slot.
        return await _reservations.Find(x => x.SlotId == slotId &&
            (x.Status == ReservationStatus.Pending || x.Status == ReservationStatus.Approved)).ToListAsync(ct);
    }

    public async Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct = default)
    {
        // Read a slot without taking ownership of station/slot CRUD.
        return await _slots.Find(x => x.SlotId == id).FirstOrDefaultAsync(ct);
    }

    public async Task<IReadOnlyList<EnergyBookingSlot>> GetActiveSlotsAsync(CancellationToken ct = default)
    {
        // Read active slots with remaining capacity.
        return await _slots.Find(x => x.IsActive && x.AvailableSlots > 0).ToListAsync(ct);
    }

    public async Task<SolarStation?> GetStationAsync(string id, CancellationToken ct = default)
    {
        // Resolve the station referenced by the persisted slot.
        return await _stations.Find(x => x.StationId == id).FirstOrDefaultAsync(ct);
    }

    public async Task<bool> TryLockAsync(string nic, string token, CancellationToken ct = default)
    {
        // No expiry: an abandoned operation must be reconciled before a new writer enters.
        var result = await _users.UpdateOneAsync(
            x => x.Nic == nic && x.Role == UserRole.Prosumer && x.ReservationWriteLock == null,
            Builders<User>.Update.Set(x => x.ReservationWriteLock, token), cancellationToken: ct);
        return result.ModifiedCount == 1;
    }

    public async Task UnlockAsync(string nic, string token, CancellationToken ct = default)
    {
        // An unrelated writer can never release somebody else's lock.
        var result = await _users.UpdateOneAsync(x => x.Nic == nic && x.ReservationWriteLock == token,
            Builders<User>.Update.Unset(x => x.ReservationWriteLock), cancellationToken: ct);
        if (result.ModifiedCount != 1)
            throw new ConflictException("Reservation lock requires reconciliation.");
    }

    public async Task<bool> TryAcquireCapacityAsync(EnergyBookingSlot expected, CancellationToken ct = default)
    {
        // The conditional decrement prevents overbooking even across processes and different Prosumers.
        var filter = Builders<EnergyBookingSlot>.Filter.Where(x =>
            x.SlotId == expected.SlotId && x.StationId == expected.StationId && x.IsActive &&
            x.StartAtUtc == expected.StartAtUtc && x.EndAtUtc == expected.EndAtUtc &&
            x.TotalSlots == expected.TotalSlots && x.AvailableSlots > 0);
        filter &= new BsonDocument("$expr", new BsonDocument("$lte", new BsonArray { "$AvailableSlots", "$TotalSlots" }));
        var result = await _slots.UpdateOneAsync(filter,
            Builders<EnergyBookingSlot>.Update.Inc(x => x.AvailableSlots, -1), cancellationToken: ct);
        return result.ModifiedCount == 1;
    }

    public async Task<bool> ReleaseCapacityAsync(string slotId, CancellationToken ct = default)
    {
        // Release also works for inactive slots, but cannot exceed configured total capacity.
        var filter = Builders<EnergyBookingSlot>.Filter.Where(x => x.SlotId == slotId && x.AvailableSlots >= 0);
        filter &= new BsonDocument("$expr", new BsonDocument("$lt", new BsonArray { "$AvailableSlots", "$TotalSlots" }));
        var result = await _slots.UpdateOneAsync(filter,
            Builders<EnergyBookingSlot>.Update.Inc(x => x.AvailableSlots, 1), cancellationToken: ct);
        return result.ModifiedCount == 1;
    }

    public async Task InsertAsync(EnergyReservation reservation, CancellationToken ct = default)
    {
        // The caller treats any exception as ambiguous and keeps acquired capacity plus the user lock.
        await _reservations.InsertOneAsync(reservation, cancellationToken: ct);
    }

    public async Task<bool> TryReplaceAsync(
        EnergyReservation expected,
        EnergyReservation replacement,
        CancellationToken ct = default)
    {
        // CAS detects concurrent changes while updating all lifecycle fields.
        var filter = Builders<EnergyReservation>.Filter.Where(x =>
            x.ReservationId == expected.ReservationId
            && x.ProsumerNic == expected.ProsumerNic
            && x.StationId == expected.StationId
            && x.SlotId == expected.SlotId
            && x.Status == expected.Status
            && x.EnergyAmountKwh == expected.EnergyAmountKwh
            && x.UpdatedAtUtc == expected.UpdatedAtUtc
            && x.ScheduledStartAtUtc == expected.ScheduledStartAtUtc
            && x.ScheduledEndAtUtc == expected.ScheduledEndAtUtc
            && x.QrToken == expected.QrToken
            && x.QrTokenHash == expected.QrTokenHash
            && x.QrIssuedAtUtc == expected.QrIssuedAtUtc
            && x.CompletedAtUtc == expected.CompletedAtUtc
            && x.CompletedByOperatorNic == expected.CompletedByOperatorNic
            && x.RejectionRemark == expected.RejectionRemark);

        var update = Builders<EnergyReservation>.Update
            .Set(x => x.StationId, replacement.StationId)
            .Set(x => x.SlotId, replacement.SlotId)
            .Set(x => x.EnergyAmountKwh, replacement.EnergyAmountKwh)
            .Set(x => x.Status, replacement.Status)
            .Set(x => x.QrToken, replacement.QrToken)
            .Set(x => x.QrTokenHash, replacement.QrTokenHash)
            .Set(x => x.QrIssuedAtUtc, replacement.QrIssuedAtUtc)
            .Set(x => x.CompletedAtUtc, replacement.CompletedAtUtc)
            .Set(x => x.CompletedByOperatorNic, replacement.CompletedByOperatorNic)
            .Set(x => x.RejectionRemark, replacement.RejectionRemark)
            .Set(x => x.ScheduledStartAtUtc, replacement.ScheduledStartAtUtc)
            .Set(x => x.ScheduledEndAtUtc, replacement.ScheduledEndAtUtc)
            .Set(x => x.UpdatedAtUtc, replacement.UpdatedAtUtc);

        var result = await _reservations.UpdateOneAsync(
            filter,
            update,
            cancellationToken: ct);

        return result.MatchedCount == 1;
    }
    public async Task<EnergyReservation?> GetByQrHashAsync(string qrTokenHash, CancellationToken ct = default)
    {
        // Lookup an authoritative reservation by its deterministic SHA-256 token hash.
        if (string.IsNullOrWhiteSpace(qrTokenHash)) return null;
        return await _reservations.Find(x => x.QrTokenHash == qrTokenHash).FirstOrDefaultAsync(ct);
    }

    public async Task<bool> TryUpdateQrHashAsync(
        string reservationId, string? expectedHash, string newHash, DateTime issuedAtUtc, DateTime updatedAtUtc, CancellationToken ct = default)
    {
        // Replaces the previous token hash conditionally so old tokens are immediately invalidated.
        var filter = Builders<EnergyReservation>.Filter.Where(x =>
            x.ReservationId == reservationId &&
            x.Status == ReservationStatus.Approved &&
            x.QrTokenHash == expectedHash);
        var update = Builders<EnergyReservation>.Update
            .Set(x => x.QrTokenHash, newHash)
            .Set(x => x.QrIssuedAtUtc, issuedAtUtc)
            .Set(x => x.UpdatedAtUtc, updatedAtUtc);
        var result = await _reservations.UpdateOneAsync(filter, update, cancellationToken: ct);
        return result.MatchedCount == 1;
    }

    public async Task<bool> TryCompleteReservationAsync(
        string reservationId, string qrTokenHash, string operatorNic, DateTime completedAtUtc, DateTime updatedAtUtc, CancellationToken ct = default)
    {
        // Atomically transitions from Approved to Completed ensuring single-completion protection.
        var filter = Builders<EnergyReservation>.Filter.Where(x =>
            x.ReservationId == reservationId &&
            x.Status == ReservationStatus.Approved &&
            x.QrTokenHash == qrTokenHash);
        var update = Builders<EnergyReservation>.Update
            .Set(x => x.Status, ReservationStatus.Completed)
            .Set(x => x.CompletedAtUtc, completedAtUtc)
            .Set(x => x.CompletedByOperatorNic, operatorNic)
            .Set(x => x.UpdatedAtUtc, updatedAtUtc);
        var result = await _reservations.UpdateOneAsync(filter, update, cancellationToken: ct);
        return result.MatchedCount == 1 && result.ModifiedCount == 1;
    }
}
