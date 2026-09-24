/*
 * File: ReservationMongoTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies real standalone Mongo concurrency and reservation persistence contracts.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using MongoDB.Bson;
using MongoDB.Bson.Serialization;
using MongoDB.Driver;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Constants;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Persistence;
using SmartSolar.Infrastructure.Persistence.Repositories;
using Xunit;

namespace SmartSolar.IntegrationTests;

public sealed class ReservationMongoTests
{
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);

    [MongoFact]
    public async Task IndependentServicesCompetingForFinalPlaceCannotOverbook()
    {
        // Different Mongo clients emulate separate API instances competing for one capacity unit.
        await WithDatabase(async f =>
        {
            var first = f.Service();
            var second = f.Service();
            var outcomes = await Task.WhenAll(Attempt(() => first.CreateAsync("P1", f.Request())),
                Attempt(() => second.CreateAsync("P2", f.Request())));
            Assert.Single(outcomes, x => x);
            Assert.Equal(0, (await f.Repository.GetSlotAsync(f.Slot.SlotId))!.AvailableSlots);
            Assert.Equal(1, await f.Reservations.CountDocumentsAsync(FilterDefinition<EnergyReservation>.Empty));
            Assert.All(await f.Users.Find(FilterDefinition<User>.Empty).ToListAsync(), x => Assert.Null(x.ReservationWriteLock));
        });
    }

    [MongoFact]
    public async Task IndependentServicesCannotCreateOverlapsAcrossDifferentSlots()
    {
        // Per-Prosumer serialization is necessary even when both slots have free capacity.
        await WithDatabase(async f =>
        {
            var next = await f.AddSlot(f.Slot.StartAtUtc.AddMinutes(30));
            var outcomes = await Task.WhenAll(
                Attempt(() => f.Service().CreateAsync("P1", f.Request())),
                Attempt(() => f.Service().CreateAsync("P1", f.Request(next))));
            Assert.Single(outcomes, x => x);
            Assert.Single(await f.Repository.GetActiveAsync("P1"));
            var remaining = (await f.Repository.GetSlotAsync(f.Slot.SlotId))!.AvailableSlots
                + (await f.Repository.GetSlotAsync(next.SlotId))!.AvailableSlots;
            Assert.Equal(1, remaining);
        });
    }

    [MongoFact]
    public async Task ConcurrentCancellationsReleaseCapacityOnce()
    {
        // Both simultaneous attempts and a later retry must not double-release a place.
        await WithDatabase(async f =>
        {
            var created = await f.Service().CreateAsync("P1", f.Request());
            var outcomes = await Task.WhenAll(
                Attempt(() => f.Service().CancelAsync("P1", created.ReservationId)),
                Attempt(() => f.Service().CancelAsync("P1", created.ReservationId)));
            Assert.Single(outcomes, x => x);
            Assert.Equal(1, (await f.Repository.GetSlotAsync(f.Slot.SlotId))!.AvailableSlots);
            await Assert.ThrowsAsync<ConflictException>(() => f.Service().CancelAsync("P1", created.ReservationId));
            Assert.Equal(1, (await f.Repository.GetSlotAsync(f.Slot.SlotId))!.AvailableSlots);
        });
    }

    [MongoFact]
    public async Task MovePersistsSnapshotsAndTransfersCapacity()
    {
        // The real repository preserves the reviewed summary, accepted times and reapproval policy.
        await WithDatabase(async f =>
        {
            var created = await f.Service().CreateAsync("P1", f.Request());
            await f.Reservations.UpdateOneAsync(x => x.ReservationId == created.ReservationId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Approved).Set(x => x.QrToken, "stale"));
            var next = await f.AddSlot(Now.AddDays(3));
            var moved = await f.Service().UpdateAsync("P1", created.ReservationId,
                new UpdateReservationRequest { SlotId = next.SlotId, EnergyAmountKwh = 2 });
            Assert.Equal(ReservationStatus.Pending, moved.Status);
            Assert.Equal(next.StartAtUtc, moved.ScheduledStartAtUtc);
            Assert.Equal(1, (await f.Repository.GetSlotAsync(f.Slot.SlotId))!.AvailableSlots);
            Assert.Equal(0, (await f.Repository.GetSlotAsync(next.SlotId))!.AvailableSlots);
            Assert.Null((await f.Repository.GetAsync(created.ReservationId))!.QrToken);
            var raw = await f.Database.GetCollection<BsonDocument>(CollectionNames.Reservations)
                .Find(new BsonDocument("_id", created.ReservationId)).SingleAsync();
            Assert.Equal("Pending", raw["Status"].AsString);
            Assert.Equal(next.StartAtUtc, raw["ScheduledStartAtUtc"].ToUniversalTime());
        });
    }

    [MongoFact]
    public async Task ProfileWritesCannotEraseLockAndWrongOwnerCannotReleaseIt()
    {
        // Shared account lifecycle uses field updates so stale profiles cannot break reservation locking.
        await WithDatabase(async f =>
        {
            var users = new UserRepository(f.Database);
            var stale = (await users.GetByNicAsync("P1"))!;
            Assert.True(await f.Repository.TryLockAsync("P1", "owner"));
            Assert.False(await f.Repository.TryLockAsync("P1", "other"));
            stale.FullName = "Changed";
            await users.ReplaceAsync(stale);
            Assert.Equal("owner", (await users.GetByNicAsync("P1"))!.ReservationWriteLock);
            await Assert.ThrowsAsync<ConflictException>(() => f.Repository.UnlockAsync("P1", "other"));
            await f.Repository.UnlockAsync("P1", "owner");
            Assert.True(await f.Repository.TryLockAsync("P1", "next"));
            await f.Repository.UnlockAsync("P1", "next");
        });
    }

    [MongoFact]
    public async Task AtomicAcquisitionRejectsChangedOrInactiveSlotsAndReleaseCannotOverflow()
    {
        // Conditional filters must enforce the validated schedule and prevent capacity underflow/overflow.
        await WithDatabase(async f =>
        {
            await f.Slots.UpdateOneAsync(x => x.SlotId == f.Slot.SlotId,
                Builders<EnergyBookingSlot>.Update.Set(x => x.StartAtUtc, Now.AddDays(4)));
            Assert.False(await f.Repository.TryAcquireCapacityAsync(f.Slot));
            var current = (await f.Repository.GetSlotAsync(f.Slot.SlotId))!;
            await f.Slots.UpdateOneAsync(x => x.SlotId == f.Slot.SlotId,
                Builders<EnergyBookingSlot>.Update.Set(x => x.IsActive, false));
            Assert.False(await f.Repository.TryAcquireCapacityAsync(current));
            Assert.False(await f.Repository.ReleaseCapacityAsync(f.Slot.SlotId));
        });
    }

    [MongoFact]
    public async Task StaleStatusCannotOverwriteConcurrentCompletion()
    {
        // CAS protects a reservation modified by an independent operational workflow.
        await WithDatabase(async f =>
        {
            var created = await f.Service().CreateAsync("P1", f.Request());
            var stale = (await f.Repository.GetAsync(created.ReservationId))!;
            await f.Reservations.UpdateOneAsync(x => x.ReservationId == created.ReservationId,
                Builders<EnergyReservation>.Update.Set(x => x.Status, ReservationStatus.Completed));
            var replacement = BsonSerializer.Deserialize<EnergyReservation>(stale.ToBson());
            replacement.Status = ReservationStatus.Cancelled;
            Assert.False(await f.Repository.TryReplaceAsync(stale, replacement));
            Assert.Equal(ReservationStatus.Completed, (await f.Repository.GetAsync(created.ReservationId))!.Status);
        });
    }

    [MongoFact]
    public async Task RetainedLockRejectsRetryAcrossNewServiceInstances()
    {
        // A simulated crashed writer is never replaced merely because time or process changes.
        await WithDatabase(async f =>
        {
            Assert.True(await f.Repository.TryLockAsync("P1", "abandoned-operation"));
            await Assert.ThrowsAsync<ConflictException>(() => f.Service().CreateAsync("P1", f.Request()));
            Assert.Equal(1, (await f.Repository.GetSlotAsync(f.Slot.SlotId))!.AvailableSlots);
            Assert.Empty(await f.Repository.GetActiveAsync("P1"));
        });
    }

    [Fact]
    public void LegacyDocumentsDeserializeWithMissingSnapshotsAndMutex()
    {
        // Optional extensions preserve old BSON readability without fabricating accepted schedules.
        MongoMappings.Register();
        var reservation = BsonSerializer.Deserialize<EnergyReservation>(new BsonDocument
        {
            { "_id", "reservation" }, { "ProsumerNic", "P1" }, { "Status", "Pending" }
        });
        Assert.Null(reservation.ScheduledStartAtUtc);
        Assert.Null(reservation.ScheduledEndAtUtc);
        var user = BsonSerializer.Deserialize<User>(new BsonDocument { { "_id", "P1" }, { "Role", "Prosumer" }, { "Status", "Active" } });
        Assert.Null(user.ReservationWriteLock);
        var snapshot = Now.AddDays(2);
        reservation.ScheduledStartAtUtc = snapshot;
        reservation.ScheduledEndAtUtc = snapshot.AddHours(1);
        var roundTrip = BsonSerializer.Deserialize<EnergyReservation>(reservation.ToBson());
        Assert.Equal(snapshot, roundTrip.ScheduledStartAtUtc);
        Assert.Equal(DateTimeKind.Utc, roundTrip.ScheduledStartAtUtc!.Value.Kind);
    }

    private static async Task<bool> Attempt(Func<Task<ReservationResponse>> action)
    {
        // Only expected business conflicts count as losing contention; infrastructure errors fail the test.
        try { await action(); return true; }
        catch (ConflictException) { return false; }
    }

    private static async Task WithDatabase(Func<Fixture, Task> test)
    {
        // Every live test owns and removes only its unique isolated database.
        MongoMappings.Register();
        var settings = MongoClientSettings.FromConnectionString(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        settings.ServerSelectionTimeout = TimeSpan.FromSeconds(5);
        var client = new MongoClient(settings);
        var name = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var database = client.GetDatabase(name);
        try
        {
            var f = new Fixture(database, settings);
            await new MongoDbInitializer(database).InitializeAsync();
            await f.Users.InsertManyAsync(new[] { "P1", "P2" }.Select(nic => new User
            {
                Nic = nic, Email = nic + "@example.test", Role = UserRole.Prosumer, Status = UserStatus.Active
            }));
            await database.GetCollection<SolarStation>(CollectionNames.Stations).InsertOneAsync(f.Station);
            await f.Slots.InsertOneAsync(f.Slot);
            await test(f);
        }
        finally { await client.DropDatabaseAsync(name); }
    }

    private sealed class Fixture
    {
        private readonly MongoClientSettings _settings;
        public IMongoDatabase Database { get; }
        public ReservationRepository Repository { get; }
        public IMongoCollection<User> Users => Database.GetCollection<User>(CollectionNames.Users);
        public IMongoCollection<EnergyBookingSlot> Slots => Database.GetCollection<EnergyBookingSlot>(CollectionNames.BookingSlots);
        public IMongoCollection<EnergyReservation> Reservations => Database.GetCollection<EnergyReservation>(CollectionNames.Reservations);
        public SolarStation Station { get; } = new() { CapacityKwh = 100 };
        public EnergyBookingSlot Slot { get; }

        public Fixture(IMongoDatabase database, MongoClientSettings settings)
        {
            // Initialize shared data while each service below gets its own Mongo client/repository.
            Database = database;
            _settings = settings;
            Repository = new ReservationRepository(database);
            Slot = NewSlot(Now.AddDays(2));
        }

        public ReservationService Service()
        {
            // Independent clients rule out accidental in-process-only synchronization.
            var database = new MongoClient(_settings).GetDatabase(Database.DatabaseNamespace.DatabaseName);
            return new ReservationService(new ReservationRepository(database), new UserRepository(database), new FixedClock());
        }

        public CreateReservationRequest Request(EnergyBookingSlot? slot = null)
        {
            // Route only slot and energy input into the reviewed application boundary.
            return new CreateReservationRequest { SlotId = (slot ?? Slot).SlotId, EnergyAmountKwh = 1 };
        }

        public async Task<EnergyBookingSlot> AddSlot(DateTime start)
        {
            // Add a separate real Mongo slot for overlap or move contention.
            var slot = NewSlot(start);
            await Slots.InsertOneAsync(slot);
            return slot;
        }

        private EnergyBookingSlot NewSlot(DateTime start)
        {
            // Use a final-place slot to make overbooking directly observable.
            return new EnergyBookingSlot { StationId = Station.StationId, StartAtUtc = start,
                EndAtUtc = start.AddHours(1), TotalSlots = 1, AvailableSlots = 1 };
        }
    }

    private sealed class FixedClock : TimeProvider
    {
        public override DateTimeOffset GetUtcNow()
        {
            // Mongo service tests exercise exact deterministic UTC schedules.
            return new DateTimeOffset(Now);
        }
    }
}

