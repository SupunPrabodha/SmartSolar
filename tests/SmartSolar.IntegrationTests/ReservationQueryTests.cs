/*
 * File: ReservationQueryTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies Member 4 queries through real JWT authorization, HTTP and isolated MongoDB data.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Logging;
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Auth;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Reservations;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Persistence;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class ReservationQueryTests
{
    private const string P1 = "200012345678", P2 = "200112345678";
    private const string Root = "/api/v1/reservations/";
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);
    private static readonly string[] Routes = ["current", "pending", "history", "search", "dashboard-summary"];

    [MongoFact]
    public async Task CurrentIncludesOngoingAndFutureActiveOnlyAndScopesOwners()
    {
        // End-time membership retains ongoing bookings; terminal/final-boundary rows belong to history.
        await WithApi(async f =>
        {
            var ongoing = Row(1, ReservationStatus.Approved, -1, 1);
            var future = Row(2, ReservationStatus.Pending, 2, 3);
            var other = Row(3, ReservationStatus.Pending, 4, 5, P2);
            await f.Rows.InsertManyAsync(new[] { ongoing, future, other,
                Row(4, ReservationStatus.Pending, -2, 0), Row(5, ReservationStatus.Approved, -3, -2),
                Row(6, ReservationStatus.Cancelled, 1, 2), Row(7, ReservationStatus.Completed, 1, 2),
                Row(8, ReservationStatus.Rejected, 1, 2) });
            using var owner = f.Client(P1);
            Assert.Equal(new[] { ongoing.ReservationId, future.ReservationId }, await Ids(owner, "current"));
            using var op = f.Client("OP");
            Assert.Equal(new[] { ongoing.ReservationId, future.ReservationId, other.ReservationId }, await Ids(op, "current"));
            Assert.Equal(new[] { other.ReservationId }, await Ids(op, "current?prosumerNic=" + P2));
        });
    }

    [MongoFact]
    public async Task PendingIsExactStatusRegardlessOfTimeAndCannotLeakAnotherOwner()
    {
        // Pending is a status queue, so expired Pending records remain visible without changing their status.
        await WithApi(async f =>
        {
            var past = Row(1, ReservationStatus.Pending, -3, -2);
            var future = Row(2, ReservationStatus.Pending, 2, 3);
            await f.Rows.InsertManyAsync(new[] { future, past, Row(3, ReservationStatus.Pending, 1, 2, P2),
                Row(4, ReservationStatus.Approved, 1, 2), Row(5, ReservationStatus.Completed, 1, 2) });
            using var owner = f.Client(P1);
            Assert.Equal(new[] { past.ReservationId, future.ReservationId }, await Ids(owner, "pending"));
            Assert.Empty(await Ids(owner, "pending?status=Approved"));
        });
    }

    [MongoFact]
    public async Task HistoryContainsTerminalAndEndedActiveRowsInStableRecentOrder()
    {
        // Passing time changes read membership, never the persisted lifecycle or capacity.
        await WithApi(async f =>
        {
            var rows = new[] { Row(1, ReservationStatus.Cancelled, 5, 6), Row(2, ReservationStatus.Completed, 5, 6),
                Row(3, ReservationStatus.Rejected, 4, 5), Row(4, ReservationStatus.Pending, -1, 0),
                Row(5, ReservationStatus.Approved, -3, -2), Row(6, ReservationStatus.Pending, 2, 3),
                Row(7, ReservationStatus.Approved, -1, 1), Row(8, ReservationStatus.Completed, 6, 7, P2) };
            await f.Rows.InsertManyAsync(rows);
            using var owner = f.Client(P1);
            Assert.Equal(rows.Take(5).Select(x => x.ReservationId), await Ids(owner, "history"));
            var stored = await f.Rows.Find(x => x.ReservationId == rows[3].ReservationId).SingleAsync();
            Assert.Equal(ReservationStatus.Pending, stored.Status);
            Assert.Equal("must-not-leak", stored.QrToken);
        });
    }

    [MongoFact]
    public async Task SearchCombinesExactIdStatusStationNicAndInclusiveStartRange()
    {
        // Mongo filtering uses accepted snapshots and exact persisted GUID strings, not slot lookups or regexes.
        await WithApi(async f =>
        {
            var match = Row(1, ReservationStatus.Approved, 2, 3);
            var next = Row(2, ReservationStatus.Pending, 3, 4);
            next.ReservationId = Guid.Parse(next.ReservationId).ToString("D");
            await f.Rows.InsertManyAsync(new[] { match, next, Row(3, ReservationStatus.Approved, 1, 2, P2) });
            using var owner = f.Client(P1);
            Assert.Equal(new[] { match.ReservationId }, await Ids(owner, "search?reservationId=" + match.ReservationId));
            Assert.Equal(new[] { next.ReservationId }, await Ids(owner, "search?reservationId=" + next.ReservationId));
            Assert.Equal(new[] { match.ReservationId }, await Ids(owner, "search?status=approved&stationId=" + match.StationId
                + "&fromUtc=2030-01-01T02:00:00Z&toUtc=2030-01-01T02:00:00Z"));
            Assert.Empty(await Ids(owner, "search?fromUtc=2030-01-02T00:00:00Z"));
            Assert.Empty(await Ids(owner, "search?reservationId=" + Guid.NewGuid().ToString("N")));
            using var op = f.Client("OP");
            Assert.Equal(new[] { match.ReservationId }, await Ids(op, "search?prosumerNic=" + P1 + "&status=Approved"));
            Assert.Empty(await Ids(op, "search?stationId=" + Guid.NewGuid().ToString("N")));
        });
    }

    [MongoFact]
    public async Task SearchAndEveryOtherViewPreventProsumerIdentityOverride()
    {
        // Guessing an identifier or passing another NIC cannot broaden authenticated owner scope.
        await WithApi(async f =>
        {
            var other = Row(1, ReservationStatus.Pending, 1, 2, P2);
            await f.Rows.InsertOneAsync(other);
            using var owner = f.Client(P1);
            Assert.Empty(await Ids(owner, "search?reservationId=" + other.ReservationId));
            foreach (var route in Routes.Where(x => x != "dashboard-summary"))
            {
                using var denied = await owner.GetAsync(Root + route + "?prosumerNic=" + P2);
                await Problem(denied, HttpStatusCode.Forbidden);
            }
            var counts = await Get(owner, "dashboard-summary?prosumerNic=" + P2 + "&nowUtc=2000-01-01T00:00:00Z");
            Assert.Equal(0, counts.GetProperty("pendingReservations").GetInt64());
            Assert.Equal(Now, counts.GetProperty("generatedAtUtc").GetDateTime());
        });
    }

    [MongoFact]
    public async Task InvalidFiltersProduceProblem400()
    {
        // Exercise actual HTTP binding as well as custom validation, including offset-less dates and numeric enums.
        await WithApi(async f =>
        {
            using var op = f.Client("OP");
            foreach (var query in new[] { "status=Unknown", "status=999", "status=0", "status=Pending,Approved",
                "reservationId=bad", "reservationId=00000000-0000-0000-0000-000000000000", "stationId=bad",
                "prosumerNic=.*", "page=0", "page=-1", "page=10001", "page=abc", "pageSize=0", "pageSize=101",
                "fromUtc=bad", "fromUtc=2030-01-02T00:00:00Z&toUtc=2030-01-01T00:00:00Z", "fromUtc=2030-01-01T00:00:00" })
            {
                using var response = await op.GetAsync(Root + "search?" + query);
                await Problem(response, HttpStatusCode.BadRequest);
            }
        });
    }

    [MongoFact]
    public async Task PaginationIsBoundedStableAndReportsEmptyPages()
    {
        // Equal schedule times use reservation ID as the tie-breaker, with one extra row to detect another page.
        await WithApi(async f =>
        {
            var rows = Enumerable.Range(1, 22).Select(i => Row(i, ReservationStatus.Pending, 1, 2)).ToArray();
            await f.Rows.InsertManyAsync(rows.Reverse());
            using var owner = f.Client(P1);
            var first = await Get(owner, "pending");
            Assert.Equal(20, first.GetProperty("items").GetArrayLength());
            Assert.True(first.GetProperty("hasMore").GetBoolean());
            Assert.Equal(1, first.GetProperty("page").GetInt32());
            Assert.Equal(rows.Take(20).Select(x => x.ReservationId), Ids(first));
            var second = await Get(owner, "pending?page=2");
            Assert.Equal(rows.Skip(20).Select(x => x.ReservationId), Ids(second));
            Assert.False(second.GetProperty("hasMore").GetBoolean());
            Assert.Equal(20, second.GetProperty("pageSize").GetInt32());
            var empty = await Get(owner, "pending?page=3");
            Assert.Empty(Ids(empty));
            Assert.False(empty.GetProperty("hasMore").GetBoolean());
        });
    }

    [MongoFact]
    public async Task DashboardUsesExactStatusesStrictFutureBoundaryAndOwnerScope()
    {
        // Future Pending, past/current Approved, and terminal statuses cannot inflate Approved Future.
        await WithApi(async f =>
        {
            await f.Rows.InsertManyAsync(new[] { Row(1, ReservationStatus.Pending, 1, 2), Row(2, ReservationStatus.Pending, -3, -2),
                Row(3, ReservationStatus.Approved, 1, 2), Row(4, ReservationStatus.Approved, -3, -2),
                Row(5, ReservationStatus.Approved, 0, 1), Row(6, ReservationStatus.Cancelled, 1, 2),
                Row(7, ReservationStatus.Completed, 1, 2), Row(8, ReservationStatus.Rejected, 1, 2),
                Row(9, ReservationStatus.Pending, 1, 2, P2), Row(10, ReservationStatus.Approved, 1, 2, P2) });
            using var owner = f.Client(P1);
            var own = await Get(owner, "dashboard-summary");
            Assert.Equal(2, own.GetProperty("pendingReservations").GetInt64());
            Assert.Equal(1, own.GetProperty("approvedFutureReservations").GetInt64());
            Assert.Equal(Now, own.GetProperty("generatedAtUtc").GetDateTime());
            using var op = f.Client("OP");
            var all = await Get(op, "dashboard-summary");
            Assert.Equal(3, all.GetProperty("pendingReservations").GetInt64());
            Assert.Equal(2, all.GetProperty("approvedFutureReservations").GetInt64());
        });
    }

    [MongoFact]
    public async Task EmptyDatabaseReturnsEmptyPagesAndZeroCounts()
    {
        // Empty results are normal successful reads, never null DTOs or missing-resource errors.
        await WithApi(async f =>
        {
            using var owner = f.Client(P1);
            foreach (var route in Routes.Where(x => x != "dashboard-summary")) Assert.Empty(await Ids(owner, route));
            var counts = await Get(owner, "dashboard-summary");
            Assert.Equal(0, counts.GetProperty("pendingReservations").GetInt64());
            Assert.Equal(0, counts.GetProperty("approvedFutureReservations").GetInt64());
        });
    }

    [MongoFact]
    public async Task EveryRouteRequiresAuthenticationAndRejectsBackofficeAndRevokedAccounts()
    {
        // Use real JWT validation rather than mocked role headers, including stored account revocation.
        await WithApi(async f =>
        {
            using var anonymous = f.Client();
            using var backoffice = f.Client("BO");
            using var revoked = f.Client(P1);
            await f.Database.GetCollection<User>(CollectionNames.Users).UpdateOneAsync(x => x.Nic == P1,
                Builders<User>.Update.Set(x => x.Status, UserStatus.Deactivated));
            foreach (var route in Routes)
            {
                using var missing = await anonymous.GetAsync(Root + route);
                await Problem(missing, HttpStatusCode.Unauthorized);
                using var wrongRole = await backoffice.GetAsync(Root + route);
                await Problem(wrongRole, HttpStatusCode.Forbidden);
                using var inactive = await revoked.GetAsync(Root + route);
                await Problem(inactive, HttpStatusCode.Unauthorized);
            }
        });
    }

    [MongoFact]
    public async Task LegacySchedulesFailClosedBeforeTimeFilteringButStayOwnerScoped()
    {
        // Missing accepted times cannot silently disappear due to date bounds, pagination or future-count predicates.
        await WithApi(async f =>
        {
            var legacy = Row(1, ReservationStatus.Approved, 1, 2);
            legacy.ScheduledStartAtUtc = null;
            await f.Rows.InsertOneAsync(legacy);
            using var owner = f.Client(P1);
            foreach (var route in Routes.Where(x => x != "pending"))
            {
                using var response = await owner.GetAsync(Root + route + "?fromUtc=2099-01-01T00:00:00Z&page=2");
                await Problem(response, HttpStatusCode.Conflict);
            }
            using var other = f.Client(P2);
            foreach (var route in Routes.Where(x => x != "dashboard-summary")) Assert.Empty(await Ids(other, route));
            Assert.Equal(0, (await Get(other, "dashboard-summary")).GetProperty("approvedFutureReservations").GetInt64());
            await f.Rows.UpdateOneAsync(x => x.ReservationId == legacy.ReservationId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Pending));
            using var pending = await owner.GetAsync(Root + "pending");
            await Problem(pending, HttpStatusCode.Conflict);
            Assert.Equal(1, (await Get(owner, "dashboard-summary")).GetProperty("pendingReservations").GetInt64());
        });
    }

    [MongoFact]
    public async Task ReversedAndMissingEndSnapshotsRequireRepair()
    {
        // Validate both snapshots, including missing BSON fields rather than only explicitly stored nulls.
        await WithApi(async f =>
        {
            var bad = Row(1, ReservationStatus.Approved, 2, 1);
            await f.Rows.InsertOneAsync(bad);
            using var owner = f.Client(P1);
            foreach (var route in new[] { "search", "current", "dashboard-summary" })
            {
                using var response = await owner.GetAsync(Root + route);
                await Problem(response, HttpStatusCode.Conflict);
            }
            await f.Rows.UpdateOneAsync(x => x.ReservationId == bad.ReservationId,
                Builders<EnergyReservation>.Update.Unset(x => x.ScheduledEndAtUtc));
            using var missing = await owner.GetAsync(Root + "dashboard-summary");
            await Problem(missing, HttpStatusCode.Conflict);
        });
    }

    [MongoFact]
    public async Task DirectServiceCallsEnforceRolesValidationAndSingleClockRead()
    {
        // MVC is not the only trust boundary: application callers must pass the same checks.
        await WithApi(async f =>
        {
            using var scope = f.Factory.Services.CreateScope();
            var service = scope.ServiceProvider.GetRequiredService<IReservationQueryService>();
            foreach (var route in Enum.GetValues<ReservationReadView>())
            {
                await Assert.ThrowsAsync<UnauthorizedException>(() => service.QueryAsync("", route, new()));
                await Assert.ThrowsAsync<ForbiddenException>(() => service.QueryAsync("BO", route, new()));
                await Assert.ThrowsAsync<ForbiddenException>(() => service.QueryAsync(P1, route, new() { ProsumerNic = P2 }));
                await Assert.ThrowsAsync<BadRequestException>(() => service.QueryAsync(P1, route, new() { PageSize = 101 }));
            }
            await Assert.ThrowsAsync<UnauthorizedException>(() => service.DashboardAsync("missing"));
            await Assert.ThrowsAsync<ForbiddenException>(() => service.DashboardAsync("BO"));
            f.Factory.Clock.Reads = 0;
            await service.QueryAsync(P1, ReservationReadView.Current, new());
            Assert.Equal(1, f.Factory.Clock.Reads);
            f.Factory.Clock.Reads = 0;
            await service.DashboardAsync(P1);
            Assert.Equal(1, f.Factory.Clock.Reads);
            using var cancelled = new CancellationTokenSource();
            cancelled.Cancel();
            await Assert.ThrowsAnyAsync<OperationCanceledException>(() => service.QueryAsync(P1, ReservationReadView.Search, new(), cancelled.Token));
        });
    }

    [MongoFact]
    public async Task IndexInitializationIsRepeatableAndKeepsFourCollections()
    {
        // Confirm additive index definitions and unchanged collection names against a real Mongo server.
        await WithApi(async f =>
        {
            await new MongoDbInitializer(f.Database).InitializeAsync();
            var indexes = await (await f.Rows.Indexes.ListAsync()).ToListAsync();
            Assert.Equal(new BsonDocument { { "Status", 1 }, { "ScheduledStartAtUtc", 1 } },
                Assert.Single(indexes, x => x["name"] == "ix_reservations_status_start")["key"].AsBsonDocument);
            Assert.Equal(new BsonDocument { { "ProsumerNic", 1 }, { "Status", 1 }, { "ScheduledStartAtUtc", 1 } },
                Assert.Single(indexes, x => x["name"] == "ix_reservations_prosumer_status_start")["key"].AsBsonDocument);
            Assert.Contains(indexes, x => x["name"] == "ix_reservations_prosumer_created");
            Assert.Contains(indexes, x => x["name"] == "ix_reservations_station_status");
            Assert.Equal(new[] { "EnergyBookingSlots", "EnergyReservation", "SolarStationInfo", "UsersDetail" },
                (await (await f.Database.ListCollectionNamesAsync()).ToListAsync()).OrderBy(x => x).ToArray());
        });
    }

    private static EnergyReservation Row(int id, ReservationStatus status, int startHours, int endHours, string nic = P1)
    {
        // Use realistic stable GUIDs and UTC snapshots independently of out-of-scope write workflows.
        return new EnergyReservation { ReservationId = id.ToString("x32"), ProsumerNic = nic,
            StationId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", SlotId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            EnergyAmountKwh = 1.5m, Status = status, ScheduledStartAtUtc = Now.AddHours(startHours),
            ScheduledEndAtUtc = Now.AddHours(endHours), CreatedAtUtc = Now.AddDays(-5), UpdatedAtUtc = Now.AddDays(-4), QrToken = "must-not-leak" };
    }

    private static async Task<string[]> Ids(HttpClient client, string route)
    {
        // Assert DTO safety whenever list membership is checked.
        return Ids(await Get(client, route));
    }

    private static string[] Ids(JsonElement page)
    {
        // Reuse the existing summary shape while excluding internal credentials and Mongo document names.
        var items = page.GetProperty("items").EnumerateArray().ToArray();
        Assert.All(items, row => { Assert.False(row.TryGetProperty("qrToken", out _)); Assert.False(row.TryGetProperty("_id", out _)); });
        return items.Select(x => x.GetProperty("reservationId").GetString()!).ToArray();
    }

    private static async Task<JsonElement> Get(HttpClient client, string route)
    {
        // Parse successful endpoint responses without retaining disposed JSON documents.
        using var response = await client.GetAsync(Root + route);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        return json.RootElement.Clone();
    }

    private static async Task Problem(HttpResponseMessage response, HttpStatusCode expected)
    {
        // Match the existing middleware and MVC validation ProblemDetails contracts.
        Assert.Equal(expected, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal((int)expected, json.RootElement.GetProperty("status").GetInt32());
        Assert.True(json.RootElement.TryGetProperty("traceId", out _));
    }

    private static async Task WithApi(Func<Fixture, Task> test)
    {
        // Own only a uniquely named disposable test database; never touch development accounts or reservations.
        var connection = Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO")!;
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var factory = new Factory(connection, name);
        try
        {
            var database = factory.Services.GetRequiredService<IMongoDatabase>();
            Assert.Equal(name, database.DatabaseNamespace.DatabaseName);
            var fixture = new Fixture(factory, database);
            await database.GetCollection<User>(CollectionNames.Users).InsertManyAsync(fixture.Accounts.Values);
            await test(fixture);
        }
        finally
        {
            await factory.DisposeAsync();
            await new MongoClient(connection).DropDatabaseAsync(name);
        }
    }

    private sealed class Factory(string connection, string name) : WebApplicationFactory<Program>
    {
        public FixedClock Clock { get; } = new();
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            // Keep real JWT and repository behavior; substitute only isolated configuration and business time.
            builder.UseEnvironment("Testing");
            builder.UseSetting("MongoDb:ConnectionString", connection);
            builder.UseSetting("MongoDb:DatabaseName", name);
            builder.UseSetting("Jwt:Key", Convert.ToHexString(RandomNumberGenerator.GetBytes(32)));
            builder.UseSetting("Jwt:Issuer", "SmartSolar.Tests");
            builder.UseSetting("Jwt:Audience", "SmartSolar.Tests");
            builder.UseSetting("Jwt:ExpiryMinutes", "60");
            builder.ConfigureLogging(logging => logging.ClearProviders());
            builder.ConfigureServices(services => { services.RemoveAll<TimeProvider>(); services.AddSingleton<TimeProvider>(Clock); });
        }
    }

    private sealed class Fixture(Factory factory, IMongoDatabase database)
    {
        public Factory Factory { get; } = factory;
        public IMongoDatabase Database { get; } = database;
        public IMongoCollection<EnergyReservation> Rows => Database.GetCollection<EnergyReservation>(CollectionNames.Reservations);
        public Dictionary<string, User> Accounts { get; } = new[] { (P1, UserRole.Prosumer), (P2, UserRole.Prosumer),
            ("OP", UserRole.GridOperator), ("BO", UserRole.Backoffice) }.ToDictionary(x => x.Item1,
                x => new User { Nic = x.Item1, Email = x.Item1 + "@example.test", Role = x.Item2, Status = UserStatus.Active });

        public HttpClient Client(string? nic = null)
        {
            // Issue signed tokens through the existing service rather than bypassing authentication middleware.
            var client = Factory.CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("https://localhost"), AllowAutoRedirect = false });
            client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            if (nic is not null)
                client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer",
                    Factory.Services.GetRequiredService<IJwtTokenService>().CreateToken(Accounts[nic]).AccessToken);
            return client;
        }
    }

    private sealed class FixedClock : TimeProvider
    {
        public int Reads { get; set; }
        public override DateTimeOffset GetUtcNow()
        {
            // Observe the single comparison instant while leaving token lifetime checks on real UTC time.
            Reads++;
            return new DateTimeOffset(Now);
        }
    }
}
