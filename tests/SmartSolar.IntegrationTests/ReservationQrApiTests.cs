/*
 * File: ReservationQrApiTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Tests QR issuance, rotation, server-side verification HTTP endpoints and MongoDB persistence.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Logging;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class ReservationQrApiTests
{
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);
    private const string Root = "/api/v1/reservations";

    [MongoFact]
    public async Task ApprovedReservationOwnerCanRequestQrAndReceivesOpaquePayload()
    {
        // Owner of an Approved reservation receives an opaque reference without internal hash or database secrets.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var client = f.Client("P1");

            using var response = await client.PostAsync($"{Root}/{resId}/qr", null);
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);

            var json = await Body(response);
            Assert.Equal(resId, json.GetProperty("reservationId").GetString());
            var qrPayload = json.GetProperty("qrPayload").GetString();
            Assert.NotNull(qrPayload);
            Assert.StartsWith("SMG1.", qrPayload);
            Assert.True(json.TryGetProperty("issuedAtUtc", out _));

            // Verify MongoDB stores one-way hash, not raw token
            var doc = await f.Reservations.Find(x => x.ReservationId == resId).FirstAsync();
            Assert.NotNull(doc.QrTokenHash);
            Assert.NotEqual(qrPayload, doc.QrTokenHash);
            Assert.Equal(64, doc.QrTokenHash.Length); // 64 hex chars for SHA-256
        });
    }

    [MongoFact]
    public async Task NonApprovedReservationsCannotIssueQr()
    {
        // Pending, Rejected, Cancelled and Completed reservations return 409 Conflict.
        await WithApi(async f =>
        {
            using var client = f.Client("P1");

            // 1. Pending
            var pendingId = await f.CreateReservation("P1");
            using var pendingResp = await client.PostAsync($"{Root}/{pendingId}/qr", null);
            Assert.Equal(HttpStatusCode.Conflict, pendingResp.StatusCode);

            // 2. Cancelled
            await f.Reservations.UpdateOneAsync(
                x => x.ReservationId == pendingId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Cancelled));
            using var cancelResp = await client.PostAsync($"{Root}/{pendingId}/qr", null);
            Assert.Equal(HttpStatusCode.Conflict, cancelResp.StatusCode);

            // 3. Completed
            await f.Reservations.UpdateOneAsync(
                x => x.ReservationId == pendingId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Completed));
            using var completeResp = await client.PostAsync($"{Root}/{pendingId}/qr", null);
            Assert.Equal(HttpStatusCode.Conflict, completeResp.StatusCode);
        });
    }

    [MongoFact]
    public async Task OtherProsumerCannotIssueOwnersQr()
    {
        // Another Prosumer receives 403 Forbidden.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var client = f.Client("P2");

            using var response = await client.PostAsync($"{Root}/{resId}/qr", null);
            Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        });
    }

    [MongoFact]
    public async Task UnauthenticatedQrRequestsReturn401()
    {
        // Requests without valid JWT return 401 Unauthorized.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var anonymous = f.Client(null);

            using var issueResp = await anonymous.PostAsync($"{Root}/{resId}/qr", null);
            Assert.Equal(HttpStatusCode.Unauthorized, issueResp.StatusCode);

            using var verifyResp = await anonymous.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = "SMG1.sample" });
            Assert.Equal(HttpStatusCode.Unauthorized, verifyResp.StatusCode);
        });
    }

    [MongoFact]
    public async Task QrReissueRotatesAndInvalidatesPreviousReference()
    {
        // Re-requesting QR for an Approved reservation rotates the hash; previous QR becomes invalid.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");
            using var op = f.Client("OP");

            // Issue QR 1
            using var resp1 = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload1 = (await Body(resp1)).GetProperty("qrPayload").GetString()!;

            // Issue QR 2 (rotation)
            using var resp2 = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload2 = (await Body(resp2)).GetProperty("qrPayload").GetString()!;

            Assert.NotEqual(payload1, payload2);

            // Verify QR 1 is now rejected (404 NotFound)
            using var verify1 = await op.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = payload1 });
            Assert.Equal(HttpStatusCode.NotFound, verify1.StatusCode);

            // Verify QR 2 succeeds (200 OK)
            using var verify2 = await op.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = payload2 });
            Assert.Equal(HttpStatusCode.OK, verify2.StatusCode);
        });
    }

    [MongoFact]
    public async Task GridOperatorCanVerifyValidApprovedQrAndReceivesTrustedServerData()
    {
        // GridOperator verifying a valid QR receives authoritative server data without altering the Approved status.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");
            using var op = f.Client("OP");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            using var verifyResp = await op.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.OK, verifyResp.StatusCode);

            var json = await Body(verifyResp);
            Assert.Equal(resId, json.GetProperty("reservationId").GetString());
            Assert.Equal("P1", json.GetProperty("prosumerNic").GetString());
            Assert.Equal(f.Station.StationId, json.GetProperty("stationId").GetString());
            Assert.Equal(f.Slot.SlotId, json.GetProperty("slotId").GetString());
            Assert.Equal("Approved", json.GetProperty("status").GetString());
            Assert.True(json.GetProperty("eligibleForCompletion").GetBoolean());
            Assert.False(json.TryGetProperty("qrTokenHash", out _));

            // Server-side status is STILL Approved — transaction completion is NOT executed.
            var doc = await f.Reservations.Find(x => x.ReservationId == resId).FirstAsync();
            Assert.Equal(ReservationStatus.Approved, doc.Status);
        });
    }

    [MongoFact]
    public async Task ProsumerCannotCallVerificationEndpoint()
    {
        // Prosumer calling the verification endpoint receives 403 Forbidden.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            using var verifyResp = await p1.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.Forbidden, verifyResp.StatusCode);
        });
    }

    [MongoFact]
    public async Task VerificationRejectsMalformedPayloadWithProblem400()
    {
        // Malformed payload returns 400 BadRequest.
        await WithApi(async f =>
        {
            using var op = f.Client("OP");

            using var resp = await op.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = "INVALID_PAYLOAD" });
            Assert.Equal(HttpStatusCode.BadRequest, resp.StatusCode);
        });
    }

    [MongoFact]
    public async Task VerificationRejectsNonExistentQrWithProblem404()
    {
        // Random/non-existent payload returns 404 NotFound.
        await WithApi(async f =>
        {
            using var op = f.Client("OP");

            using var resp = await op.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = "SMG1.non-existent-random-payload-token-12345678" });
            Assert.Equal(HttpStatusCode.NotFound, resp.StatusCode);
        });
    }

    [MongoFact]
    public async Task VerificationRejectsReservationIfStatusChangedAfterQrIssued()
    {
        // If reservation was cancelled or completed after QR was generated, verification returns 409 Conflict.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");
            using var op = f.Client("OP");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            // Status changes to Cancelled
            await f.Reservations.UpdateOneAsync(
                x => x.ReservationId == resId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Cancelled));

            using var verifyResp = await op.PostAsJsonAsync($"{Root}/qr/verify", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.Conflict, verifyResp.StatusCode);
        });
    }

    [MongoFact]
    public async Task MongoIndexExistsForQrTokenHash()
    {
        // Verifies the sparse unique index for QrTokenHash was created by the initializer.
        await WithApi(async f =>
        {
            using var cursor = await f.Reservations.Indexes.ListAsync();
            var indexes = await cursor.ToListAsync();
            var qrIndex = indexes.FirstOrDefault(x => x.GetElement("name").Value.AsString == "ux_reservations_qr_token_hash");
            Assert.NotNull(qrIndex);
            Assert.True(qrIndex.GetValue("unique").AsBoolean);
            Assert.True(qrIndex.Contains("partialFilterExpression"));
        });
    }

    [MongoFact]
    public async Task GridOperatorCanCompleteApprovedReservationAndPersistsCompletionMetadata()
    {
        // GridOperator completing a valid Approved QR changes status to Completed and records metadata.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");
            using var op = f.Client("OP");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            using var completeResp = await op.PostAsJsonAsync($"{Root}/qr/complete", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.OK, completeResp.StatusCode);

            var json = await Body(completeResp);
            Assert.Equal(resId, json.GetProperty("reservationId").GetString());
            Assert.Equal("Completed", json.GetProperty("status").GetString());
            Assert.Equal("OP", json.GetProperty("completedByOperatorNic").GetString());
            Assert.True(json.TryGetProperty("completedAtUtc", out _));

            // Verify database state in MongoDB
            var doc = await f.Reservations.Find(x => x.ReservationId == resId).FirstAsync();
            Assert.Equal(ReservationStatus.Completed, doc.Status);
            Assert.Equal("OP", doc.CompletedByOperatorNic);
            Assert.NotNull(doc.CompletedAtUtc);
        });
    }

    [MongoFact]
    public async Task DuplicateCompletionAttemptIsRejectedWithProblem409()
    {
        // Second completion of an already completed reservation returns 409 Conflict.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");
            using var op = f.Client("OP");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            // First completion succeeds
            using var resp1 = await op.PostAsJsonAsync($"{Root}/qr/complete", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.OK, resp1.StatusCode);

            // Second completion returns 409
            using var resp2 = await op.PostAsJsonAsync($"{Root}/qr/complete", new { qrPayload = payload, reservationId = resId });
            Assert.Equal(HttpStatusCode.Conflict, resp2.StatusCode);
        });
    }

    [MongoFact]
    public async Task ProsumerCannotCallCompletionEndpoint()
    {
        // Prosumer is forbidden from calling the complete endpoint.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            using var completeResp = await p1.PostAsJsonAsync($"{Root}/qr/complete", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.Forbidden, completeResp.StatusCode);
        });
    }

    [MongoFact]
    public async Task CompletionRejectsPendingReservationWithProblem409()
    {
        // Non-approved reservations cannot be completed.
        await WithApi(async f =>
        {
            var resId = await f.CreateApprovedReservation("P1");
            using var p1 = f.Client("P1");
            using var op = f.Client("OP");

            using var issueResp = await p1.PostAsync($"{Root}/{resId}/qr", null);
            var payload = (await Body(issueResp)).GetProperty("qrPayload").GetString()!;

            // Reset status to Pending in Mongo
            await f.Reservations.UpdateOneAsync(
                x => x.ReservationId == resId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Pending));

            using var completeResp = await op.PostAsJsonAsync($"{Root}/qr/complete", new { qrPayload = payload });
            Assert.Equal(HttpStatusCode.Conflict, completeResp.StatusCode);
        });
    }

    private static async Task<JsonElement> Body(HttpResponseMessage response)
    {
        using var stream = await response.Content.ReadAsStreamAsync();
        using var doc = await JsonDocument.ParseAsync(stream);
        return doc.RootElement.Clone();
    }

    private static async Task WithApi(Func<ApiFixture, Task> test)
    {
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var connection = Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")!;
        var factory = new ApiFactory(connection, name);
        try
        {
            var database = factory.Services.GetRequiredService<IMongoDatabase>();
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
            foreach (var pair in new[] { ("P1", UserRole.Prosumer), ("P2", UserRole.Prosumer), ("OP", UserRole.GridOperator), ("BO", UserRole.Backoffice) })
            {
                _accounts[pair.Item1] = new User
                {
                    Nic = pair.Item1,
                    FullName = "Test User",
                    Email = pair.Item1 + "@example.test",
                    Role = pair.Item2,
                    Status = UserStatus.Active
                };
            }
            await Users.InsertManyAsync(_accounts.Values);
            await Database.GetCollection<SolarStation>(CollectionNames.Stations).InsertOneAsync(Station);
            Slot = new EnergyBookingSlot
            {
                StationId = Station.StationId,
                StartAtUtc = Now.AddDays(2),
                EndAtUtc = Now.AddDays(2).AddHours(1),
                AvailableSlots = 5,
                TotalSlots = 5,
                IsActive = true
            };
            await Slots.InsertOneAsync(Slot);
        }

        public HttpClient Client(string? nic = null)
        {
            var client = factory.CreateClient(new WebApplicationFactoryClientOptions
            {
                BaseAddress = new Uri("https://localhost"),
                AllowAutoRedirect = false
            });
            client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            if (nic is not null)
            {
                var token = factory.Services.GetRequiredService<IJwtTokenService>().CreateToken(_accounts[nic]);
                client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token.AccessToken);
            }
            return client;
        }

        public async Task<string> CreateReservation(string nic)
        {
            using var client = Client(nic);
            using var response = await client.PostAsJsonAsync(Root, new { slotId = Slot.SlotId, energyAmountKwh = 10m });
            Assert.Equal(HttpStatusCode.Created, response.StatusCode);
            return (await Body(response)).GetProperty("reservationId").GetString()!;
        }

        public async Task<string> CreateApprovedReservation(string nic)
        {
            var id = await CreateReservation(nic);
            await Reservations.UpdateOneAsync(
                x => x.ReservationId == id,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Approved));
            return id;
        }
    }

    private sealed class FixedClock : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => new(Now);
    }
}
