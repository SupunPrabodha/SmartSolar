/*
 * File: LoginResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Returns the access token, expiry and API-safe authenticated profile.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Application.DTOs.Auth;

public sealed record LoginResponse(
    string AccessToken,
    DateTime ExpiresAtUtc,
    UserResponse User);
