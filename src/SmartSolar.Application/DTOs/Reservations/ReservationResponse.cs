/*
 * File: ReservationResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines the server-derived reservation summary without QR credentials.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.DTOs.Reservations;

// Schedule values must be resolved by the server; this DTO adds no persisted entity fields.
public sealed record ReservationResponse(
    string ReservationId,
    string ProsumerNic,
    string StationId,
    string SlotId,
    decimal EnergyAmountKwh,
    DateTime ScheduledStartAtUtc,
    DateTime ScheduledEndAtUtc,
    ReservationStatus Status,
    DateTime CreatedAtUtc,
    DateTime UpdatedAtUtc);
