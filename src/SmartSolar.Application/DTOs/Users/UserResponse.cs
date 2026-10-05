/*
 * File: UserResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Returns public profile, role, state and completion metadata without credentials.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.DTOs.Users;

public sealed record UserResponse(
    string Nic,
    string FullName,
    string Email,
    string PhoneNumber,
    UserRole Role,
    UserStatus Status,
    DateTime CreatedAtUtc,
    DateTime UpdatedAtUtc,
    DateTime? ApprovedAtUtc = null,
    DateTime? EmailVerifiedAtUtc = null,
    bool ProfileComplete = false,
    string? AvatarVersion = null);
