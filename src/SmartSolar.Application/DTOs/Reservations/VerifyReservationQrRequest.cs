/*
 * File: VerifyReservationQrRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Transports scanned opaque QR reference for server-side verification.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed class VerifyReservationQrRequest : IValidatableObject
{
    public string QrPayload { get; set; } = string.Empty;

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        // Require a nonempty QR payload from the client before security validation.
        if (string.IsNullOrWhiteSpace(QrPayload))
        {
            yield return new ValidationResult("QrPayload is required.", [nameof(QrPayload)]);
        }
    }
}
