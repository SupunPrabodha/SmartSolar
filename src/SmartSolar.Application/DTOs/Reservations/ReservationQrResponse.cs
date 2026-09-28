/*
 * File: ReservationQrResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Returns opaque QR reference payload and issuance timestamp to an authorized Prosumer.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

namespace SmartSolar.Application.DTOs.Reservations;

public sealed record ReservationQrResponse(
    string ReservationId,
    string QrPayload,
    DateTime IssuedAtUtc
);
