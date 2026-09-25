/*
 * File: OperatingDay.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Stores one UTC day of a station operating schedule.
 */
namespace SmartSolar.Domain.Entities;

public sealed class OperatingDay
{
    public int Day { get; set; }
    public bool IsClosed { get; set; }
    public string? OpensAt { get; set; }
    public string? ClosesAt { get; set; }
}
