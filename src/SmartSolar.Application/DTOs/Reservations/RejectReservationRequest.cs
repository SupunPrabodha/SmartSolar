/*
 * File: RejectReservationRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines operator input for reservation rejection with a mandatory remark.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed class RejectReservationRequest : IValidatableObject
{
    [Required(AllowEmptyStrings = false, ErrorMessage = "Rejection remark is required.")]
    [StringLength(500, MinimumLength = 1, ErrorMessage = "Rejection remark cannot exceed 500 characters.")]
    public string Remark { get; init; } = string.Empty;

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        if (string.IsNullOrWhiteSpace(Remark))
            yield return new ValidationResult("Rejection remark must not be empty or whitespace.", [nameof(Remark)]);
    }
}
