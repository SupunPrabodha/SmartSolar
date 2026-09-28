/*
 * File: AuthFoundationTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies shared account lifecycle and password behavior without a database.
 */
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Auth;
using SmartSolar.Application.DTOs.Users;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Security;
using Xunit;

namespace SmartSolar.UnitTests;

public sealed class AuthFoundationTests
{
    [Fact]
    public async Task RegistrationNormalizesIdentityAndRequiresActivation()
    {
        // Registration stores a one-way hash and never issues a token to a pending account.
        var users = new MemoryUsers();
        var tokens = new TestTokens();
        var auth = new AuthService(users, new PasswordService(), tokens);
        var response = await auth.RegisterProsumerAsync(new RegisterProsumerRequest
        {
            Nic = "123456789v", FullName = " Test User ", Email = "TEST@example.com", PhoneNumber = "0771234567", Password = "test-only-password"
        });
        Assert.Equal("123456789V", response.Nic);
        Assert.Equal("test@example.com", response.Email);
        Assert.Equal(UserRole.Prosumer, response.Role);
        Assert.Equal(UserStatus.PendingActivation, response.Status);
        Assert.NotEqual("test-only-password", users.Items.Single().PasswordHash);
        await Assert.ThrowsAsync<ForbiddenException>(() => auth.LoginAsync(new LoginRequest { Nic = response.Nic, Password = "test-only-password" }));
        Assert.Equal(0, tokens.Issued);
    }

    [Fact]
    public async Task ActivationAllowsLoginAndDeactivationRejectsLogin()
    {
        // Backoffice lifecycle operations determine whether the authentication service issues tokens.
        var users = new MemoryUsers();
        var passwords = new PasswordService();
        var user = new User { Nic = "200012345678", Role = UserRole.Prosumer, Status = UserStatus.PendingActivation };
        user.PasswordHash = passwords.HashPassword(user, "test-only-password");
        users.Items.Add(user);
        var service = new UserService(users, passwords);
        var tokens = new TestTokens();
        var auth = new AuthService(users, passwords, tokens);
        var request = new LoginRequest { Nic = user.Nic, Password = "test-only-password" };
        await service.ActivateAsync(user.Nic);
        Assert.Equal(UserStatus.Active, (await auth.LoginAsync(request)).User.Status);
        await service.RequestOwnDeactivationAsync(user.Nic);
        await Assert.ThrowsAsync<ForbiddenException>(() => auth.LoginAsync(request));
        Assert.Equal(1, tokens.Issued);
    }

    [Fact]
    public async Task InvalidCredentialsAndDuplicateIdentityAreRejected()
    {
        // Reject duplicates and wrong passwords before issuing a token or inserting another account.
        var users = new MemoryUsers();
        var auth = new AuthService(users, new PasswordService(), new TestTokens());
        var request = new RegisterProsumerRequest { Nic = "200012345678", FullName = "Test User", Email = "test@example.com", PhoneNumber = "0771234567", Password = "test-only-password" };
        await auth.RegisterProsumerAsync(request);
        await Assert.ThrowsAsync<ConflictException>(() => auth.RegisterProsumerAsync(request));
        await Assert.ThrowsAsync<UnauthorizedException>(() => auth.LoginAsync(new LoginRequest { Nic = request.Nic, Password = "wrong" }));
        await Assert.ThrowsAsync<UnauthorizedException>(() => auth.LoginAsync(new LoginRequest { Nic = "199912345678", Password = "wrong" }));
        Assert.Single(users.Items);
    }

    [Fact]
    public void PasswordHashesAreSaltedAndVerifyOnlyMatchingPassword()
    {
        // Identical passwords must produce different salted hashes and reject incorrect input.
        var passwords = new PasswordService();
        var user = new User();
        var first = passwords.HashPassword(user, "test-only-password");
        Assert.NotEqual(first, passwords.HashPassword(user, "test-only-password"));
        Assert.True(passwords.VerifyPassword(user, first, "test-only-password"));
        Assert.False(passwords.VerifyPassword(user, first, "wrong"));
    }

    [Fact]
    public async Task BackofficeProsumerUpdateKeepsIdentityAndRejectsStaffProfiles()
    {
        // Managed profile edits preserve the NIC, role and lifecycle state required by account administration.
        var users = new MemoryUsers();
        var prosumer = new User { Nic = "200012345678", FullName = "Before", Email = "before@example.com", PhoneNumber = "0771234567", Role = UserRole.Prosumer, Status = UserStatus.Active };
        var otherProsumer = new User { Nic = "200012345677", Email = "taken@example.com", Role = UserRole.Prosumer, Status = UserStatus.Active };
        var operatorUser = new User { Nic = "200012345679", Role = UserRole.GridOperator, Status = UserStatus.Active };
        users.Items.Add(prosumer); users.Items.Add(otherProsumer); users.Items.Add(operatorUser);
        var service = new UserService(users, new PasswordService());
        var updated = await service.UpdateProsumerAsync(prosumer.Nic, new UpdateProsumerRequest { FullName = "After Name", Email = "after@example.com", PhoneNumber = "0712345678" });
        Assert.Equal("200012345678", updated.Nic); Assert.Equal(UserRole.Prosumer, updated.Role); Assert.Equal(UserStatus.Active, updated.Status);
        await Assert.ThrowsAsync<ConflictException>(() => service.UpdateProsumerAsync(prosumer.Nic, new UpdateProsumerRequest { FullName = "Duplicate", Email = "taken@example.com", PhoneNumber = "0712345678" }));
        await Assert.ThrowsAsync<BadRequestException>(() => service.UpdateProsumerAsync(operatorUser.Nic, new UpdateProsumerRequest { FullName = "Operator", Email = "operator@example.com", PhoneNumber = "0712345678" }));
    }

    private sealed class TestTokens : IJwtTokenService
    {
        public int Issued { get; private set; }
        public TokenResult CreateToken(User user)
        {
            // Count issuance without creating a real signing key or usable JWT.
            Issued++;
            return new TokenResult("test-token-not-a-jwt", DateTime.UtcNow.AddMinutes(5));
        }
    }

    private sealed class MemoryUsers : IUserRepository
    {
        // This test double isolates application rules from persistence; MongoDB has separate integration tests.
        public List<User> Items { get; } = [];
        public Task<User?> GetByNicAsync(string nic, CancellationToken cancellationToken = default) => Task.FromResult(Items.Find(x => x.Nic == nic));
        public Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default) => Task.FromResult(Items.Find(x => x.Email == email));
        public Task<IReadOnlyList<User>> GetAllAsync(CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<User>>(Items);
        public Task<IReadOnlyList<User>> GetByStatusAsync(UserStatus status, CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<User>>(Items.Where(x => x.Status == status).ToList());
        public Task InsertAsync(User user, CancellationToken cancellationToken = default) { Items.Add(user); return Task.CompletedTask; }
        public Task ReplaceAsync(User user, CancellationToken cancellationToken = default) => Task.CompletedTask;
    }
}
