/*
 * File: StationDtos.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines station and nearby API contracts without persistence objects.
 */
using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Stations;

public sealed record OperatingDayDto(int Day, bool IsClosed, string? OpensAt, string? ClosesAt);

public sealed class StationRequest
{
    [Required, StringLength(120, MinimumLength = 2)]
    public string Name { get; init; } = "";
    [Required, StringLength(300, MinimumLength = 3)]
    public string Address { get; init; } = "";
    [Required, Range(-90d, 90d)]
    public double? Latitude { get; init; }
    [Required, Range(-180d, 180d)]
    public double? Longitude { get; init; }
    [Required]
    public decimal? CapacityKwh { get; init; }
    [Required, Range(1, int.MaxValue)]
    public int? TotalBatterySlots { get; init; }
    [Required]
    public List<OperatingDayDto>? OperatingSchedule { get; init; }
    public DateTimeOffset? ExpectedUpdatedAtUtc { get; init; }
}

public sealed class CatalogChangeRequest
{
    [Required]
    public DateTimeOffset? ExpectedUpdatedAtUtc { get; init; }
}

public sealed class NearbyQuery
{
    [Required, Range(-90d, 90d)]
    public double? Latitude { get; init; }
    [Required, Range(-180d, 180d)]
    public double? Longitude { get; init; }
    [Range(0.1, 500)]
    public double RadiusKm { get; init; } = 25;
}

public sealed record StationResponse(string StationId, string Name, string Address, double Latitude,
    double Longitude, decimal CapacityKwh, int TotalBatterySlots, bool IsActive,
    IReadOnlyList<OperatingDayDto> OperatingSchedule, DateTime CreatedAtUtc, DateTime UpdatedAtUtc);
public sealed record NearbyStationResponse(StationResponse Station, double DistanceKm);
