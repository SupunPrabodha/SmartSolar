/*
 * File: IJwtTokenService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Entities;

namespace SmartSolar.Application.Abstractions.Auth;

public interface IJwtTokenService
{
    TokenResult CreateToken(User user);
}

public sealed record TokenResult(string AccessToken, DateTime ExpiresAtUtc);
