/*
 * File: ReservationSearchRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Validates bounded, exact reservation read filters without accepting database expressions.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.ComponentModel.DataAnnotations;
using System.Text.RegularExpressions;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.DTOs.Reservations;

public sealed class ReservationSearchRequest : IValidatableObject
{
    public string? ReservationId { get; init; }
    public string? ProsumerNic { get; init; }
    public string? StationId { get; init; }
    public string? Status { get; init; }
    public DateTime? FromUtc { get; init; }
    public DateTime? ToUtc { get; init; }
    [Range(1, 10000)] public int Page { get; init; } = 1;
    [Range(1, 100)] public int PageSize { get; init; } = 20;

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        // Validate direct service calls as well as MVC binding; GUID strings retain their stored representation.
        foreach (var (name, value) in new[] { (nameof(ReservationId), ReservationId), (nameof(StationId), StationId) })
            if (!string.IsNullOrWhiteSpace(value) && (!Guid.TryParse(value.Trim(), out var id) || id == Guid.Empty))
                yield return new ValidationResult($"{name} must be a nonempty GUID.", [name]);
        if (!string.IsNullOrWhiteSpace(ProsumerNic) && !Regex.IsMatch(ProsumerNic.Trim(), @"\A([0-9]{12}|[0-9]{9}[VXvx])\z"))
            yield return new ValidationResult("ProsumerNic must be 12 digits or 9 digits followed by V/X.", [nameof(ProsumerNic)]);
        if (!string.IsNullOrWhiteSpace(Status) && !Enum.GetNames<ReservationStatus>().Contains(Status.Trim(), StringComparer.OrdinalIgnoreCase))
            yield return new ValidationResult("Status must be Pending, Approved, Rejected, Cancelled or Completed.", [nameof(Status)]);
        foreach (var (name, value) in new[] { (nameof(FromUtc), FromUtc), (nameof(ToUtc), ToUtc) })
            if (value.HasValue && value.Value.Kind != DateTimeKind.Utc)
                yield return new ValidationResult($"{name} must use UTC.", [name]);
        if (FromUtc.HasValue && ToUtc.HasValue && FromUtc > ToUtc)
            yield return new ValidationResult("FromUtc must not be later than ToUtc.", [nameof(FromUtc), nameof(ToUtc)]);
    }
}
