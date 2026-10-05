/*
 * File: EnergyReservation.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): RAMANAYAKE R. H. B. D. G. (IT23164130), ALAHAKOON A. W. A. C. N. (IT23163522)
 * Purpose: Models reservation identity, accepted schedule, lifecycle, QR and completion metadata.
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

    // Nullable for legacy documents; missing snapshots require verified backfill.
    public DateTime? ScheduledStartAtUtc { get; set; }
    public DateTime? ScheduledEndAtUtc { get; set; }

    public string? QrToken { get; set; }
    public string? QrTokenHash { get; set; }
    public DateTime? QrIssuedAtUtc { get; set; }
    public DateTime? CompletedAtUtc { get; set; }
    public string? CompletedByOperatorNic { get; set; }
    public string? RejectionRemark { get; set; }
    public List<AuditEntry> AuditHistory { get; set; } = [];
    public DateTime CreatedAtUtc { get; set; }
    public DateTime UpdatedAtUtc { get; set; }
}
