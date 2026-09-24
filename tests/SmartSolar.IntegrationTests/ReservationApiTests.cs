/*
 * File: ReservationApiTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exercises reservation HTTP routes, real JWT authorization and Mongo persistence.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Logging;
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class ReservationApiTests
{
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);
    private const string Root = "/api/v1/reservations";

    [MongoFact]
    public async Task ProsumerLifecycleReturnsCreatedLocationAndSummaries()
    {
        // Exercise routing, model binding, response serialization and persistence through the real app.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");
            using var created = await client.PostAsJsonAsync(Root, f.Request());
            Assert.Equal(HttpStatusCode.Created, created.StatusCode);
            var json = await Body(created);
            var id = json.GetProperty("reservationId").GetString()!;
            Assert.Equal("P1", json.GetProperty("prosumerNic").GetString());
            Assert.Equal("Pending", json.GetProperty("status").GetString());
            Assert.Equal(f.Slot.StationId, json.GetProperty("stationId").GetString());
            Assert.Equal(f.Slot.StartAtUtc, json.GetProperty("scheduledStartAtUtc").GetDateTime());
            Assert.EndsWith("Z", json.GetProperty("scheduledEndAtUtc").GetString());
            Assert.False(json.TryGetProperty("qrToken", out _));
            Assert.NotNull(created.Headers.Location);
            Assert.EndsWith(Root + "/" + id, created.Headers.Location.ToString());

            using var retrieved = await client.GetAsync(created.Headers.Location);
            Assert.Equal(HttpStatusCode.OK, retrieved.StatusCode);
            Assert.Equal(id, (await Body(retrieved)).GetProperty("reservationId").GetString());
            using var updated = await client.PutAsJsonAsync(Root + "/" + id, new { slotId = f.Slot.SlotId, energyAmountKwh = 2m });
            Assert.Equal(HttpStatusCode.OK, updated.StatusCode);
            Assert.Equal(2m, (await Body(updated)).GetProperty("energyAmountKwh").GetDecimal());
            using var cancelled = await client.PatchAsync(Root + "/" + id + "/cancel", null);
            Assert.Equal(HttpStatusCode.OK, cancelled.StatusCode);
            Assert.Equal("Cancelled", (await Body(cancelled)).GetProperty("status").GetString());
            Assert.Equal(f.Slot.TotalSlots, (await f.Slots.Find(x => x.SlotId == f.Slot.SlotId).SingleAsync()).AvailableSlots);
        });
    }

    [MongoFact]
    public async Task GridOperatorCanAssistCreateInspectUpdateAndCancel()
    {
        // Assisted operations use the dedicated creation route and the same lifecycle service.
        await WithApi(async f =>
        {
            using var client = f.Client("OP");
            using var created = await client.PostAsJsonAsync(Root + "/prosumers/P1", f.Request());
            Assert.Equal(HttpStatusCode.Created, created.StatusCode);
            var id = (await Body(created)).GetProperty("reservationId").GetString()!;
            using var retrieved = await client.GetAsync(Root + "/" + id);
            Assert.Equal(HttpStatusCode.OK, retrieved.StatusCode);
            await f.Reservations.UpdateOneAsync(x => x.ReservationId == id,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Approved).Set(x => x.QrToken, "stale"));
            var replacement = await f.AddSlot(Now.AddDays(3));
            using var updated = await client.PutAsJsonAsync(Root + "/" + id, f.Request(replacement));
            Assert.Equal(HttpStatusCode.OK, updated.StatusCode);
            Assert.Equal("Pending", (await Body(updated)).GetProperty("status").GetString());
            Assert.Null((await f.Reservations.Find(x => x.ReservationId == id).SingleAsync()).QrToken);
            using var cancelled = await client.PatchAsync(Root + "/" + id + "/cancel", null);
            Assert.Equal(HttpStatusCode.OK, cancelled.StatusCode);
            Assert.Equal("Cancelled", (await Body(cancelled)).GetProperty("status").GetString());
        });
    }

    [MongoFact]
    public async Task OtherProsumerCannotReadUpdateOrCancel()
    {
        // The NIC in the signed identity, not URL knowledge, determines Prosumer access.
        await WithApi(async f =>
        {
            var id = await f.Create("P1");
            using var other = f.Client("P2");
            using var read = await other.GetAsync(Root + "/" + id);
            await Problem(read, HttpStatusCode.Forbidden);
            using var update = await other.PutAsJsonAsync(Root + "/" + id, f.Request());
            await Problem(update, HttpStatusCode.Forbidden);
            using var cancel = await other.PatchAsync(Root + "/" + id + "/cancel", null);
            await Problem(cancel, HttpStatusCode.Forbidden);
            Assert.Equal(ReservationStatus.Pending, (await f.Reservations.Find(x => x.ReservationId == id).SingleAsync()).Status);
        });
    }

    [MongoFact]
    public async Task RouteRolesDoNotGrantBackofficeOrProsumerAssistance()
    {
        // Controller authorization narrows access without granting Backoffice a new capability.
        await WithApi(async f =>
        {
            var id = await f.Create("P1");
            using var backoffice = f.Client("BO");
            using var create = await backoffice.PostAsJsonAsync(Root, f.Request());
            await Problem(create, HttpStatusCode.Forbidden);
            using var assisted = await backoffice.PostAsJsonAsync(Root + "/prosumers/P2", f.Request());
            await Problem(assisted, HttpStatusCode.Forbidden);
            using var read = await backoffice.GetAsync(Root + "/" + id);
            await Problem(read, HttpStatusCode.Forbidden);
            using var update = await backoffice.PutAsJsonAsync(Root + "/" + id, f.Request());
            await Problem(update, HttpStatusCode.Forbidden);
            using var cancel = await backoffice.PatchAsync(Root + "/" + id + "/cancel", null);
            await Problem(cancel, HttpStatusCode.Forbidden);
            using var prosumer = f.Client("P1");
            using var forbiddenAssistance = await prosumer.PostAsJsonAsync(Root + "/prosumers/P2", f.Request());
            await Problem(forbiddenAssistance, HttpStatusCode.Forbidden);
            using var op = f.Client("OP");
            using var self = await op.PostAsJsonAsync(Root, f.Request());
            await Problem(self, HttpStatusCode.Forbidden);
        });
    }

    [MongoFact]
    public async Task MissingInvalidAndRevokedAuthenticationReturnProblem401()
    {
        // Real JWT validation includes the current Mongo account status and role check.
        await WithApi(async f =>
        {
            using var anonymous = f.Client();
            using var missing = await anonymous.PostAsJsonAsync(Root, f.Request());
            await Problem(missing, HttpStatusCode.Unauthorized);
            Assert.Contains(missing.Headers.WwwAuthenticate, x => x.Scheme == "Bearer");
            anonymous.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "not-a-jwt");
            using var invalid = await anonymous.GetAsync(Root + "/missing");
            await Problem(invalid, HttpStatusCode.Unauthorized);

            using var revoked = f.Client("P1");
            await f.Users.UpdateOneAsync(x => x.Nic == "P1", Builders<User>.Update.Set(x => x.Status, UserStatus.Deactivated));
            using var inactive = await revoked.PostAsJsonAsync(Root, f.Request());
            await Problem(inactive, HttpStatusCode.Unauthorized);
            using var changedRole = f.Client("OP");
            await f.Users.UpdateOneAsync(x => x.Nic == "OP", Builders<User>.Update.Set(x => x.Role, UserRole.Prosumer));
            using var roleMismatch = await changedRole.PostAsJsonAsync(Root + "/prosumers/P2", f.Request());
            await Problem(roleMismatch, HttpStatusCode.Unauthorized);
        });
    }

    [MongoFact]
    public async Task ExtraBodyFieldsCannotOverrideIdentityStatusScheduleOrQr()
    {
        // Existing JSON conventions ignore unknown fields, while the service derives protected values.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");
            using var response = await client.PostAsJsonAsync(Root, new
            {
                slotId = f.Slot.SlotId, energyAmountKwh = 1,
                prosumerNic = "P2", stationId = "wrong", status = "Completed", qrToken = "injected",
                scheduledStartAtUtc = Now.AddYears(1), reservationId = "injected"
            });
            Assert.Equal(HttpStatusCode.Created, response.StatusCode);
            var json = await Body(response);
            Assert.Equal("P1", json.GetProperty("prosumerNic").GetString());
            Assert.Equal("Pending", json.GetProperty("status").GetString());
            Assert.Equal(f.Slot.StartAtUtc, json.GetProperty("scheduledStartAtUtc").GetDateTime());
            var id = json.GetProperty("reservationId").GetString()!;
            Assert.NotEqual("injected", id);
            using var update = await client.PutAsJsonAsync(Root + "/" + id, new
            {
                slotId = f.Slot.SlotId, energyAmountKwh = 2, prosumerNic = "P2", status = "Completed", qrToken = "injected"
            });
            Assert.Equal(HttpStatusCode.OK, update.StatusCode);
            var stored = await f.Reservations.Find(x => x.ReservationId == id).SingleAsync();
            Assert.Equal("P1", stored.ProsumerNic);
            Assert.Equal(ReservationStatus.Pending, stored.Status);
            Assert.Null(stored.QrToken);
        });
    }

    [MongoFact]
    public async Task MalformedAndInvalidRequestsReturnValidationProblems()
    {
        // MVC binding and DTO validation reject bad inputs before any database mutation.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");
            foreach (var body in new[] { "{", "null", "{}", "{\"slotId\":\"bad\",\"energyAmountKwh\":1}",
                JsonSerializer.Serialize(new { slotId = f.Slot.SlotId, energyAmountKwh = 0 }),
                JsonSerializer.Serialize(new { slotId = f.Slot.SlotId, energyAmountKwh = -1 }) })
            {
                using var response = await client.PostAsync(Root, new StringContent(body, Encoding.UTF8, "application/json"));
                var problem = await Problem(response, HttpStatusCode.BadRequest);
                Assert.True(problem.TryGetProperty("errors", out _));
            }
            var id = await f.Create("P1");
            using var invalidUpdate = await client.PutAsJsonAsync(Root + "/" + id, new { slotId = f.Slot.SlotId, energyAmountKwh = 0 });
            await Problem(invalidUpdate, HttpStatusCode.BadRequest);
            Assert.Equal(1, await f.Reservations.CountDocumentsAsync(FilterDefinition<EnergyReservation>.Empty));
        });
    }

    [MongoFact]
    public async Task MissingResourcesReturn404()
    {
        // Lookup failures use the common middleware ProblemDetails contract.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");
            using var read = await client.GetAsync(Root + "/missing");
            await Problem(read, HttpStatusCode.NotFound);
            using var update = await client.PutAsJsonAsync(Root + "/missing", f.Request());
            await Problem(update, HttpStatusCode.NotFound);
            using var cancel = await client.PatchAsync(Root + "/missing/cancel", null);
            await Problem(cancel, HttpStatusCode.NotFound);
            using var slot = await client.PostAsJsonAsync(Root, new { slotId = Guid.NewGuid().ToString("N"), energyAmountKwh = 1 });
            await Problem(slot, HttpStatusCode.NotFound);
            using var op = f.Client("OP");
            using var prosumer = await op.PostAsJsonAsync(Root + "/prosumers/missing", f.Request());
            await Problem(prosumer, HttpStatusCode.NotFound);
            await f.Database.GetCollection<SolarStation>(CollectionNames.Stations).DeleteOneAsync(x => x.StationId == f.Station.StationId);
            using var station = await client.PostAsJsonAsync(Root, f.Request());
            await Problem(station, HttpStatusCode.NotFound);
        });
    }

    [MongoFact]
    public async Task SevenDayBoundaryIsEnforcedThroughHttp()
    {
        // Fixed business time allows exact inclusive/exclusive boundary verification through HTTP.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");
            var exact = await f.AddSlot(Now.AddDays(7));
            using var allowed = await client.PostAsJsonAsync(Root, f.Request(exact));
            Assert.Equal(HttpStatusCode.Created, allowed.StatusCode);
            foreach (var start in new[] { Now.AddDays(7).AddSeconds(1), Now, Now.AddSeconds(-1) })
            {
                var slot = await f.AddSlot(start);
                using var rejected = await client.PostAsJsonAsync(Root, f.Request(slot));
                await Problem(rejected, HttpStatusCode.BadRequest);
            }
        });
    }

    [MongoFact]
    public async Task TwelveHourBoundaryAlsoAppliesToOperatorAssistance()
    {
        // Operators get no cutoff bypass, and successful responses preserve summary semantics.
        await WithApi(async f =>
        {
            using var op = f.Client("OP");
            var exact = await f.AddSlot(Now.AddHours(12));
            using var created = await op.PostAsJsonAsync(Root + "/prosumers/P1", f.Request(exact));
            Assert.Equal(HttpStatusCode.Created, created.StatusCode);
            var id = (await Body(created)).GetProperty("reservationId").GetString()!;
            using var update = await op.PutAsJsonAsync(Root + "/" + id, f.Request(exact));
            Assert.Equal(HttpStatusCode.OK, update.StatusCode);
            using var cancel = await op.PatchAsync(Root + "/" + id + "/cancel", null);
            Assert.Equal(HttpStatusCode.OK, cancel.StatusCode);
            var near = await f.AddSlot(Now.AddSeconds(43199));
            using var createdNear = await op.PostAsJsonAsync(Root + "/prosumers/P1", f.Request(near));
            Assert.Equal(HttpStatusCode.Created, createdNear.StatusCode);
            var nearId = (await Body(createdNear)).GetProperty("reservationId").GetString()!;
            using var blockedUpdate = await op.PutAsJsonAsync(Root + "/" + nearId, f.Request(near));
            await Problem(blockedUpdate, HttpStatusCode.Conflict);
            using var blockedCancel = await op.PatchAsync(Root + "/" + nearId + "/cancel", null);
            await Problem(blockedCancel, HttpStatusCode.Conflict);
        });
    }

    [MongoFact]
    public async Task OverlapFullInactiveAndTerminalStatesReturn409()
    {
        // Business conflicts are preserved at the wire boundary rather than becoming 500 errors.
        await WithApi(async f =>
        {
            var id = await f.Create("P1");
            using var p1 = f.Client("P1");
            using var p2 = f.Client("P2");
            using var overlap = await p1.PostAsJsonAsync(Root, f.Request());
            await Problem(overlap, HttpStatusCode.Conflict);
            using var full = await p2.PostAsJsonAsync(Root, f.Request());
            await Problem(full, HttpStatusCode.Conflict);
            var inactive = await f.AddSlot(Now.AddDays(3));
            await f.Slots.UpdateOneAsync(x => x.SlotId == inactive.SlotId, Builders<EnergyBookingSlot>.Update.Set(x => x.IsActive, false));
            using var inactiveSlot = await p2.PostAsJsonAsync(Root, f.Request(inactive));
            await Problem(inactiveSlot, HttpStatusCode.Conflict);
            foreach (var status in new[] { ReservationStatus.Rejected, ReservationStatus.Cancelled, ReservationStatus.Completed })
            {
                await f.Reservations.UpdateOneAsync(x => x.ReservationId == id, Builders<EnergyReservation>.Update.Set(x => x.Status, status));
                using var cancel = await p1.PatchAsync(Root + "/" + id + "/cancel", null);
                await Problem(cancel, HttpStatusCode.Conflict);
                using var update = await p1.PutAsJsonAsync(Root + "/" + id, f.Request());
                await Problem(update, HttpStatusCode.Conflict);
            }
            await f.Database.GetCollection<SolarStation>(CollectionNames.Stations).UpdateOneAsync(
                x => x.StationId == f.Station.StationId, Builders<SolarStation>.Update.Set(x => x.IsActive, false));
            using var inactiveStation = await p2.PostAsJsonAsync(Root, f.Request());
            await Problem(inactiveStation, HttpStatusCode.Conflict);
        });
    }

    [MongoFact]
    public async Task AssistedCreationRejectsInactiveOrStaffTargets()
    {
        // Operator access does not make an inactive account or staff member a valid booking owner.
        await WithApi(async f =>
        {
            using var op = f.Client("OP");
            await f.Users.UpdateOneAsync(x => x.Nic == "P1", Builders<User>.Update.Set(x => x.Status, UserStatus.Deactivated));
            using var inactive = await op.PostAsJsonAsync(Root + "/prosumers/P1", f.Request());
            await Problem(inactive, HttpStatusCode.Forbidden);
            using var staff = await op.PostAsJsonAsync(Root + "/prosumers/BO", f.Request());
            await Problem(staff, HttpStatusCode.Forbidden);
        });
    }

    [MongoFact]
    public async Task ConcurrentHttpCreatesCannotBothTakeFinalPlace()
    {
        // Real concurrent HTTP requests exercise auth, separate request scopes and atomic Mongo capacity.
        await WithApi(async f =>
        {
            using var p1 = f.Client("P1");
            using var p2 = f.Client("P2");
            var results = await Task.WhenAll(p1.PostAsJsonAsync(Root, f.Request()), p2.PostAsJsonAsync(Root, f.Request()));
            try
            {
                Assert.Single(results, x => x.StatusCode == HttpStatusCode.Created);
                await Problem(Assert.Single(results, x => x.StatusCode == HttpStatusCode.Conflict), HttpStatusCode.Conflict);
                Assert.Equal(0, (await f.Slots.Find(x => x.SlotId == f.Slot.SlotId).SingleAsync()).AvailableSlots);
                Assert.Equal(1, await f.Reservations.CountDocumentsAsync(FilterDefinition<EnergyReservation>.Empty));
            }
            finally { foreach (var response in results) response.Dispose(); }
        });
    }

    [MongoFact]
    public async Task ConcurrentHttpCancellationReleasesOnlyOnce()
    {
        // The retrying client sees a conflict and cannot produce a second capacity release.
        await WithApi(async f =>
        {
            var id = await f.Create("P1");
            using var client = f.Client("P1");
            var results = await Task.WhenAll(client.PatchAsync(Root + "/" + id + "/cancel", null),
                client.PatchAsync(Root + "/" + id + "/cancel", null));
            try
            {
                Assert.Single(results, x => x.StatusCode == HttpStatusCode.OK);
                await Problem(Assert.Single(results, x => x.StatusCode == HttpStatusCode.Conflict), HttpStatusCode.Conflict);
                Assert.Equal(1, (await f.Slots.Find(x => x.SlotId == f.Slot.SlotId).SingleAsync()).AvailableSlots);
            }
            finally { foreach (var response in results) response.Dispose(); }
        });
    }

    [MongoFact]
    public async Task RecoveryAndUnexpectedDatabaseErrorsDoNotLeakInternals()
    {
        // Both fail-closed recovery conflicts and unforeseen serialization failures remain safe HTTP errors.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");
            await f.Users.UpdateOneAsync(x => x.Nic == "P1", Builders<User>.Update.Set(x => x.ReservationWriteLock, "abandoned"));
            using var locked = await client.PostAsJsonAsync(Root, f.Request());
            await Problem(locked, HttpStatusCode.Conflict);
            await f.Database.GetCollection<BsonDocument>(CollectionNames.Reservations).InsertOneAsync(new BsonDocument
            {
                { "_id", "broken" }, { "ProsumerNic", "P1" }, { "Status", "server-sensitive-test" }
            });
            using var unexpected = await client.GetAsync(Root + "/broken");
            var problem = await Problem(unexpected, HttpStatusCode.InternalServerError);
            Assert.Equal("An unexpected server error occurred.", problem.GetProperty("detail").GetString());
            Assert.DoesNotContain("server-sensitive-test", problem.GetRawText());
        });
    }


    [MongoFact]
    public async Task SwaggerDescribesReservationTestingContracts()
    {
        // Inspect the actual generated document from the application, including status codes and request examples.
        await WithApi(f =>
        {
            var swagger = f.Swagger();
            var listing = swagger.Paths[Root].Operations[Microsoft.OpenApi.Models.OperationType.Get];
            Assert.Contains("GridOperator", listing.Summary);
            Assert.Equal("array", listing.Responses["200"].Content["application/json"].Schema.Type);
            Assert.Equal(new[] { "prosumernic", "stationid", "status" },
                listing.Parameters.Select(x => x.Name.ToLowerInvariant()).OrderBy(x => x).ToArray());
            var create = swagger.Paths[Root].Operations[Microsoft.OpenApi.Models.OperationType.Post];
            Assert.Contains("Prosumer", create.Summary);
            Assert.Contains("placeholder", create.Description);
            Assert.True(create.Responses.ContainsKey("201"));
            Assert.False(create.Responses.ContainsKey("200"));
            Assert.True(create.Responses["201"].Headers.ContainsKey("Location"));
            foreach (var code in new[] { "400", "401", "403", "404", "409", "500" })
                Assert.True(create.Responses[code].Content.ContainsKey("application/problem+json"));
            Assert.Contains("GridOperator", swagger.Paths[Root + "/prosumers/{prosumerNic}"]
                .Operations[Microsoft.OpenApi.Models.OperationType.Post].Summary);
            Assert.Contains("12 hours", swagger.Paths[Root + "/{reservationId}"]
                .Operations[Microsoft.OpenApi.Models.OperationType.Put].Description);
            var cancel = swagger.Paths[Root + "/{reservationId}/cancel"].Operations[Microsoft.OpenApi.Models.OperationType.Patch];
            Assert.Null(cancel.RequestBody);
            Assert.True(cancel.Responses.ContainsKey("200"));
            foreach (var name in new[] { "CreateReservationRequest", "UpdateReservationRequest" })
            {
                var schema = swagger.Components.Schemas[name];
                Assert.NotNull(schema.Example);
                Assert.Contains("energyAmountKwh", schema.Required);
                Assert.Equal(0m, schema.Properties["energyAmountKwh"].Minimum);
                Assert.True(schema.Properties["energyAmountKwh"].ExclusiveMinimum);
                Assert.Equal(2, schema.Properties.Count);
            }
            return Task.CompletedTask;
        });
    }


    [MongoFact]
    public async Task ListingRequiresCurrentGridOperatorRole()
    {
        // The collection endpoint must not bypass the ownership restrictions on individual records.
        await WithApi(async f =>
        {
            await f.Create("P1");
            foreach (var nic in new[] { "P1", "P2", "BO" })
            {
                using var denied = f.Client(nic);
                using var response = await denied.GetAsync(Root + "?prosumerNic=P1");
                await Problem(response, HttpStatusCode.Forbidden);
            }
            using var anonymous = f.Client();
            using var missingAuth = await anonymous.GetAsync(Root);
            await Problem(missingAuth, HttpStatusCode.Unauthorized);
            using var op = f.Client("OP");
            using var allowed = await op.GetAsync(Root);
            Assert.Equal(HttpStatusCode.OK, allowed.StatusCode);
            Assert.Single((await Body(allowed)).EnumerateArray());
            await f.Users.UpdateOneAsync(x => x.Nic == "OP", Builders<User>.Update.Set(x => x.Status, UserStatus.Deactivated));
            using var revoked = await op.GetAsync(Root);
            await Problem(revoked, HttpStatusCode.Unauthorized);
        });
    }

    [MongoFact]
    public async Task ListingCombinesExactFiltersAndUsesStableOrder()
    {
        // Seed independent persisted summaries so filters are tested against both matches and near misses.
        await WithApi(async f =>
        {
            var otherStation = Guid.NewGuid().ToString("N");
            var rows = new[]
            {
                ("a", "P1", f.Station.StationId, ReservationStatus.Pending, Now),
                ("b", "P1", otherStation, ReservationStatus.Approved, Now.AddHours(1)),
                ("c", "P2", f.Station.StationId, ReservationStatus.Pending, Now.AddHours(1)),
                ("d", "P1", f.Station.StationId, ReservationStatus.Cancelled, Now.AddHours(-1))
            }.Select(row => new EnergyReservation
            {
                ReservationId = row.Item1, ProsumerNic = row.Item2, StationId = row.Item3,
                SlotId = f.Slot.SlotId, Status = row.Item4, CreatedAtUtc = row.Item5, UpdatedAtUtc = row.Item5,
                EnergyAmountKwh = 1, ScheduledStartAtUtc = Now.AddDays(2), ScheduledEndAtUtc = Now.AddDays(2).AddHours(1),
                QrToken = "must-not-leak"
            });
            await f.Reservations.InsertManyAsync(rows);
            using var op = f.Client("OP");
            var cases = new (string Query, string[] Ids)[]
            {
                ("", ["b", "c", "a", "d"]),
                ("?status=Pending", ["c", "a"]),
                ("?prosumerNic=%20p1%20", ["b", "a", "d"]),
                ("?stationId=" + f.Station.StationId, ["c", "a", "d"]),
                ("?status=Pending&prosumerNic=P1&stationId=" + f.Station.StationId, ["a"]),
                ("?prosumerNic=P", []),
                ("?status=Rejected", []),
                ("?status=Pending&prosumerNic=P2&stationId=" + otherStation, []),
                ("?prosumerNic=%20&stationId=%20", ["b", "c", "a", "d"])
            };
            foreach (var (query, ids) in cases)
            {
                using var response = await op.GetAsync(Root + query);
                Assert.Equal(HttpStatusCode.OK, response.StatusCode);
                var records = (await Body(response)).EnumerateArray().ToArray();
                Assert.Equal(ids, records.Select(x => x.GetProperty("reservationId").GetString()));
                Assert.All(records, x => Assert.False(x.TryGetProperty("qrToken", out _)));
            }
        });
    }

    [MongoFact]
    public async Task ListingRejectsInvalidFiltersWithProblem400()
    {
        // Reject unrecognized names/numeric enum values and malformed station GUIDs.
        await WithApi(async f =>
        {
            using var op = f.Client("OP");
            foreach (var query in new[] { "?status=Unknown", "?status=999", "?stationId=bad",
                "?stationId=00000000-0000-0000-0000-000000000000" })
            {
                using var response = await op.GetAsync(Root + query);
                await Problem(response, HttpStatusCode.BadRequest);
            }
        });
    }

    [MongoFact]
    public async Task ListingPreservesLegacySnapshotRecoveryPolicy()
    {
        // Do not invent accepted times or silently omit a matching record that requires backfill.
        await WithApi(async f =>
        {
            var id = await f.Create("P1");
            await f.Reservations.UpdateOneAsync(x => x.ReservationId == id,
                Builders<EnergyReservation>.Update.Unset(x => x.ScheduledStartAtUtc));
            using var op = f.Client("OP");
            using var response = await op.GetAsync(Root);
            await Problem(response, HttpStatusCode.Conflict);
            using var empty = await op.GetAsync(Root + "?prosumerNic=P2");
            Assert.Equal(HttpStatusCode.OK, empty.StatusCode);
            Assert.Empty((await Body(empty)).EnumerateArray());
        });
    }

    private static async Task<JsonElement> Body(HttpResponseMessage response)
    {
        // Clone JSON so assertions do not depend on a disposed document.
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        return json.RootElement.Clone();
    }

    private static async Task<JsonElement> Problem(HttpResponseMessage response, HttpStatusCode expected)
    {
        // Validate the actual response media type and common ProblemDetails envelope.
        Assert.Equal(expected, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        var json = await Body(response);
        Assert.Equal((int)expected, json.GetProperty("status").GetInt32());
        Assert.False(string.IsNullOrWhiteSpace(json.GetProperty("title").GetString()));
        Assert.True(json.TryGetProperty("traceId", out _));
        return json;
    }

    private static async Task WithApi(Func<ApiFixture, Task> test)
    {
        // Run the real application against one uniquely named database, then dispose it before cleanup.
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var connection = Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")!;
        var factory = new ApiFactory(connection, name);
        try
        {
            var database = factory.Services.GetRequiredService<IMongoDatabase>();
            Assert.Equal(name, database.DatabaseNamespace.DatabaseName);
            var fixture = new ApiFixture(factory, database);
            await fixture.Seed();
            await test(fixture);
        }
        finally
        {
            await factory.DisposeAsync();
            await new MongoClient(connection).DropDatabaseAsync(name);
        }
    }

    private sealed class ApiFactory(string connection, string databaseName) : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            // Override only test configuration and business time; authentication and repositories remain real.
            builder.UseEnvironment("Testing");
            builder.UseSetting("MongoDb:ConnectionString", connection);
            builder.UseSetting("MongoDb:DatabaseName", databaseName);
            builder.UseSetting("Jwt:Key", Convert.ToHexString(RandomNumberGenerator.GetBytes(32)));
            builder.UseSetting("Jwt:Issuer", "SmartSolar.Tests");
            builder.UseSetting("Jwt:Audience", "SmartSolar.Tests");
            builder.UseSetting("Jwt:ExpiryMinutes", "60");
            builder.ConfigureLogging(logging => logging.ClearProviders());
            builder.ConfigureServices(services =>
            {
                services.RemoveAll<TimeProvider>();
                services.AddSingleton<TimeProvider>(new FixedClock());
            });
        }
    }

    private sealed class ApiFixture(ApiFactory factory, IMongoDatabase database)
    {
        public IMongoDatabase Database { get; } = database;
        public IMongoCollection<User> Users => Database.GetCollection<User>(CollectionNames.Users);
        public IMongoCollection<EnergyBookingSlot> Slots => Database.GetCollection<EnergyBookingSlot>(CollectionNames.BookingSlots);
        public IMongoCollection<EnergyReservation> Reservations => Database.GetCollection<EnergyReservation>(CollectionNames.Reservations);
        public SolarStation Station { get; } = new() { CapacityKwh = 100, IsActive = true };
        public EnergyBookingSlot Slot { get; private set; } = null!;
        private readonly Dictionary<string, User> _accounts = [];

        public async Task Seed()
        {
            // Seed isolated accounts directly; tests still use the real JWT issuer and validation pipeline.
            foreach (var pair in new[] { ("P1", UserRole.Prosumer), ("P2", UserRole.Prosumer),
                ("OP", UserRole.GridOperator), ("BO", UserRole.Backoffice) })
                _accounts[pair.Item1] = new User { Nic = pair.Item1, FullName = "Test User",
                    Email = pair.Item1 + "@example.test", Role = pair.Item2, Status = UserStatus.Active };
            await Users.InsertManyAsync(_accounts.Values);
            await Database.GetCollection<SolarStation>(CollectionNames.Stations).InsertOneAsync(Station);
            Slot = await AddSlot(Now.AddDays(2));
        }

        public HttpClient Client(string? nic = null)
        {
            // Valid HTTPS test requests traverse all production middleware without redirect or auth substitutes.
            var client = factory.CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("https://localhost"), AllowAutoRedirect = false });
            client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            if (nic is not null)
            {
                var token = factory.Services.GetRequiredService<IJwtTokenService>().CreateToken(_accounts[nic]);
                client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token.AccessToken);
            }
            return client;
        }

        public Microsoft.OpenApi.Models.OpenApiDocument Swagger()
        {
            // Resolve the same generator used by the Development Swagger JSON route.
            return factory.Services.GetRequiredService<Swashbuckle.AspNetCore.Swagger.ISwaggerProvider>().GetSwagger("v1");
        }
        public object Request(EnergyBookingSlot? slot = null)
        {
            // Supply only the two reviewed reservation request fields.
            return new { slotId = (slot ?? Slot).SlotId, energyAmountKwh = 1m };
        }

        public async Task<EnergyBookingSlot> AddSlot(DateTime start)
        {
            // A one-place slot makes capacity behavior observable.
            var slot = new EnergyBookingSlot { StationId = Station.StationId, StartAtUtc = start,
                EndAtUtc = start.AddHours(1), AvailableSlots = 1, TotalSlots = 1 };
            await Slots.InsertOneAsync(slot);
            return slot;
        }

        public async Task<string> Create(string nic)
        {
            // Fixture reservations are created through HTTP, not by bypassing lifecycle validation.
            using var client = Client(nic);
            using var response = await client.PostAsJsonAsync(Root, Request());
            Assert.Equal(HttpStatusCode.Created, response.StatusCode);
            return (await Body(response)).GetProperty("reservationId").GetString()!;
        }
    }

    private sealed class FixedClock : TimeProvider
    {
        public override DateTimeOffset GetUtcNow()
        {
            // Reservation rules use deterministic time; existing JWT lifetime validation remains real.
            return new DateTimeOffset(Now);
        }
    }
}
