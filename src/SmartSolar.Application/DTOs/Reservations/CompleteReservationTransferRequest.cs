/*
 * File: CompleteReservationTransferRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Encapsulates the scanned opaque reference sent by a GridOperator to complete a transfer.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed record CompleteReservationTransferRequest(
    [Required(ErrorMessage = "QR payload is required.")]
    [StringLength(500, MinimumLength = 10, ErrorMessage = "QR payload length is invalid.")]
    string QrPayload,
    string? ReservationId = null
);
