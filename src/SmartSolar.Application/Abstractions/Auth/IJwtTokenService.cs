/*
 * File: IJwtTokenService.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Smart Solar Development Team
 * Purpose: Defines creation of signed, expiring access tokens for authenticated accounts.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Entities;

namespace SmartSolar.Application.Abstractions.Auth;

public interface IJwtTokenService
{
    TokenResult CreateToken(User user);
}

public sealed record TokenResult(string AccessToken, DateTime ExpiresAtUtc);
