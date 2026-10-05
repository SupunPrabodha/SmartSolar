/*
 * File: User.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450), Wickramathilaka N. M. (IT23165434), RAMANAYAKE R. H. B. D. G. (IT23164130)
 * Purpose: Models NIC-based account identity, verification, security versions and embedded profile history.
 * Note: Keep this header and add/update method-level comments as the code evolves.
 */

using SmartSolar.Domain.Enums;

namespace SmartSolar.Domain.Entities;

public sealed class User
{
    public string Nic { get; set; } = string.Empty;

    public string FullName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PhoneNumber { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;

    public UserRole Role { get; set; }

    public UserStatus Status { get; set; }

    public long SecurityVersion { get; set; }
    public string? PasswordResetTokenHash { get; set; }
    public DateTime? PasswordResetExpiresAtUtc { get; set; }
    public DateTime? PasswordResetRequestedAtUtc { get; set; }
    public DateTime? ProfileCompletedAtUtc { get; set; }
    public byte[]? AvatarBytes { get; set; }
    public string? AvatarContentType { get; set; }
    public string? AvatarVersion { get; set; }
    public List<InboxNotification> Notifications { get; set; } = [];
    public List<AuditEntry> AuditHistory { get; set; } = [];

    public long AccountVersion { get; set; }
    public DateTime? ApprovedAtUtc { get; set; }
    public DateTime? EmailVerifiedAtUtc { get; set; }
    public string? EmailVerificationHash { get; set; }
    public DateTime? EmailVerificationExpiresAtUtc { get; set; }

    // Internal non-expiring reservation mutex; abandoned writes require reconciliation.
    public string? ReservationWriteLock { get; set; }

    public DateTime CreatedAtUtc { get; set; }
    public DateTime UpdatedAtUtc { get; set; }
}
