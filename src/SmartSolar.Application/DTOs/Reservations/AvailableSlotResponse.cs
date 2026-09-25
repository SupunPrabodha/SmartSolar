/*
 * File: AvailableSlotResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exposes active booking slot summary for assisted/self-service slot selection.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
namespace SmartSolar.Application.DTOs.Reservations;

public sealed record AvailableSlotResponse(
    string SlotId,
    string StationId,
    DateTime StartAtUtc,
    DateTime EndAtUtc,
    int AvailableSlots,
    int TotalSlots);
