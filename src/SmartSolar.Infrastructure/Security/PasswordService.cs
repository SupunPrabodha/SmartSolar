/*
 * File: PasswordService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Provides secure one-way password hashing and verification.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using Microsoft.AspNetCore.Identity;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Domain.Entities;

namespace SmartSolar.Infrastructure.Security;

public sealed class PasswordService : IPasswordService
{
    private readonly PasswordHasher<User> _hasher = new();

    public string HashPassword(User user, string password)
    {
        // Hash the supplied plaintext password using ASP.NET Core Identity's password hasher.
        return _hasher.HashPassword(user, password);
    }

    public bool VerifyPassword(User user, string passwordHash, string providedPassword)
    {
        // Verify the supplied plaintext password against the stored one-way hash.
        var result = _hasher.VerifyHashedPassword(user, passwordHash, providedPassword);
        return result is PasswordVerificationResult.Success or PasswordVerificationResult.SuccessRehashNeeded;
    }
}
