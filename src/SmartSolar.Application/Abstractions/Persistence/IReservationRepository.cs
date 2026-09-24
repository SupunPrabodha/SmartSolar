/*
 * File: IReservationRepository.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines reservation aggregate reads and conditional standalone-safe writes.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Abstractions.Persistence;

public interface IReservationRepository
{
    Task<IReadOnlyList<EnergyReservation>> ListAsync(ReservationStatus? status, string? prosumerNic, string? stationId, CancellationToken ct = default);
    Task<EnergyReservation?> GetAsync(string id, CancellationToken ct = default);
    Task<IReadOnlyList<EnergyReservation>> GetActiveAsync(string nic, CancellationToken ct = default);
    Task<IReadOnlyList<EnergyReservation>> GetActiveBySlotAsync(string slotId, CancellationToken ct = default);
    Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct = default);
    Task<IReadOnlyList<EnergyBookingSlot>> GetActiveSlotsAsync(CancellationToken ct = default);
    Task<SolarStation?> GetStationAsync(string id, CancellationToken ct = default);
    Task<bool> TryLockAsync(string nic, string token, CancellationToken ct = default);
    Task UnlockAsync(string nic, string token, CancellationToken ct = default);
    Task<bool> TryAcquireCapacityAsync(EnergyBookingSlot expected, CancellationToken ct = default);
    Task<bool> ReleaseCapacityAsync(string slotId, CancellationToken ct = default);
    Task InsertAsync(EnergyReservation reservation, CancellationToken ct = default);
    Task<bool> TryReplaceAsync(EnergyReservation expected, EnergyReservation replacement, CancellationToken ct = default);
}
