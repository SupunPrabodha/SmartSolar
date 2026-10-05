/*
 * File: EnterpriseSecurityTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Author(s): Liyanage S. P. (IT23187450)
 * Purpose: Tests Mongo recovery concurrency, session revocation and bounded notification persistence.
 */
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Persistence;
using SmartSolar.Infrastructure.Persistence.Repositories;
using SmartSolar.Infrastructure.Security;
using Xunit;
namespace SmartSolar.IntegrationTests;

public sealed class EnterpriseSecurityTests
{
    private sealed class Clock : TimeProvider { public DateTimeOffset Now = DateTimeOffset.UtcNow; public override DateTimeOffset GetUtcNow()
    {
        // Return the controlled fixture clock for deterministic time-boundary assertions.
        return Now;
    } }
    private sealed class Identity : IRequestIdentity
    {
        public string Nic => "200012345678";
        public string CorrelationId => new('a', 32);
        public int Failures;
        public void DeliveryFailed(string operation)
        {
            // Count simulated delivery failures without retaining provider diagnostics.
            Failures++;
        }
    }
    private sealed class Email : IAccountSecurityEmailSender
    {
        public string? Token;
        public int Requests, Changes;
        public bool Fail;
        public Task SendResetAsync(string email, string token, CancellationToken ct) {
            // Capture reset delivery attempts and optionally simulate a private SMTP failure.
            Token = token; Requests++; if (Fail) throw new Exception("private SMTP detail"); return Task.CompletedTask; }
        public Task SendChangedAsync(string email, bool reset, CancellationToken ct) {
            // Count password-change notifications and optionally simulate delivery failure.
            Changes++; if (Fail) throw new Exception("private SMTP detail"); return Task.CompletedTask; }
    }
    [MongoFact]
    public async Task RecoveryReplacementExpiryConcurrencyAndRevocationAreAtomic()
    {
        // Verify reset replacement, expiry, concurrent consumption and session revocation in MongoDB.
        MongoMappings.Register();
        var client = new MongoClient(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N"); var db = client.GetDatabase(name);
        try
        {
            await new MongoDbInitializer(db).InitializeAsync();
            var identity = new Identity(); var email = new Email(); var clock = new Clock(); var hasher = new PasswordService();
            var users = new UserRepository(db); var security = new PasswordSecurityRepository(db);
            var service = new PasswordSecurityService(users, security, hasher, email, clock, identity);
            var user = new User { Nic = identity.Nic, FullName = "Disposable Account", Email = "recovery@example.invalid", PhoneNumber = "0123456789", Role = UserRole.Prosumer, Status = UserStatus.Active };
            user.PasswordHash = hasher.HashPassword(user, "Original-Test-Password");
            await users.InsertAsync(user);
            await service.ForgotAsync(new() { Identifier = "unknown@example.invalid" });
            Assert.Equal(0, email.Requests);
            await service.ForgotAsync(new() { Identifier = user.Email });
            var first = email.Token!; Assert.Equal(64, first.Length);
            var stored = (await users.GetByNicAsync(user.Nic))!;
            Assert.NotEqual(first, stored.PasswordResetTokenHash);
            Assert.Equal(clock.Now.UtcDateTime.AddMinutes(20).Ticks / 10000, stored.PasswordResetExpiresAtUtc!.Value.Ticks / 10000);
            clock.Now = clock.Now.AddMinutes(2);
            await service.ForgotAsync(new() { Identifier = user.Nic });
            var second = email.Token!; Assert.NotEqual(first, second);
            await Assert.ThrowsAsync<BadRequestException>(() => service.ResetAsync(new() { Token = first, NewPassword = "Replacement-Test-Password" }));
            await Assert.ThrowsAsync<BadRequestException>(() => service.ResetAsync(new() { Token = "malformed", NewPassword = "Replacement-Test-Password" }));
            await Assert.ThrowsAsync<BadRequestException>(() => service.ResetAsync(new() { Token = second, NewPassword = "short" }));
            async Task<bool> Attempt() {
                // Capture whether this competing reset request wins the one-use conditional update.
                try { await service.ResetAsync(new() { Token = second, NewPassword = "Replacement-Test-Password" }); return true; } catch (BadRequestException) { return false; } }
            var winners = await Task.WhenAll(Attempt(), Attempt()); Assert.Single(winners, x => x);
            stored = (await users.GetByNicAsync(user.Nic))!;
            Assert.Null(stored.PasswordResetTokenHash); Assert.Null(stored.PasswordResetExpiresAtUtc);
            Assert.Equal(1, stored.SecurityVersion);
            Assert.False(hasher.VerifyPassword(stored, stored.PasswordHash, "Original-Test-Password"));
            Assert.True(hasher.VerifyPassword(stored, stored.PasswordHash, "Replacement-Test-Password"));
            Assert.Single(stored.AuditHistory, x => x.Event == "PasswordResetCompleted");
            Assert.Single(stored.Notifications); Assert.Equal("Security", stored.Notifications[0].Category);
            await Assert.ThrowsAsync<BadRequestException>(() => service.ResetAsync(new() { Token = second, NewPassword = "Another-Test-Password" }));
            await Assert.ThrowsAsync<BadRequestException>(() => service.ChangeAsync(new() { CurrentPassword = "wrong", NewPassword = "Another-Test-Password" }));
            email.Fail = true;
            await service.ChangeAsync(new() { CurrentPassword = "Replacement-Test-Password", NewPassword = "Changed-Test-Password" });
            stored = (await users.GetByNicAsync(user.Nic))!;
            Assert.Equal(2, stored.SecurityVersion); Assert.Equal(1, identity.Failures);
            Assert.True(hasher.VerifyPassword(stored, stored.PasswordHash, "Changed-Test-Password"));
            Assert.Single(stored.AuditHistory, x => x.Event == "PasswordChanged");
            await service.ForgotAsync(new() { Identifier = user.Nic }); // SMTP failure stays generic.
            Assert.Equal(2, identity.Failures);
            var expired = email.Token!; clock.Now = clock.Now.AddMinutes(21);
            await Assert.ThrowsAsync<BadRequestException>(() => service.ResetAsync(new() { Token = expired, NewPassword = "Expired-Test-Password" }));
            var raw = await db.GetCollection<BsonDocument>(CollectionNames.Users).Find(new BsonDocument("_id", user.Nic)).SingleAsync();
            Assert.DoesNotContain(first, raw.ToJson()); Assert.DoesNotContain(second, raw.ToJson());
            Assert.DoesNotContain("Changed-Test-Password", raw.ToJson());
        }
        finally { await client.DropDatabaseAsync(name); }
    }
    [MongoFact]
    public async Task NotificationsAreBoundedDeduplicatedAndReadWithoutErasingProfile()
    {
        // Verify retention, delivery deduplication and read updates preserve unrelated profile data.
        MongoMappings.Register();
        var client = new MongoClient(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N"); var db = client.GetDatabase(name);
        try
        {
            await new MongoDbInitializer(db).InitializeAsync();
            var users = new UserRepository(db); var experience = new ExperienceRepository(db);
            var user = new User { Nic="200012345678", Email="inbox@example.invalid", Role=UserRole.GridOperator,Status=UserStatus.Active };
            await users.InsertAsync(user);
            var catalog = new StationCatalogRepository(db);
            var station = new SolarStation { Name="Test station", CreatedAtUtc=DateTime.UtcNow, UpdatedAtUtc=DateTime.UtcNow };
            await catalog.InsertStationAsync(station,default);
            var dispatcher = new NotificationDispatcher(db);
            await dispatcher.DispatchAsync(default); await dispatcher.DispatchAsync(default);
            var inbox = await experience.InboxAsync(user.Nic,default);
            Assert.Single(inbox); // Routine catalog edits are audit-only.
            var pending = (await users.GetByNicAsync(user.Nic))!;
            pending.Status = UserStatus.PendingActivation; await users.ReplaceAsync(pending);
            await dispatcher.DispatchAsync(default);
            inbox = await experience.InboxAsync(user.Nic,default); Assert.Equal(2,inbox.Count);
            await experience.ReadAsync(user.Nic,inbox[0].Id,DateTime.UtcNow,default);
            Assert.Single(await experience.InboxAsync(user.Nic,default), x=>x.ReadAtUtc is not null);
            await experience.ReadAsync("another-account",null,DateTime.UtcNow,default);
            Assert.Single(await experience.InboxAsync(user.Nic,default), x=>x.ReadAtUtc is null);
            await experience.SetAvatarAsync(user.Nic,[1,2,3],default);
            Assert.NotNull((await experience.GetAvatarAsync(user.Nic,default)).Version);
            for(var i=0;i<105;i++) {
                var current=(await users.GetByNicAsync(user.Nic))!; current.Status = i % 2 == 0 ? UserStatus.Active : UserStatus.PendingActivation; await users.ReplaceAsync(current);
            }
            await dispatcher.DispatchAsync(default);
            var currentUser=(await users.GetByNicAsync(user.Nic))!;
            Assert.Equal(100,currentUser.AuditHistory.Count); Assert.Equal(100,currentUser.Notifications.Count);
            Assert.Equal(new byte[]{1,2,3},currentUser.AvatarBytes);
            await Assert.ThrowsAsync<ForbiddenException>(()=>experience.AuditAsync("reservations","any",user.Nic,UserRole.Backoffice,default));
            await Assert.ThrowsAsync<ForbiddenException>(()=>experience.ExportAsync("users",new(),user.Nic,UserRole.GridOperator,default));
        }
        finally { await client.DropDatabaseAsync(name); }
    }
}
