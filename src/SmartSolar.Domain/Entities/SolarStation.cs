/*
 * File: SolarStation.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */


namespace SmartSolar.Domain.Entities;

public sealed class SolarStation
{
    public string StationId { get; set; } = Guid.NewGuid().ToString("N");

    public string Name { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public decimal CapacityKwh { get; set; }
    public int TotalBatterySlots { get; set; }
    public List<OperatingDay> OperatingSchedule { get; set; } = [];
    public bool IsActive { get; set; } = true;
    public DateTime CreatedAtUtc { get; set; }
    public DateTime UpdatedAtUtc { get; set; }
}
