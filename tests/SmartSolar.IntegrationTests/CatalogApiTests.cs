/*
 * File: CatalogApiTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exercises actual JWT authorization, HTTP contracts and Mongo catalog persistence.
 */
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Stations;
using SmartSolar.Application.DTOs.Slots;
using SmartSolar.Application.Services;
using SmartSolar.Application.Exceptions;
using SmartSolar.Infrastructure.Persistence;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Persistence.Repositories;
using Xunit;
namespace SmartSolar.IntegrationTests;

public sealed class CatalogApiTests
{
    [MongoFact]
    public async Task CatalogHttpRolesErrorsPersistenceAndReferenceGuards()
    {
        // Boot the real API with an isolated database and random test-only signing key.
        var connection = Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")!;
        var dbName = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var mongo = new MongoClient(connection); var db = mongo.GetDatabase(dbName);
        try
        {
            await using var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
            {
                builder.UseEnvironment("Testing");
                var settings = new Dictionary<string, string?> {
                    ["MongoDb:ConnectionString"] = connection, ["MongoDb:DatabaseName"] = dbName,
                    ["Jwt:Key"] = Convert.ToHexString(System.Security.Cryptography.RandomNumberGenerator.GetBytes(32)),
                    ["Jwt:Issuer"] = "catalog-tests", ["Jwt:Audience"] = "catalog-tests", ["Jwt:ExpiryMinutes"] = "15" };
                foreach (var setting in settings) builder.UseSetting(setting.Key, setting.Value);
            });
            using var client = factory.CreateClient(new() { BaseAddress = new Uri("https://localhost"), AllowAutoRedirect = false });
            await Problem(client, HttpMethod.Get, "/api/v1/stations", null, 401);
            using var scope = factory.Services.CreateScope();
            var users = scope.ServiceProvider.GetRequiredService<IUserRepository>();
            var jwt = scope.ServiceProvider.GetRequiredService<IJwtTokenService>();
            var tokens = new Dictionary<UserRole, string>();
            foreach (var role in Enum.GetValues<UserRole>())
            {
                var user = new User { Nic = "test-" + role, FullName = "Catalog test", Email = role + "@example.invalid", Role = role, Status = UserStatus.Active };
                await users.InsertAsync(user); tokens[role] = jwt.CreateToken(user).AccessToken;
            }
            var body = new StationRequest { Name = "Node [A]", Address = "Test road", Latitude = 6.9, Longitude = 79.8,
                CapacityKwh = 50, TotalBatterySlots = 10,
                OperatingSchedule = Enumerable.Range(1, 7).Select(day => new OperatingDayDto(day, false, "00:00", "24:00")).ToList() };
            foreach (var role in new[] { UserRole.Prosumer, UserRole.GridOperator })
            {
                client.DefaultRequestHeaders.Authorization = new("Bearer", tokens[role]);
                await Problem(client, HttpMethod.Post, "/api/v1/stations", body, 403);
            }
            client.DefaultRequestHeaders.Authorization = new("Bearer", tokens[UserRole.Backoffice]);
            await Problem(client, HttpMethod.Post, "/api/v1/stations", new { name = "Incomplete" }, 400);
            using var create = await client.PostAsJsonAsync("/api/v1/stations", body);
            Assert.Equal(HttpStatusCode.Created, create.StatusCode); Assert.NotNull(create.Headers.Location);
            var station = (await create.Content.ReadFromJsonAsync<StationResponse>())!;
            Assert.Equal(7, station.OperatingSchedule.Count);
            var stationPath = "/api/v1/stations/" + station.StationId;
            var raw = await db.GetCollection<BsonDocument>(CollectionNames.Stations).Find(new BsonDocument("_id", station.StationId)).SingleAsync();
            Assert.Equal(station.StationId, raw["_id"].AsString); Assert.Equal(7, raw["OperatingSchedule"].AsBsonArray.Count);
            Assert.False(raw.Contains("DistanceKm"));
            var literalSearch = await client.GetFromJsonAsync<StationResponse[]>("/api/v1/stations?search=%5BA%5D");
            Assert.Single(literalSearch!);
            await Problem(client, HttpMethod.Get, "/api/v1/stations/missing", null, 404);
            await Problem(client, HttpMethod.Get, "/api/v1/stations/nearby?latitude=91&longitude=0", null, 400);
            var nearby = await client.GetFromJsonAsync<NearbyStationResponse[]>("/api/v1/stations/nearby?latitude=6.9&longitude=79.8");
            Assert.Equal(0, Assert.Single(nearby!).DistanceKm, 6);
            var slotBody = new SlotRequest { StartAtUtc = DateTimeOffset.Parse("2030-01-01T00:00:00Z"),
                EndAtUtc = DateTimeOffset.Parse("2030-01-01T01:00:00Z"), TotalSlots = 5, AvailableSlots = 4 };
            await Problem(client, HttpMethod.Post, stationPath + "/slots", slotBody, 403);
            client.DefaultRequestHeaders.Authorization = new("Bearer", tokens[UserRole.Prosumer]);
            await Problem(client, HttpMethod.Post, stationPath + "/slots", slotBody, 403);
            await Problem(client, HttpMethod.Get, "/api/v1/stations?includeInactive=true", null, 403);
            client.DefaultRequestHeaders.Authorization = new("Bearer", tokens[UserRole.GridOperator]);
            using var slotCreate = await client.PostAsJsonAsync(stationPath + "/slots", slotBody);
            Assert.Equal(HttpStatusCode.Created, slotCreate.StatusCode);
            var slot = (await slotCreate.Content.ReadFromJsonAsync<SlotResponse>())!;
            var rawSlot = await db.GetCollection<BsonDocument>(CollectionNames.BookingSlots).Find(new BsonDocument("_id", slot.SlotId)).SingleAsync();
            Assert.Equal(station.StationId, rawSlot["StationId"].AsString);
            await Problem(client, HttpMethod.Post, stationPath + "/slots", slotBody, 409);
            var slotPath = "/api/v1/slots/" + slot.SlotId;
            using var patch = await client.PatchAsJsonAsync(slotPath + "/availability", new { availableSlots = 2, expectedUpdatedAtUtc = slot.UpdatedAtUtc });
            Assert.Equal(HttpStatusCode.OK, patch.StatusCode);
            var changedSlot = (await patch.Content.ReadFromJsonAsync<SlotResponse>())!;
            await Problem(client, HttpMethod.Patch, slotPath + "/availability", new { availableSlots = 1, expectedUpdatedAtUtc = slot.UpdatedAtUtc }, 409);
            var repository = new StationCatalogRepository(db);
            var snapshot = (await repository.GetSlotAsync(slot.SlotId, default))!;
            snapshot.AvailableSlots = 1;
            Assert.False(await repository.UpdateSlotAsync(snapshot, slot.UpdatedAtUtc, default));
            Assert.Equal(2, (await repository.GetSlotAsync(slot.SlotId, default))!.AvailableSlots);
            var reservation = new EnergyReservation { StationId = station.StationId, SlotId = slot.SlotId,
                ProsumerNic = "test-Prosumer", Status = ReservationStatus.Pending };
            await db.GetCollection<EnergyReservation>(CollectionNames.Reservations).InsertOneAsync(reservation);
            await Problem(client, HttpMethod.Patch, slotPath + "/deactivate", new { expectedUpdatedAtUtc = changedSlot.UpdatedAtUtc }, 409);
            client.DefaultRequestHeaders.Authorization = new("Bearer", tokens[UserRole.Backoffice]);
            await Problem(client, HttpMethod.Patch, stationPath + "/deactivate", new { expectedUpdatedAtUtc = station.UpdatedAtUtc }, 409);
            // Remove only this isolated test fixture to exercise safe no-reference deactivation.
            await db.GetCollection<EnergyReservation>(CollectionNames.Reservations).DeleteOneAsync(x => x.ReservationId == reservation.ReservationId);
            using var deactivate = await client.PatchAsJsonAsync(stationPath + "/deactivate", new { expectedUpdatedAtUtc = station.UpdatedAtUtc });
            Assert.Equal(HttpStatusCode.NoContent, deactivate.StatusCode);
            client.DefaultRequestHeaders.Authorization = new("Bearer", tokens[UserRole.Prosumer]);
            await Problem(client, HttpMethod.Get, stationPath, null, 404);
            Assert.Empty((await client.GetFromJsonAsync<NearbyStationResponse[]>("/api/v1/stations/nearby?latitude=6.9&longitude=79.8"))!);
            // A legacy station without the new schedule deserializes with an empty, unconfigured schedule.
            raw.Remove("OperatingSchedule"); raw["_id"] = "legacy";
            await db.GetCollection<BsonDocument>(CollectionNames.Stations).InsertOneAsync(raw);
            Assert.Empty((await repository.GetStationAsync("legacy", default))!.OperatingSchedule);
        }
        finally { await mongo.DropDatabaseAsync(dbName); }
    }

    [MongoFact]
    public async Task PendingBlocksStationAndSlotProtection()
    {
        // Verify Pending against actual Mongo filtering and all protected catalog mutations.
        await AssertReservationProtectionAsync(ReservationStatus.Pending, true);
    }

    [MongoFact]
    public async Task ApprovedBlocksStationAndSlotProtection()
    {
        // Verify Approved against actual Mongo filtering and all protected catalog mutations.
        await AssertReservationProtectionAsync(ReservationStatus.Approved, true);
    }

    [MongoFact]
    public async Task RejectedDoesNotBlockStationAndSlotProtection()
    {
        // Verify Rejected against actual Mongo filtering and all protected catalog mutations.
        await AssertReservationProtectionAsync(ReservationStatus.Rejected, false);
    }

    [MongoFact]
    public async Task CancelledDoesNotBlockStationAndSlotProtection()
    {
        // Verify Cancelled against actual Mongo filtering and all protected catalog mutations.
        await AssertReservationProtectionAsync(ReservationStatus.Cancelled, false);
    }

    [MongoFact]
    public async Task CompletedDoesNotBlockStationAndSlotProtection()
    {
        // Verify Completed against actual Mongo filtering and all protected catalog mutations.
        await AssertReservationProtectionAsync(ReservationStatus.Completed, false);
    }

    [MongoFact]
    public async Task StationCapacityReductionUsesAllActiveReservationEnergy()
    {
        MongoMappings.Register();
        var client = new MongoClient(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var db = client.GetDatabase(name);
        try
        {
            var repository = new StationCatalogRepository(db);
            var gate = new CatalogWriteGate();
            var stations = new StationService(repository, repository, gate);
            var slots = new SlotService(repository, repository, gate);
            var station = await stations.CreateAsync(new StationRequest {
                Name = "Capacity fixture", Address = "Test road", Latitude = 6.9, Longitude = 79.8,
                CapacityKwh = 100, TotalBatterySlots = 10,
                OperatingSchedule = Enumerable.Range(1, 7).Select(day => new OperatingDayDto(day, true, null, null)).ToList()
            });
            var first = await slots.CreateAsync(station.StationId, new SlotRequest {
                StartAtUtc = DateTimeOffset.Parse("2030-01-01T00:00:00Z"),
                EndAtUtc = DateTimeOffset.Parse("2030-01-01T01:00:00Z"), TotalSlots = 2, AvailableSlots = 1
            });
            var second = await slots.CreateAsync(station.StationId, new SlotRequest {
                StartAtUtc = DateTimeOffset.Parse("2030-01-01T02:00:00Z"),
                EndAtUtc = DateTimeOffset.Parse("2030-01-01T03:00:00Z"), TotalSlots = 2, AvailableSlots = 1
            });
            var reservations = db.GetCollection<EnergyReservation>(CollectionNames.Reservations);
            await reservations.InsertManyAsync(new[] {
                new EnergyReservation { StationId = station.StationId, SlotId = first.SlotId, Status = ReservationStatus.Pending, EnergyAmountKwh = 60 },
                new EnergyReservation { StationId = station.StationId, SlotId = second.SlotId, Status = ReservationStatus.Approved, EnergyAmountKwh = 40 }
            });

            await Assert.ThrowsAsync<ConflictException>(() => stations.UpdateAsync(station.StationId,
                new StationRequest { Name = station.Name, Address = station.Address, Latitude = station.Latitude,
                    Longitude = station.Longitude, CapacityKwh = 99, TotalBatterySlots = station.TotalBatterySlots,
                    ExpectedUpdatedAtUtc = station.UpdatedAtUtc,
                    OperatingSchedule = station.OperatingSchedule.ToList() }));

            var unchanged = await stations.GetAsync(station.StationId, true);
            var boundary = await stations.UpdateAsync(station.StationId,
                new StationRequest { Name = unchanged.Name, Address = unchanged.Address, Latitude = unchanged.Latitude,
                    Longitude = unchanged.Longitude, CapacityKwh = 100, TotalBatterySlots = unchanged.TotalBatterySlots,
                    ExpectedUpdatedAtUtc = unchanged.UpdatedAtUtc,
                    OperatingSchedule = unchanged.OperatingSchedule.ToList() });
            Assert.Equal(100, boundary.CapacityKwh);
        }
        finally { await client.DropDatabaseAsync(name); }
    }

    private static async Task AssertReservationProtectionAsync(ReservationStatus status, bool blocks)
    {
        // Isolate each status fixture; catalog operations must never rewrite reservation history or references.
        MongoMappings.Register();
        var client = new MongoClient(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var db = client.GetDatabase(name);
        try
        {
            var repository = new StationCatalogRepository(db);
            var gate = new CatalogWriteGate();
            var stations = new StationService(repository, repository, gate);
            var slots = new SlotService(repository, repository, gate);
            var stationRequest = new StationRequest {
                Name = "Protection fixture", Address = "Test road", Latitude = 6.9, Longitude = 79.8,
                CapacityKwh = 50, TotalBatterySlots = 10,
                OperatingSchedule = Enumerable.Range(1, 7).Select(day => new OperatingDayDto(day, true, null, null)).ToList()
            };
            var slotRequest = new SlotRequest {
                StartAtUtc = DateTimeOffset.Parse("2030-01-01T00:00:00Z"),
                EndAtUtc = DateTimeOffset.Parse("2030-01-01T01:00:00Z"), TotalSlots = 5, AvailableSlots = 3
            };
            var station = await stations.CreateAsync(stationRequest);
            var slot = await slots.CreateAsync(station.StationId, slotRequest);
            var reservations = db.GetCollection<EnergyReservation>(CollectionNames.Reservations);
            var rawReservations = db.GetCollection<BsonDocument>(CollectionNames.Reservations);
            var reservation = new EnergyReservation {
                StationId = station.StationId, SlotId = slot.SlotId, ProsumerNic = "test-Prosumer",
                Status = status, EnergyAmountKwh = 2, CreatedAtUtc = station.CreatedAtUtc, UpdatedAtUtc = station.CreatedAtUtc
            };
            await reservations.InsertOneAsync(reservation);
            var original = await rawReservations.Find(new BsonDocument("_id", reservation.ReservationId)).SingleAsync();

            // An active reservation at a different station/slot must not affect this target.
            var otherStation = await stations.CreateAsync(stationRequest);
            var otherSlot = await slots.CreateAsync(otherStation.StationId, slotRequest);
            await reservations.InsertOneAsync(new EnergyReservation {
                StationId = otherStation.StationId, SlotId = otherSlot.SlotId,
                ProsumerNic = "other-test-Prosumer", Status = ReservationStatus.Pending
            });
            Assert.Equal(blocks, await repository.HasActiveStationReservationsAsync(station.StationId, default));
            Assert.Equal(blocks, await repository.HasActiveSlotReservationsAsync(slot.SlotId, default));
            Assert.True(await repository.HasActiveStationReservationsAsync(otherStation.StationId, default));
            Assert.True(await repository.HasActiveSlotReservationsAsync(otherSlot.SlotId, default));
            Assert.False(await repository.HasActiveStationReservationsAsync("missing", default));
            Assert.False(await repository.HasActiveSlotReservationsAsync("missing", default));

            var update = new SlotRequest {
                StartAtUtc = slot.StartAtUtc, EndAtUtc = slot.EndAtUtc, TotalSlots = 4,
                AvailableSlots = 2, ExpectedUpdatedAtUtc = slot.UpdatedAtUtc
            };
            var availability = new SlotAvailabilityRequest { AvailableSlots = 1, ExpectedUpdatedAtUtc = slot.UpdatedAtUtc };
            if (blocks)
            {
                await Assert.ThrowsAsync<ConflictException>(() => slots.UpdateAsync(slot.SlotId, update));
                await Assert.ThrowsAsync<ConflictException>(() => slots.AvailabilityAsync(slot.SlotId, availability));
                await Assert.ThrowsAsync<ConflictException>(() => slots.DeactivateAsync(slot.SlotId, new() { ExpectedUpdatedAtUtc = slot.UpdatedAtUtc }));
                await Assert.ThrowsAsync<ConflictException>(() => stations.DeactivateAsync(station.StationId, new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc }));
                var unchanged = await slots.GetAsync(slot.SlotId, true);
                Assert.Equal(slot, unchanged);
                Assert.Equal(station.UpdatedAtUtc, (await stations.GetAsync(station.StationId, true)).UpdatedAtUtc);
            }
            else
            {
                var updated = await slots.UpdateAsync(slot.SlotId, update);
                Assert.Equal(4, updated.TotalSlots); Assert.Equal(2, updated.AvailableSlots);
                Assert.True(updated.UpdatedAtUtc > slot.UpdatedAtUtc);
                // Terminal history permits mutation, but a stale timestamp must still reject it.
                await Assert.ThrowsAsync<ConflictException>(() => slots.AvailabilityAsync(slot.SlotId, availability));
                var available = await slots.AvailabilityAsync(slot.SlotId,
                    new() { AvailableSlots = 1, ExpectedUpdatedAtUtc = updated.UpdatedAtUtc });
                Assert.Equal(1, available.AvailableSlots);
                await slots.DeactivateAsync(slot.SlotId, new() { ExpectedUpdatedAtUtc = available.UpdatedAtUtc });
                await Assert.ThrowsAsync<ConflictException>(() => stations.DeactivateAsync(station.StationId,
                    new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc.AddMilliseconds(-1) }));
                await stations.DeactivateAsync(station.StationId, new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc });
            }
            var storedStation = (await repository.GetStationAsync(station.StationId, default))!;
            var storedSlot = (await repository.GetSlotAsync(slot.SlotId, default))!;
            Assert.Equal(station.StationId, storedStation.StationId);
            Assert.Equal(slot.SlotId, storedSlot.SlotId); Assert.Equal(station.StationId, storedSlot.StationId);
            Assert.Equal(station.CreatedAtUtc, storedStation.CreatedAtUtc); Assert.Equal(slot.CreatedAtUtc, storedSlot.CreatedAtUtc);
            Assert.Equal(blocks, storedStation.IsActive); Assert.Equal(blocks, storedSlot.IsActive);
            var unchangedReservation = await rawReservations.Find(new BsonDocument("_id", reservation.ReservationId)).SingleAsync();
            Assert.Equal(original, unchangedReservation);
            Assert.Equal(status.ToString(), unchangedReservation["Status"].AsString);
            Assert.Equal(reservation.ReservationId, unchangedReservation["_id"].AsString);
            Assert.Equal(station.StationId, unchangedReservation["StationId"].AsString);
            Assert.Equal(slot.SlotId, unchangedReservation["SlotId"].AsString);
            Assert.Equal(2, await reservations.CountDocumentsAsync(Builders<EnergyReservation>.Filter.Empty));
        }
        finally { await client.DropDatabaseAsync(name); }
    }

    private static async Task Problem(HttpClient client, HttpMethod method, string path, object? body, int status)
    {
        // Verify actual middleware/model-binding/auth responses all use the common problem contract.
        using var request = new HttpRequestMessage(method, path);
        if (body is not null) request.Content = JsonContent.Create(body);
        using var response = await client.SendAsync(request);
        Assert.Equal(status, (int)response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal(status, json.RootElement.GetProperty("status").GetInt32());
    }
}
