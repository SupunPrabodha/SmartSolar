/*
 * File: AuthService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Implements Prosumer registration and login business rules.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.DTOs.Users;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Services;

public sealed class AuthService : IAuthService
{
    private readonly IUserRepository _users;
    private readonly IPasswordService _passwords;
    private readonly IJwtTokenService _tokens;

    public AuthService(
        IUserRepository users,
        IPasswordService passwords,
        IJwtTokenService tokens)
    {
        // Store the dependencies required for authentication use cases.
        _users = users;
        _passwords = passwords;
        _tokens = tokens;
    }

    public async Task<UserResponse> RegisterProsumerAsync(
        RegisterProsumerRequest request,
        CancellationToken cancellationToken = default)
    {
        // Normalize identity fields and reject duplicate NIC/email values before creating the Prosumer.
        RequestValidation.EnsureValid(request);
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
            Role = UserRole.Prosumer,
            Status = UserStatus.PendingActivation,
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        };

        user.PasswordHash = _passwords.HashPassword(user, request.Password);
        await _users.InsertAsync(user, cancellationToken);

        return user.ToResponse();
    }

    public async Task<LoginResponse> LoginAsync(
        LoginRequest request,
        CancellationToken cancellationToken = default)
    {
        // Validate credentials and account status before issuing a JWT to the active user.
        RequestValidation.EnsureValid(request);
        var nic = request.Nic.Trim().ToUpperInvariant();
        var user = await _users.GetByNicAsync(nic, cancellationToken)
                   ?? throw new UnauthorizedException("Invalid NIC or password.");

        if (!_passwords.VerifyPassword(user, user.PasswordHash, request.Password))
        {
            throw new UnauthorizedException("Invalid NIC or password.");
        }

        if (user.Status == UserStatus.PendingActivation)
        {
            throw new ForbiddenException("This account is pending Backoffice activation.");
        }

        if (user.Status == UserStatus.Deactivated)
        {
            throw new ForbiddenException("This account is deactivated. Contact Backoffice for reactivation.");
        }

        var token = _tokens.CreateToken(user);
        return new LoginResponse(token.AccessToken, token.ExpiresAtUtc, user.ToResponse());
    }
}
