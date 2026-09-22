/*
 * File: UserService.cs
 * Project: Smart Solar Microgrid Trading System
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

namespace SmartSolar.Application.Services;

public sealed class UserService : IUserService
{
    private readonly IUserRepository _users;
    private readonly IPasswordService _passwords;

    public UserService(IUserRepository users, IPasswordService passwords)
    {
        // Store persistence and password dependencies used by user-management operations.
        _users = users;
        _passwords = passwords;
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

        user.FullName = request.FullName.Trim();
        user.Email = email;
        user.PhoneNumber = request.PhoneNumber.Trim();
        user.UpdatedAtUtc = DateTime.UtcNow;

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
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    public async Task ActivateAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Activate a pending or previously deactivated account through the Backoffice-only endpoint.
        var user = await FindRequiredAsync(nic, cancellationToken);
        user.Status = UserStatus.Active;
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    public async Task DeactivateAsync(string nic, CancellationToken cancellationToken = default)
    {
        // Deactivate the selected user account through the Backoffice administration workflow.
        var user = await FindRequiredAsync(nic, cancellationToken);
        user.Status = UserStatus.Deactivated;
        user.UpdatedAtUtc = DateTime.UtcNow;
        await _users.ReplaceAsync(user, cancellationToken);
    }

    private async Task<User> FindRequiredAsync(string nic, CancellationToken cancellationToken)
    {
        // Normalize the NIC lookup and convert a missing record into a domain-friendly not-found error.
        var normalized = nic.Trim().ToUpperInvariant();
        return await _users.GetByNicAsync(normalized, cancellationToken)
               ?? throw new NotFoundException("User not found.");
    }
}
