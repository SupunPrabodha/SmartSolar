/*
 * File: DevelopmentDataSeeder.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Seeds a development Backoffice account without storing credentials in source control.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using System.ComponentModel.DataAnnotations;
using SmartSolar.Application.DTOs.Users;

namespace SmartSolar.Api.Seed;

public sealed class DevelopmentDataSeeder
{
    private readonly IUserRepository _users;
    private readonly IPasswordService _passwords;
    private readonly IConfiguration _configuration;

    public DevelopmentDataSeeder(
        IUserRepository users,
        IPasswordService passwords,
        IConfiguration configuration)
    {
        // Store dependencies required to create the local Backoffice seed account safely.
        _users = users;
        _passwords = passwords;
        _configuration = configuration;
    }

    public async Task SeedAsync(CancellationToken cancellationToken = default)
    {
        // Read seed credentials from user-secrets and create the Backoffice account only when it does not already exist.
        var nic = _configuration["SeedAdmin:Nic"]?.Trim().ToUpperInvariant();
        var password = _configuration["SeedAdmin:Password"];
        var email = _configuration["SeedAdmin:Email"]?.Trim().ToLowerInvariant();
        var fullName = _configuration["SeedAdmin:FullName"]?.Trim();
        var phone = _configuration["SeedAdmin:PhoneNumber"]?.Trim();

        if (new[] { nic, password, email, fullName, phone }.All(string.IsNullOrWhiteSpace)) return;

        if (string.IsNullOrWhiteSpace(nic) ||
            string.IsNullOrWhiteSpace(password) ||
            string.IsNullOrWhiteSpace(email) ||
            string.IsNullOrWhiteSpace(fullName) ||
            string.IsNullOrWhiteSpace(phone))
        {
            throw new InvalidOperationException("SeedAdmin configuration is incomplete. Supply all six SeedAdmin values or remove the section.");
        }

        // Apply the same input constraints as staff creation before persisting development data.
        var request = new CreateStaffRequest { Nic = nic, Password = password, Email = email, FullName = fullName, PhoneNumber = phone, Role = UserRole.Backoffice };
        Validator.ValidateObject(request, new ValidationContext(request), validateAllProperties: true);

        if (await _users.GetByNicAsync(nic, cancellationToken) is not null)
        {
            return;
        }

        var now = DateTime.UtcNow;
        var admin = new User
        {
            Nic = nic,
            FullName = fullName,
            Email = email,
            PhoneNumber = phone,
            Role = UserRole.Backoffice,
            Status = UserStatus.Active,
            CreatedAtUtc = now,
            UpdatedAtUtc = now
        };

        admin.PasswordHash = _passwords.HashPassword(admin, password);
        await _users.InsertAsync(admin, cancellationToken);
    }
}
