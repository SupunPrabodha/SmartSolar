/*
 * File: UserResponse.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Shared project source file for the SE4040 EAD implementation.
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
    DateTime UpdatedAtUtc);
