/*
 * File: UpdateOwnProfileRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Restricts own-profile updates to editable contact fields.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Users;

public sealed class UpdateOwnProfileRequest
{
    [Required, StringLength(120, MinimumLength = 2)]
    public string FullName { get; init; } = string.Empty;

    [Required, EmailAddress]
    public string Email { get; init; } = string.Empty;

    [Required, StringLength(20, MinimumLength = 7)]
    public string PhoneNumber { get; init; } = string.Empty;
}
