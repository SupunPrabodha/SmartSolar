/*
 * File: EnergyBookingSlot.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Models station inventory, its UTC interval, availability and audit history.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */


namespace SmartSolar.Domain.Entities;

public sealed class EnergyBookingSlot
{
    public string SlotId { get; set; } = Guid.NewGuid().ToString("N");

    public string StationId { get; set; } = string.Empty;
    public DateTime StartAtUtc { get; set; }
    public DateTime EndAtUtc { get; set; }
    public int TotalSlots { get; set; }
    public int AvailableSlots { get; set; }
    public bool IsActive { get; set; } = true;
    public List<AuditEntry> AuditHistory { get; set; } = [];
    public DateTime CreatedAtUtc { get; set; }
    public DateTime UpdatedAtUtc { get; set; }
}
