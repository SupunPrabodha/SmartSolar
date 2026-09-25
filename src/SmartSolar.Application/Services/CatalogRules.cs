/*
 * File: CatalogRules.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Validates UTC operating schedules, coordinates and optimistic update timestamps.
 */
using System.Globalization;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
namespace SmartSolar.Application.Services;

public static class CatalogRules
{
    public static List<OperatingDay> Schedule(List<OperatingDayDto>? days)
    {
        // Require all seven ISO weekdays; closed days have no hours and overnight hours are split.
        if (days is null || days.Count != 7 || days.Any(x => x is null) ||
            days.Select(x => x.Day).Distinct().Count() != 7 || days.Any(x => x.Day is < 1 or > 7))
            throw new BadRequestException("Operating schedule must contain each day 1 (Monday) through 7 (Sunday) exactly once.");
        foreach (var day in days)
        {
            if (day.IsClosed)
            {
                if (day.OpensAt is not null || day.ClosesAt is not null)
                    throw new BadRequestException("Closed days must have null opening and closing times.");
            }
            else if (!TryMinute(day.OpensAt, false, out var start) ||
                     !TryMinute(day.ClosesAt, true, out var end) || start >= end)
                throw new BadRequestException("Open days require HH:mm UTC hours with opening before closing; 24:00 is allowed only for closing.");
        }
        return days.OrderBy(x => x.Day).Select(x => new OperatingDay {
            Day = x.Day, IsClosed = x.IsClosed, OpensAt = x.OpensAt, ClosesAt = x.ClosesAt }).ToList();
    }

    private static bool TryMinute(string? value, bool closing, out int minute)
    {
        // Parse a strict minute-resolution clock value without locale-dependent date conversion.
        minute = 0;
        if (closing && value == "24:00") { minute = 1440; return true; }
        if (value is null || value.Length != 5 ||
            !TimeOnly.TryParseExact(value, "HH:mm", CultureInfo.InvariantCulture, DateTimeStyles.None, out var time)) return false;
        minute = time.Hour * 60 + time.Minute;
        return true;
    }

    public static void Coordinates(double latitude, double longitude)
    {
        // Reject NaN/infinity as well as coordinates outside the geographic ranges.
        if (!double.IsFinite(latitude) || !double.IsFinite(longitude) ||
            latitude is < -90 or > 90 || longitude is < -180 or > 180)
            throw new BadRequestException("Latitude must be -90..90 and longitude -180..180.");
    }

    public static double DistanceKm(double latitude, double longitude, double targetLatitude, double targetLongitude)
    {
        // Compute great-circle distance server-side with the Haversine formula; never persist distance.
        const double radians = Math.PI / 180;
        var dLat = (targetLatitude - latitude) * radians;
        var dLon = (targetLongitude - longitude) * radians;
        var a = Math.Pow(Math.Sin(dLat / 2), 2) + Math.Cos(latitude * radians) *
            Math.Cos(targetLatitude * radians) * Math.Pow(Math.Sin(dLon / 2), 2);
        return 6371.0088 * 2 * Math.Asin(Math.Sqrt(Math.Clamp(a, 0, 1)));
    }

    public static DateTime RequireExpected(DateTimeOffset? expected, DateTime actual)
    {
        // Require the exact returned timestamp so stale clients cannot overwrite later changes.
        if (expected is null) throw new BadRequestException("expectedUpdatedAtUtc is required for changes.");
        if (expected.Value.UtcDateTime != actual) throw new ConflictException("This record changed. Reload it before saving.");
        return actual;
    }

    public static DateTime NextTimestamp(DateTime previous = default)
    {
        // Mongo timestamps have millisecond precision; always advance even within the same millisecond.
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var old = previous == default ? 0 : new DateTimeOffset(DateTime.SpecifyKind(previous, DateTimeKind.Utc)).ToUnixTimeMilliseconds();
        return DateTimeOffset.FromUnixTimeMilliseconds(Math.Max(now, old + 1)).UtcDateTime;
    }

    public static StationResponse ToResponse(SolarStation station)
    {
        // Copy persisted fields into the public response without exposing database objects.
        return new(station.StationId, station.Name, station.Address, station.Latitude, station.Longitude,
            station.CapacityKwh, station.TotalBatterySlots, station.IsActive,
            station.OperatingSchedule.Select(x => new OperatingDayDto(x.Day, x.IsClosed, x.OpensAt, x.ClosesAt)).ToArray(),
            station.CreatedAtUtc, station.UpdatedAtUtc);
    }
}

/// <summary>Serializes related station/slot writes within this API process; not a distributed reservation lock.</summary>
public sealed class CatalogWriteGate
{
    public SemaphoreSlim Mutex { get; } = new(1, 1);
}
