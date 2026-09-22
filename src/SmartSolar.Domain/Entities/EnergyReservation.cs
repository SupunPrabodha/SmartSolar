/*
 * File: EnergyReservation.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Enums;

namespace SmartSolar.Domain.Entities;

public sealed class EnergyReservation
{
    public string ReservationId { get; set; } = Guid.NewGuid().ToString("N");

    public string ProsumerNic { get; set; } = string.Empty;
    public string StationId { get; set; } = string.Empty;
    public string SlotId { get; set; } = string.Empty;
    public decimal EnergyAmountKwh { get; set; }

    public ReservationStatus Status { get; set; } = ReservationStatus.Pending;

    public string? QrToken { get; set; }
    public DateTime CreatedAtUtc { get; set; }
    public DateTime UpdatedAtUtc { get; set; }
}
