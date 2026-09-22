/*
 * File: MongoFoundationTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies MongoDB collection contracts, identifiers and uniqueness in an isolated test database.
 */
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Persistence;
using SmartSolar.Infrastructure.Persistence.Repositories;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class MongoFactAttribute : FactAttribute
{
    public MongoFactAttribute()
    {
        // Opt in explicitly; unavailable configured infrastructure is a failure, never a silently passing test.
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")))
            Skip = "TEST NOT EXECUTED DUE TO ENVIRONMENT: set SMARTSOLAR_TEST_MONGO to an isolated MongoDB server connection string.";
    }
}

public sealed class MongoFoundationTests
{
    [MongoFact]
    public async Task InitializerAndRepositoryPreserveTheCollectionContract()
    {
        // Create only a uniquely named test database, then remove that exact database in finally.
        MongoMappings.Register();
        var settings = MongoClientSettings.FromConnectionString(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        settings.ServerSelectionTimeout = TimeSpan.FromSeconds(5);
        var client = new MongoClient(settings);
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var database = client.GetDatabase(name);
        try
        {
            var initializer = new MongoDbInitializer(database);
            await initializer.InitializeAsync();
            await initializer.InitializeAsync();
            using var cursor = await database.ListCollectionNamesAsync();
            Assert.Equal(new[] { "EnergyBookingSlots", "EnergyReservation", "SolarStationInfo", "UsersDetail" }, (await cursor.ToListAsync()).OrderBy(x => x).ToArray());
            var users = new UserRepository(database);
            await users.InsertAsync(new User { Nic = "200012345678", Email = "test@example.com", Role = UserRole.Prosumer, Status = UserStatus.PendingActivation });
            var raw = await database.GetCollection<BsonDocument>(CollectionNames.Users).Find(new BsonDocument("_id", "200012345678")).SingleAsync();
            Assert.Equal("Prosumer", raw["Role"].AsString);
            Assert.Equal("PendingActivation", raw["Status"].AsString);
            Assert.Equal("200012345678", (await users.GetByEmailAsync("test@example.com"))!.Nic);
            await Assert.ThrowsAsync<ConflictException>(() => users.InsertAsync(new User { Nic = "199912345678", Email = "test@example.com" }));
            var user = (await users.GetByNicAsync("200012345678"))!;
            user.Status = UserStatus.Active;
            await users.ReplaceAsync(user);
            Assert.Single(await users.GetByStatusAsync(UserStatus.Active));
        }
        finally { await client.DropDatabaseAsync(name); }
    }
}
