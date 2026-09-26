/*
 * File: ReservationQueryService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enforces Member 4 read authorization, filters and server-clock semantics.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Reservations;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Services;

public sealed class ReservationQueryService(
    IReservationReadRepository reservations, IUserRepository users, TimeProvider clock) : IReservationQueryService
{
    public async Task<ReservationPageResponse> QueryAsync(string actorNic, ReservationReadView view,
        ReservationSearchRequest request, CancellationToken ct = default)
    {
        // Scope before querying so neither records nor backfill errors disclose another Prosumer's data.
        var actor = await ActorAsync(actorNic, ct);
        ArgumentNullException.ThrowIfNull(request);
        RequestValidation.EnsureValid(request);
        if (!Enum.IsDefined(view)) throw new BadRequestException("Unsupported reservation view.");
        var requestedNic = Clean(request.ProsumerNic)?.ToUpperInvariant();
        if (actor.Role == UserRole.Prosumer && requestedNic is not null && requestedNic != actor.Nic)
            throw new ForbiddenException("Prosumers may query only their own reservations.");
        var nic = actor.Role == UserRole.Prosumer ? actor.Nic : requestedNic;
        var status = Clean(request.Status) is string value ? Enum.Parse<ReservationStatus>(value, true) : (ReservationStatus?)null;
        var filter = new ReservationReadFilter(view, nic, Clean(request.ReservationId), Clean(request.StationId),
            status, request.FromUtc, request.ToUtc, clock.GetUtcNow().UtcDateTime, request.Page, request.PageSize);
        var rows = await reservations.QueryAsync(filter, ct);
        var items = rows.Take(request.PageSize).Select(Response).ToArray();
        return new ReservationPageResponse(items, request.Page, request.PageSize, rows.Count > request.PageSize);
    }

    public async Task<ReservationDashboardSummaryResponse> DashboardAsync(string actorNic, CancellationToken ct = default)
    {
        // Capture one server UTC instant for both live counts; clients cannot select identity or time here.
        var actor = await ActorAsync(actorNic, ct);
        var now = clock.GetUtcNow().UtcDateTime;
        var counts = await reservations.CountAsync(actor.Role == UserRole.Prosumer ? actor.Nic : null, now, ct);
        return new ReservationDashboardSummaryResponse(counts.Pending, counts.ApprovedFuture, now);
    }

    private async Task<User> ActorAsync(string nic, CancellationToken ct)
    {
        // Preserve the existing reservation service's current-account and role checks outside MVC too.
        if (string.IsNullOrWhiteSpace(nic)) throw new UnauthorizedException("Authentication is required.");
        var actor = await users.GetByNicAsync(nic.Trim().ToUpperInvariant(), ct)
            ?? throw new UnauthorizedException("Authenticated account is unavailable.");
        if (actor.Status != UserStatus.Active || actor.Role is not (UserRole.Prosumer or UserRole.GridOperator))
            throw new ForbiddenException("An active Prosumer or GridOperator account is required.");
        return actor;
    }

    private static ReservationResponse Response(EnergyReservation row)
    {
        // Retain Member 3's summary and fail-closed legacy policy without invoking any write rules.
        if (row.ScheduledStartAtUtc is not DateTime start || row.ScheduledEndAtUtc is not DateTime end
            || start.Kind != DateTimeKind.Utc || end.Kind != DateTimeKind.Utc || end <= start)
            throw new ConflictException("Reservation schedule requires verified backfill before use.");
        return new ReservationResponse(row.ReservationId, row.ProsumerNic, row.StationId, row.SlotId,
            row.EnergyAmountKwh, start, end, row.Status, row.CreatedAtUtc, row.UpdatedAtUtc);
    }

    private static string? Clean(string? value)
    {
        // Blank optional filters mean no filter, matching the established collection-list convention.
        return string.IsNullOrWhiteSpace(value) ? null : value.Trim();
    }
}
