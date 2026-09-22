/*
 * File: UserMappings.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Maps user domain entities to API-safe DTOs.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Application.DTOs.Users;
using SmartSolar.Domain.Entities;

namespace SmartSolar.Application.Services;

internal static class UserMappings
{
    public static UserResponse ToResponse(this User user)
    {
        // Convert the persisted User entity into a response that never exposes PasswordHash.
        return new UserResponse(
            user.Nic,
            user.FullName,
            user.Email,
            user.PhoneNumber,
            user.Role,
            user.Status,
            user.CreatedAtUtc,
            user.UpdatedAtUtc);
    }
}
