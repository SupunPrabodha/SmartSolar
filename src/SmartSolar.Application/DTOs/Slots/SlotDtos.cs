/*
 * File: SlotDtos.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines booking-slot administration DTOs, not reservation commands.
 */
using System.ComponentModel.DataAnnotations;
namespace SmartSolar.Application.DTOs.Slots;

public sealed class SlotRequest
{
    [Required] public DateTimeOffset? StartAtUtc { get; init; }
    [Required] public DateTimeOffset? EndAtUtc { get; init; }
    [Required, Range(1, int.MaxValue)] public int? TotalSlots { get; init; }
    [Required, Range(0, int.MaxValue)] public int? AvailableSlots { get; init; }
    public DateTimeOffset? ExpectedUpdatedAtUtc { get; init; }
}
public sealed class SlotAvailabilityRequest
{
    [Required, Range(0, int.MaxValue)] public int? AvailableSlots { get; init; }
    [Required] public DateTimeOffset? ExpectedUpdatedAtUtc { get; init; }
}
public sealed record SlotResponse(string SlotId, string StationId, DateTime StartAtUtc,
    DateTime EndAtUtc, int TotalSlots, int AvailableSlots, bool IsActive,
    DateTime CreatedAtUtc, DateTime UpdatedAtUtc);
