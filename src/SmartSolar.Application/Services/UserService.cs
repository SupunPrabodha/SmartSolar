/*
 * File: UserService.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Wickramathilaka N. M. (IT23165434)
 * Purpose: Implements user/profile lifecycle business operations shared by web and mobile clients.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Application.Abstractions.Users;
using SmartSolar.Application.DTOs.Users;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using System.Security.Cryptography;
using System.Text;

namespace SmartSolar.Application.Services;

public sealed class UserService : IUserService
{
    private readonly IUserRepository _users;
    private readonly IPasswordService _passwords;
    private readonly IVerificationEmailSender _email;

    public UserService(IUserRepository users, IPasswordService passwords, IVerificationEmailSender email)
    {
        // Store persistence and password dependencies used by user-management operations.
        _users = users;
        _passwords = passwords;
        _email = email;
    }

    public async Task<UserResponse> GetByNicAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Fetch the required user and return a password-safe response DTO.
        var user = await FindRequiredAsync(nic, cancellationToken);
        return user.ToResponse();
    }

    public async Task<IReadOnlyList<UserResponse>> GetAllAsync(CancellationToken cancellationToken = default)
    {
        // Return all users for Backoffice administration without exposing password hashes.
        var users = await _users.GetAllAsync(cancellationToken);
        return users.Select(x => x.ToResponse()).ToList();
    }

    public async Task<IReadOnlyList<UserResponse>> GetPendingAsync(CancellationToken cancellationToken = default)
    {
        // Return the pending-activation queue required by the Backoffice workflow.
        var users = await _users.GetByStatusAsync(UserStatus.PendingActivation, cancellationToken);
        return users.Select(x => x.ToResponse()).ToList();
    }

    public async Task<UserResponse> CreateStaffAsync(
        CreateStaffRequest request,
        CancellationToken cancellationToken = default)
    {
        // Validate the requested staff role and create an active Backoffice/GridOperator account.
        RequestValidation.EnsureValid(request);
        if (request.Role is not (UserRole.Backoffice or UserRole.GridOperator))
        {
            throw new BadRequestException("Staff role must be Backoffice or GridOperator.");
        }

        var nic = request.Nic.Trim().ToUpperInvariant();
        var email = request.Email.Trim().ToLowerInvariant();

        if (await _users.GetByNicAsync(nic, cancellationToken) is not null)
        {
            throw new ConflictException("A user with this NIC already exists.");
        }

        if (await _users.GetByEmailAsync(email, cancellationToken) is not null)
        {
            throw new ConflictException("A user with this email already exists.");
        }

        var now = DateTime.UtcNow;
        var user = new User
        {
            Nic = nic,
            FullName = request.FullName.Trim(),
            Email = email,
            PhoneNumber = request.PhoneNumber.Trim(),
            Role = request.Role.Value,
            Status = UserStatus.Active,
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        };

        user.PasswordHash = _passwords.HashPassword(user, request.Password);
        await _users.InsertAsync(user, cancellationToken);
        return user.ToResponse();
    }

    public async Task<UserResponse> UpdateOwnProfileAsync(
        string nic,
        UpdateOwnProfileRequest request,
        CancellationToken cancellationToken = default)
    {
        // Update editable profile fields while preserving unique email ownership and immutable identity/role fields.
        RequestValidation.EnsureValid(request);
        var user = await FindRequiredAsync(nic, cancellationToken);
        var email = request.Email.Trim().ToLowerInvariant();
        var userWithEmail = await _users.GetByEmailAsync(email, cancellationToken);

        if (userWithEmail is not null && !string.Equals(userWithEmail.Nic, user.Nic, StringComparison.OrdinalIgnoreCase))
        {
            throw new ConflictException("A user with this email already exists.");
        }

        InvalidateChangedEmail(user, email);
        user.FullName = request.FullName.Trim();
        user.Email = email;
        user.PhoneNumber = request.PhoneNumber.Trim();
        user.UpdatedAtUtc = DateTime.UtcNow;

        user.ProfileCompletedAtUtc = user.AvatarVersion is not null &&
            !string.IsNullOrWhiteSpace(user.FullName) && !string.IsNullOrWhiteSpace(user.Email) &&
            !string.IsNullOrWhiteSpace(user.PhoneNumber) ? user.ProfileCompletedAtUtc ?? DateTime.UtcNow : null;
        await _users.ReplaceAsync(user, cancellationToken);
        return user.ToResponse();
    }

    public async Task RequestOwnDeactivationAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Deactivate the authenticated Prosumer so that only Backoffice can reactivate the account later.
        var user = await FindRequiredAsync(nic, cancellationToken);

        if (user.Role != UserRole.Prosumer)
        {
            throw new ForbiddenException("Only Prosumer accounts can request self-deactivation.");
        }

        user.Status = UserStatus.Deactivated;
        ClearVerification(user);
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    public async Task<UserResponse> UpdateProsumerAsync(string nic, UpdateProsumerRequest request, CancellationToken cancellationToken = default)
    {
        // Allow Backoffice administration to change only editable Prosumer contact fields.
        RequestValidation.EnsureValid(request);
        var user = await FindRequiredAsync(nic, cancellationToken);
        if (user.Role != UserRole.Prosumer) throw new BadRequestException("Only Prosumer profiles can be updated here.");
        var email = request.Email.Trim().ToLowerInvariant();
        var existing = await _users.GetByEmailAsync(email, cancellationToken);
        if (existing is not null && !string.Equals(existing.Nic, user.Nic, StringComparison.OrdinalIgnoreCase))
            throw new ConflictException("A user with this email already exists.");
        InvalidateChangedEmail(user, email);
        user.FullName = request.FullName.Trim(); user.Email = email; user.PhoneNumber = request.PhoneNumber.Trim();
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
        return user.ToResponse();
    }

    public async Task ActivateAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Staff activation is immediate; Prosumer approval requires a one-use email proof before activation.
        var user = await FindRequiredAsync(nic, cancellationToken);
        if (user.Status == UserStatus.Active) return;
        if (user.Role == UserRole.Prosumer)
        {
            _email.EnsureConfigured();
            var now = DateTime.UtcNow;
            if (user.ApprovedAtUtc is DateTime sent && now - sent < TimeSpan.FromMinutes(1))
                throw new ConflictException("Please wait one minute before resending verification.");
            var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
            user.Status = UserStatus.PendingActivation;
            user.ApprovedAtUtc = now;
            user.EmailVerifiedAtUtc = null;
            user.EmailVerificationHash = HashToken(token);
            user.EmailVerificationExpiresAtUtc = now.AddHours(24);
            user.UpdatedAtUtc = now;
            await _users.ReplaceAsync(user, cancellationToken);
            // A delivery failure leaves the account pending; Backoffice may resend after the cooldown.
            await _email.SendAsync(user.Email, user.Nic, token, cancellationToken);
            return;
        }
        user.Status = UserStatus.Active;
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    public async Task DeactivateAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Deactivate the selected user account through the Backoffice administration workflow.
        var user = await FindRequiredAsync(nic, cancellationToken);
        user.Status = UserStatus.Deactivated;
        ClearVerification(user);
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    public async Task VerifyEmailAsync(string nic, string token, CancellationToken cancellationToken = default)
    {
        // Match a bounded opaque token, then consume it using the repository's optimistic account version.
        if (string.IsNullOrWhiteSpace(nic) || nic.Length > 20 || token is null || token.Length != 64)
            throw new BadRequestException("Verification link is invalid or expired. Ask Backoffice to resend it.");
        var user = await _users.GetByNicAsync(nic.Trim().ToUpperInvariant(), cancellationToken);
        if (user is null || user.Role != UserRole.Prosumer || user.Status != UserStatus.PendingActivation ||
            user.ApprovedAtUtc is null || user.EmailVerificationExpiresAtUtc is not DateTime expiry || expiry <= DateTime.UtcNow ||
            user.EmailVerificationHash is null || !CryptographicOperations.FixedTimeEquals(
                Encoding.UTF8.GetBytes(user.EmailVerificationHash), Encoding.UTF8.GetBytes(HashToken(token))))
            throw new BadRequestException("Verification link is invalid or expired. Ask Backoffice to resend it.");
        user.Status = UserStatus.Active;
        user.EmailVerifiedAtUtc = DateTime.UtcNow;
        user.EmailVerificationHash = null;
        user.EmailVerificationExpiresAtUtc = null;
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    private static string HashToken(string token)
    {
        // Only the digest is stored in MongoDB; the bearer token travels in the email link.
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
    }

    private static void ClearVerification(User user)
    {
        // Revocation prevents a previously sent link from reopening a deactivated account.
        user.ApprovedAtUtc = null;
        user.EmailVerifiedAtUtc = null;
        user.EmailVerificationHash = null;
        user.EmailVerificationExpiresAtUtc = null;
    }

    private static void InvalidateChangedEmail(User user, string email)
    {
        // A new Prosumer address needs fresh Backoffice approval and ownership verification.
        if (user.Role != UserRole.Prosumer || string.Equals(user.Email, email, StringComparison.OrdinalIgnoreCase)) return;
        ClearVerification(user);
        if (user.Status == UserStatus.Active) user.Status = UserStatus.PendingActivation;
    }

    private async Task<User> FindRequiredAsync(string nic, CancellationToken cancellationToken)
    {
        // Normalize the NIC lookup and convert a missing record into a domain-friendly not-found error.
        var normalized = nic.Trim().ToUpperInvariant();
        return await _users.GetByNicAsync(normalized, cancellationToken)
               ?? throw new NotFoundException("User not found.");
    }
}
