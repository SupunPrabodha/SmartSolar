/*
 * File: ReservationVerificationResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Returns trusted authoritative reservation details after server QR verification.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed record ReservationVerificationResponse(
    string ReservationId,
    string ProsumerNic,
    string StationId,
    string SlotId,
    decimal EnergyAmountKwh,
    DateTime ScheduledStartAtUtc,
    DateTime ScheduledEndAtUtc,
    ReservationStatus Status,
    DateTime? QrIssuedAtUtc,
    bool EligibleForCompletion
);
