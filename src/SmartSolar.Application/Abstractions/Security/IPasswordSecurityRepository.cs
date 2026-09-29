/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using SmartSolar.Domain.Entities;
namespace SmartSolar.Application.Abstractions.Security;

public interface IPasswordSecurityRepository
{
    Task<bool> SetResetAsync(User expected, string hash, DateTime now, DateTime expiry, CancellationToken ct);
    Task<User?> FindResetAsync(string hash, DateTime now, CancellationToken ct);
    Task<bool> ChangeAsync(User expected, string passwordHash, string? resetHash, DateTime now,
        AuditEntry audit, InboxNotification notification, CancellationToken ct);
}
public interface IAccountSecurityEmailSender
{
    Task SendResetAsync(string email, string token, CancellationToken ct);
    Task SendChangedAsync(string email, bool reset, CancellationToken ct);
}
public interface IRequestIdentity
{
    string Nic { get; }
    string CorrelationId { get; }
    void DeliveryFailed(string operation);
}
