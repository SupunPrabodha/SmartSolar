/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using System.Security.Cryptography;
using System.Text;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Services;

public sealed class PasswordSecurityService(IUserRepository users, IPasswordSecurityRepository security,
    IPasswordService passwords, IAccountSecurityEmailSender email, TimeProvider clock, IRequestIdentity identity)
{
    public static readonly TimeSpan ResetLifetime = TimeSpan.FromMinutes(20);
    public const string GenericResponse = "If an eligible Smart Solar account exists for the supplied details, password reset instructions have been sent.";
    private static string Hash(string token) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    public async Task ForgotAsync(ForgotPasswordRequest request, CancellationToken ct = default)
    {
        // The HTTP adapter returns exactly the same response for missing, inactive and eligible accounts.
        RequestValidation.EnsureValid(request);
        var value = request.Identifier.Trim();
        var user = value.Contains('@') ? await users.GetByEmailAsync(value.ToLowerInvariant(), ct)
            : await users.GetByNicAsync(value.ToUpperInvariant(), ct);
        if (user is null || user.Status != UserStatus.Active) return;
        var now = clock.GetUtcNow().UtcDateTime;
        if (user.PasswordResetRequestedAtUtc > now.AddMinutes(-1)) return;
        var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        if (!await security.SetResetAsync(user, Hash(token), now, now.Add(ResetLifetime), ct)) return;
        try { await email.SendResetAsync(user.Email, token, ct); }
        catch (OperationCanceledException) when (ct.IsCancellationRequested) { throw; }
        catch (Exception) { identity.DeliveryFailed("PasswordReset"); }
    }

    public async Task ResetAsync(ResetPasswordRequest request, CancellationToken ct = default)
    {
        RequestValidation.EnsureValid(request);
        if (!request.Token.All(Uri.IsHexDigit)) throw InvalidReset();
        var hash = Hash(request.Token);
        var now = clock.GetUtcNow().UtcDateTime;
        var user = await security.FindResetAsync(hash, now, ct) ?? throw InvalidReset();
        await CommitAsync(user, request.NewPassword, hash, now, ct);
    }

    public async Task ChangeAsync(ChangePasswordRequest request, CancellationToken ct = default)
    {
        RequestValidation.EnsureValid(request);
        var user = await users.GetByNicAsync(identity.Nic, ct);
        if (user is null || user.Status != UserStatus.Active) throw new UnauthorizedException("Sign in again.");
        if (!passwords.VerifyPassword(user, user.PasswordHash, request.CurrentPassword))
            throw new BadRequestException("The current password is incorrect.");
        await CommitAsync(user, request.NewPassword, null, clock.GetUtcNow().UtcDateTime, ct);
    }

    private async Task CommitAsync(User user, string password, string? resetHash, DateTime now, CancellationToken ct)
    {
        // One conditional document update consumes the token, changes the hash and revokes every old JWT.
        var reset = resetHash is not null;
        var audit = new AuditEntry { AtUtc = now, ActorNic = user.Nic,
            Event = reset ? "PasswordResetCompleted" : "PasswordChanged", CorrelationId = identity.CorrelationId };
        var notice = new InboxNotification { Id = audit.Id, AtUtc = now, Category = "Security", Priority = "High",
            Message = reset ? "Your password was reset." : "Your password was changed.", Action = "Profile" };
        if (!await security.ChangeAsync(user, passwords.HashPassword(user, password), resetHash, now, audit, notice, ct))
        {
            if (reset) throw InvalidReset();
            throw new ConflictException("This account changed. Sign in again before retrying.");
        }
        // Acknowledgement transport failure must never undo a committed password/security mutation.
        try { await email.SendChangedAsync(user.Email, reset, ct); }
        catch (Exception) { identity.DeliveryFailed("PasswordChanged"); }
    }
    private static BadRequestException InvalidReset() => new("This password reset link is invalid or has expired.");
}
