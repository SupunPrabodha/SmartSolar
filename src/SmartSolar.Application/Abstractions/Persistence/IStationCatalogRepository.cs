/*
 * File: IStationCatalogRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Abstracts the existing station and slot collections.
 */
using SmartSolar.Domain.Entities;
namespace SmartSolar.Application.Abstractions.Persistence;

public interface IStationCatalogRepository
{
    Task<IReadOnlyList<SolarStation>> ListStationsAsync(bool includeInactive, string? search, CancellationToken ct);
    Task<SolarStation?> GetStationAsync(string id, CancellationToken ct);
    Task InsertStationAsync(SolarStation station, CancellationToken ct);
    Task<bool> UpdateStationAsync(SolarStation station, DateTime expected, CancellationToken ct);
    Task<IReadOnlyList<EnergyBookingSlot>> ListSlotsAsync(string stationId, bool includeInactive, CancellationToken ct);
    Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct);
    Task InsertSlotAsync(EnergyBookingSlot slot, CancellationToken ct);
    Task<bool> UpdateSlotAsync(EnergyBookingSlot slot, DateTime expected, CancellationToken ct);
}
/// <summary>Read-only protection queries: only Pending and Approved reservations are active.</summary>
public interface IReservationReferenceReader
{
    Task<bool> HasActiveStationReservationsAsync(string stationId, CancellationToken ct);
    Task<decimal> ActiveAllocatedEnergyAsync(string stationId, CancellationToken ct);
    Task<bool> HasActiveSlotReservationsAsync(string slotId, CancellationToken ct);
}
