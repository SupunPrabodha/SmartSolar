/*
 * File: LoginRequest.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
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
