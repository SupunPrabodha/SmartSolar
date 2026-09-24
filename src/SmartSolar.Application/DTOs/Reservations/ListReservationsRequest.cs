/*
 * File: ListReservationsRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines optional exact filters for GridOperator reservation management.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.ComponentModel.DataAnnotations;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed class ListReservationsRequest : IValidatableObject
{
    public ReservationStatus? Status { get; init; }
    public string? ProsumerNic { get; init; }
    public string? StationId { get; init; }

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        // Reject undefined enum values and malformed station IDs for MVC and direct service callers.
        if (Status.HasValue && !Enum.IsDefined(Status.Value))
            yield return new ValidationResult("Status must be a supported reservation status.", [nameof(Status)]);
        if (!string.IsNullOrWhiteSpace(StationId) &&
            (!Guid.TryParse(StationId.Trim(), out var id) || id == Guid.Empty))
            yield return new ValidationResult("StationId must be a nonempty GUID.", [nameof(StationId)]);
    }
}
