/*
 * File: IReservationReadRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines scoped persistence reads independently of Member 3 lifecycle writes.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Abstractions.Persistence;

// These are read categories, not persisted lifecycle statuses or client-supplied authority.
public enum ReservationReadView { Current, Pending, History, Search }

public sealed record ReservationReadFilter(
    ReservationReadView View, string? ProsumerNic, string? ReservationId, string? StationId,
    ReservationStatus? Status, DateTime? FromUtc, DateTime? ToUtc, DateTime NowUtc, int Page, int PageSize);

public interface IReservationReadRepository
{
    // Return at most PageSize + 1 records so the service can report HasMore without loading all matches.
    Task<IReadOnlyList<EnergyReservation>> QueryAsync(ReservationReadFilter filter, CancellationToken ct = default);
    Task<(long Pending, long ApprovedFuture)> CountAsync(string? prosumerNic, DateTime nowUtc, CancellationToken ct = default);
}
