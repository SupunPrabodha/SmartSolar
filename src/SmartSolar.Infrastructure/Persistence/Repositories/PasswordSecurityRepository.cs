/*
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Enterprise experience and operations security.
 */
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Infrastructure.Persistence.Repositories;

public sealed class PasswordSecurityRepository(IMongoDatabase database) : IPasswordSecurityRepository
{
    private IMongoCollection<User> Users => database.GetCollection<User>(CollectionNames.Users);
    private static FilterDefinition<User> Expected(User user)
    {
        var f = Builders<User>.Filter;
        var version = f.Eq(x => x.AccountVersion, user.AccountVersion);
        if (user.AccountVersion == 0) version |= f.Exists(x => x.AccountVersion, false);
        return f.Eq(x => x.Nic, user.Nic) & f.Eq(x => x.Status, UserStatus.Active) & version;
    }
    public async Task<bool> SetResetAsync(User expected, string hash, DateTime now, DateTime expiry, CancellationToken ct)
    {
        // AccountVersion protects a concurrent profile, password or recovery request.
        var result = await Users.UpdateOneAsync(Expected(expected), Builders<User>.Update
            .Set(x => x.PasswordResetTokenHash, hash).Set(x => x.PasswordResetExpiresAtUtc, expiry)
            .Set(x => x.PasswordResetRequestedAtUtc, now).Inc(x => x.AccountVersion, 1), cancellationToken: ct);
        return result.ModifiedCount == 1;
    }
    public Task<User?> FindResetAsync(string hash, DateTime now, CancellationToken ct) =>
        Users.Find(x => x.PasswordResetTokenHash == hash && x.PasswordResetExpiresAtUtc > now && x.Status == UserStatus.Active)
            .FirstOrDefaultAsync(ct)!;

    public async Task<bool> ChangeAsync(User expected, string passwordHash, string? resetHash, DateTime now,
        AuditEntry audit, InboxNotification notification, CancellationToken ct)
    {
        var filter = Expected(expected);
        if (resetHash is not null)
            filter &= Builders<User>.Filter.Eq(x => x.PasswordResetTokenHash, resetHash) &
                Builders<User>.Filter.Gt(x => x.PasswordResetExpiresAtUtc, now);
        var update = Builders<User>.Update.Set(x => x.PasswordHash, passwordHash)
            .Unset(x => x.PasswordResetTokenHash).Unset(x => x.PasswordResetExpiresAtUtc).Unset(x => x.PasswordResetRequestedAtUtc)
            .Inc(x => x.SecurityVersion, 1).Inc(x => x.AccountVersion, 1).Set(x => x.UpdatedAtUtc, now)
            .PushEach(x => x.AuditHistory, [audit], slice: -100)
            .PushEach(x => x.Notifications, [notification], slice: -100);
        return (await Users.UpdateOneAsync(filter, update, cancellationToken: ct)).ModifiedCount == 1;
    }
}
