/*
 * File: UpdateProsumerRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Validated editable fields for a Backoffice-managed Prosumer profile.
 */
using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Users;

public sealed class UpdateProsumerRequest
{
    [Required, StringLength(120, MinimumLength = 2)] public string FullName { get; init; } = string.Empty;
    [Required, EmailAddress] public string Email { get; init; } = string.Empty;
    [Required, StringLength(20, MinimumLength = 7)] public string PhoneNumber { get; init; } = string.Empty;
}
