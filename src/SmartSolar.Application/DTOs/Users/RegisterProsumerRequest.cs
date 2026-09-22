/*
 * File: RegisterProsumerRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Users;

public sealed class RegisterProsumerRequest
{
    [Required]
    [RegularExpression(@"^([0-9]{9}[VvXx]|[0-9]{12})$", ErrorMessage = "NIC must be a valid 9-digit legacy NIC with V/X or a 12-digit NIC.")]
    public string Nic { get; init; } = string.Empty;

    [Required, StringLength(120, MinimumLength = 2)]
    public string FullName { get; init; } = string.Empty;

    [Required, EmailAddress]
    public string Email { get; init; } = string.Empty;

    [Required, StringLength(20, MinimumLength = 7)]
    public string PhoneNumber { get; init; } = string.Empty;

    [Required, StringLength(100, MinimumLength = 8)]
    public string Password { get; init; } = string.Empty;
}
