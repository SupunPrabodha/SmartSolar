/*
 * File: MergedAccountCatalogTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies merged account lifecycle and catalog authorization against the real API and MongoDB.
 */
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class MergedAccountCatalogTests
{
    [MongoFact]
    public async Task AccountLifecyclePreservesIdentityAndControlsCatalogAccess()
    {
        // Use an isolated database and random test credentials; no developer accounts are modified.
        var connection = Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")!;
        var database = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var mongo = new MongoClient(connection);
        try
        {
            await using var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
            {
                builder.UseEnvironment("Testing");
                var settings = new Dictionary<string, string?> {
                    ["MongoDb:ConnectionString"] = connection, ["MongoDb:DatabaseName"] = database,
                    ["Jwt:Key"] = Convert.ToHexString(System.Security.Cryptography.RandomNumberGenerator.GetBytes(32)),
                    ["Jwt:Issuer"] = "merged-tests", ["Jwt:Audience"] = "merged-tests", ["Jwt:ExpiryMinutes"] = "15"
                };
                foreach (var setting in settings) builder.UseSetting(setting.Key, setting.Value);
            });
            using var client = factory.CreateClient(new() { BaseAddress = new Uri("https://localhost"), AllowAutoRedirect = false });
            using var scope = factory.Services.CreateScope();
            var users = scope.ServiceProvider.GetRequiredService<IUserRepository>();
            var jwt = scope.ServiceProvider.GetRequiredService<IJwtTokenService>();
            var backoffice = new User { Nic = "199000000001", FullName = "Test Backoffice", Email = "admin@example.invalid",
                Role = UserRole.Backoffice, Status = UserStatus.Active };
            await users.InsertAsync(backoffice);
            var adminToken = jwt.CreateToken(backoffice).AccessToken;
            var password = Guid.NewGuid().ToString("N");
            const string nic = "199000000002";
            var login = new { nic, password };
            client.DefaultRequestHeaders.Authorization = new("Bearer", adminToken);
            await Send(client, HttpMethod.Post, "/api/v1/users/staff", new {
                nic = "199000000003", fullName = "Test Operator", email = "operator@example.invalid",
                phoneNumber = "0770000000", password, role = "GridOperator"
            }, 201);
            var operatorLogin = await Send(client, HttpMethod.Post, "/api/v1/auth/login",
                new { nic = "199000000003", password }, 200);
            var operatorToken = operatorLogin.GetProperty("accessToken").GetString()!;
            var registered = await Send(client, HttpMethod.Post, "/api/v1/auth/register-prosumer", new {
                nic, fullName = "Test Prosumer", email = "prosumer@example.invalid", phoneNumber = "0770000001", password
            }, 201);
            Assert.Equal("PendingActivation", registered.GetProperty("status").GetString());
            await Send(client, HttpMethod.Post, "/api/v1/auth/login", login, 403);
            await Send(client, HttpMethod.Patch, "/api/v1/users/" + nic + "/activate", null, 204);
            var signedIn = await Send(client, HttpMethod.Post, "/api/v1/auth/login", login, 200);
            var prosumerToken = signedIn.GetProperty("accessToken").GetString()!;
            var original = (await users.GetByNicAsync(nic))!;
            var station = await Send(client, HttpMethod.Post, "/api/v1/stations", new {
                name = "Merged fixture", address = "Test road", latitude = 6.9, longitude = 79.8, capacityKwh = 50,
                totalBatterySlots = 10,
                operatingSchedule = Enumerable.Range(1, 7).Select(day => new { day = day, isClosed = false, opensAt = "00:00", closesAt = "24:00" })
            }, 201);
            var stationId = station.GetProperty("stationId").GetString()!;
            client.DefaultRequestHeaders.Authorization = new("Bearer", operatorToken);
            await Send(client, HttpMethod.Get, "/api/v1/users", null, 403);
            await Send(client, HttpMethod.Patch, "/api/v1/users/" + nic + "/activate", null, 403);
            var slot = await Send(client, HttpMethod.Post, "/api/v1/stations/" + stationId + "/slots", new {
                startAtUtc = "2030-01-01T00:00:00Z", endAtUtc = "2030-01-01T01:00:00Z", totalSlots = 5, availableSlots = 5
            }, 201);
            Assert.Equal(stationId, slot.GetProperty("stationId").GetString());

            // Profile edits from either client must retain identity, credentials and lifecycle state.
            client.DefaultRequestHeaders.Authorization = new("Bearer", prosumerToken);
            await Send(client, HttpMethod.Put, "/api/v1/users/me", new {
                fullName = "Self Edited", email = "self@example.invalid", phoneNumber = "0770000002"
            }, 200);
            await Send(client, HttpMethod.Get, "/api/v1/stations/" + stationId, null, 200);
            client.DefaultRequestHeaders.Authorization = new("Bearer", adminToken);
            await Send(client, HttpMethod.Put, "/api/v1/users/" + nic, new {
                fullName = "Admin Edited", email = "edited@example.invalid", phoneNumber = "0770000003",
                nic = "199999999999", role = "Backoffice", status = "Deactivated", password = "Ignored-test-value"
            }, 200);
            var edited = (await users.GetByNicAsync(nic))!;
            Assert.Equal("Admin Edited", edited.FullName);
            Assert.Equal(original.Nic, edited.Nic);
            Assert.Equal(original.Role, edited.Role);
            Assert.Equal(original.Status, edited.Status);
            Assert.Equal(original.PasswordHash, edited.PasswordHash);
            Assert.Equal(original.CreatedAtUtc, edited.CreatedAtUtc);

            // Account deactivation invalidates the same JWT for both members' endpoints.
            client.DefaultRequestHeaders.Authorization = new("Bearer", prosumerToken);
            await Send(client, HttpMethod.Post, "/api/v1/users/me/deactivation-request", null, 204);
            await Send(client, HttpMethod.Get, "/api/v1/users/me", null, 401);
            await Send(client, HttpMethod.Get, "/api/v1/stations/" + stationId, null, 401);
            await Send(client, HttpMethod.Post, "/api/v1/auth/login", login, 403);
            client.DefaultRequestHeaders.Authorization = new("Bearer", operatorToken);
            await Send(client, HttpMethod.Patch, "/api/v1/users/" + nic + "/activate", null, 403);
            client.DefaultRequestHeaders.Authorization = new("Bearer", adminToken);
            await Send(client, HttpMethod.Patch, "/api/v1/users/" + nic + "/activate", null, 204);
            var restored = await Send(client, HttpMethod.Post, "/api/v1/auth/login", login, 200);
            client.DefaultRequestHeaders.Authorization = new("Bearer", restored.GetProperty("accessToken").GetString());
            var profile = await Send(client, HttpMethod.Get, "/api/v1/users/me", null, 200);
            Assert.Equal(nic, profile.GetProperty("nic").GetString());
            var unchanged = await Send(client, HttpMethod.Get, "/api/v1/stations/" + stationId, null, 200);
            Assert.Equal(stationId, unchanged.GetProperty("stationId").GetString());
            var slots = await Send(client, HttpMethod.Get, "/api/v1/stations/" + stationId + "/slots", null, 200);
            Assert.Equal(slot.GetProperty("slotId").GetString(), Assert.Single(slots.EnumerateArray()).GetProperty("slotId").GetString());
        }
        finally { await mongo.DropDatabaseAsync(database); }
    }

    private static async Task<JsonElement> Send(HttpClient client, HttpMethod method, string path, object? body, int status)
    {
        // Assert the HTTP boundary while keeping JWTs and credentials out of assertion output.
        using var request = new HttpRequestMessage(method, path);
        if (body is not null) request.Content = JsonContent.Create(body);
        using var response = await client.SendAsync(request);
        Assert.Equal(status, (int)response.StatusCode);
        if (status == 204) return default;
        if (status >= 400) Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        return json.RootElement.Clone();
    }
}
