/*
 * File: CreateReservationRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Limits reservation input to slot selection and positive energy amount.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed class CreateReservationRequest : IValidatableObject
{
    [Required]
    public string SlotId { get; init; } = string.Empty;
    public decimal EnergyAmountKwh { get; init; }

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        // Accept existing GUID string formats and reject empty IDs and nonpositive energy.
        if (!Guid.TryParse(SlotId, out var id) || id == Guid.Empty)
            yield return new ValidationResult("SlotId must be a nonempty GUID.", [nameof(SlotId)]);
        if (EnergyAmountKwh <= 0)
            yield return new ValidationResult("EnergyAmountKwh must be greater than zero.", [nameof(EnergyAmountKwh)]);
    }
}
