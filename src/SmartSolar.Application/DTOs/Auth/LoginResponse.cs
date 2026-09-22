/*
 * File: LoginResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Application.DTOs.Auth;

public sealed record LoginResponse(
    string AccessToken,
    DateTime ExpiresAtUtc,
    UserResponse User);
