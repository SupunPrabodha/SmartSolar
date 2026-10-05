/*
 * File: LoginRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Validates the NIC and password supplied to the sign-in endpoint.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using System.ComponentModel.DataAnnotations;

namespace SmartSolar.Application.DTOs.Auth;

public sealed class LoginRequest
{
    [Required]
    public string Nic { get; init; } = string.Empty;

    [Required]
    public string Password { get; init; } = string.Empty;
}
