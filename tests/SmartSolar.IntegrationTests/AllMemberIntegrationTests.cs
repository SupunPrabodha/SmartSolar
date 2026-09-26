/*
 * File: AllMemberIntegrationTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Verifies catalog protection against real lifecycle/QR writes and shared booking summaries.
 */
using MongoDB.Bson;
using MongoDB.Driver;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.DTOs.Stations;
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

public sealed class AllMemberIntegrationTests
{
    [MongoFact]
    public async Task LifecycleAndCompletionFeedTheSameCatalogProtectionAndHistory()
    {
        // Exercise each real transition against isolated catalog data; no direct status fixture updates.
        MongoMappings.Register();
        var mongo = new MongoClient(Environment.GetEnvironmentVariable("SMARTSOLAR_TEST_MONGO"));
        var databaseName = "SmartSolarTests_" + Guid.NewGuid().ToString("N");
        var db = mongo.GetDatabase(databaseName);
        try
        {
            var catalog = new StationCatalogRepository(db);
            var gate = new CatalogWriteGate();
            var stations = new StationService(catalog, catalog, gate);
            var slots = new SlotService(catalog, catalog, gate);
            var users = new UserRepository(db);
            var reservations = new ReservationRepository(db);
            var clock = new FixedClock();
            var lifecycle = new ReservationService(reservations, users, new QrSecurityService(), clock, gate);
            var queries = new ReservationQueryService(new ReservationReadRepository(db), users, clock);
            const string nic = "199000000002";
            const string operatorNic = "199000000003";
            await users.InsertAsync(new User { Nic = nic, Role = UserRole.Prosumer, Status = UserStatus.Active });
            await users.InsertAsync(new User { Nic = operatorNic, Role = UserRole.GridOperator, Status = UserStatus.Active });
            var index = 0;
            foreach (var target in Enum.GetValues<ReservationStatus>())
            {
                var station = await stations.CreateAsync(new() {
                    Name = "Integration station", Address = "Test road", Latitude = 6.9, Longitude = 79.8,
                    CapacityKwh = 50, TotalBatterySlots = 4,
                    OperatingSchedule = Enumerable.Range(1, 7).Select(day => new OperatingDayDto(day, false, "00:00", "24:00")).ToList()
                });
                var start = clock.GetUtcNow().AddDays(1).AddHours(index++ * 2);
                var slot = await slots.CreateAsync(station.StationId, new() {
                    StartAtUtc = start, EndAtUtc = start.AddHours(1), TotalSlots = 4, AvailableSlots = 4
                });
                var created = await lifecycle.CreateAsync(nic, new() { SlotId = slot.SlotId, EnergyAmountKwh = 2 });
                Assert.Equal(3, (await reservations.GetSlotAsync(slot.SlotId))!.AvailableSlots);
                var id = created.ReservationId;
                if (target is ReservationStatus.Approved or ReservationStatus.Completed)
                    await lifecycle.ApproveAsync(operatorNic, id);
                if (target == ReservationStatus.Rejected)
                    await lifecycle.RejectAsync(operatorNic, id, new() { Remark = "Integration rejection" });
                if (target == ReservationStatus.Cancelled)
                    await lifecycle.CancelAsync(nic, id);
                if (target == ReservationStatus.Completed)
                {
                    var qr = await lifecycle.IssueQrAsync(nic, id);
                    clock.Current = start;
                    var verified = await lifecycle.VerifyQrAsync(operatorNic, new() { QrPayload = qr.QrPayload });
                    Assert.Equal(id, verified.ReservationId);
                    await lifecycle.CompleteTransferAsync(operatorNic, new(qr.QrPayload, id));
                    await Assert.ThrowsAsync<ConflictException>(() => lifecycle.CompleteTransferAsync(operatorNic,
                        new(qr.QrPayload, id)));
                }
                clock.Current = new DateTimeOffset(2030, 1, 1, 0, 0, 0, TimeSpan.Zero);
                slot = await slots.GetAsync(slot.SlotId, true);
                if (target == ReservationStatus.Completed) Assert.Equal(3, slot.AvailableSlots);
                if (target is ReservationStatus.Cancelled or ReservationStatus.Rejected)
                    Assert.Equal(4, (await reservations.GetSlotAsync(slot.SlotId))!.AvailableSlots);

                var rawCollection = db.GetCollection<BsonDocument>(CollectionNames.Reservations);
                var beforeCatalog = await rawCollection.Find(new BsonDocument("_id", id)).SingleAsync();
                Assert.Equal(target.ToString(), beforeCatalog["Status"].AsString);
                Assert.Equal(station.StationId, beforeCatalog["StationId"].AsString);
                Assert.Equal(slot.SlotId, beforeCatalog["SlotId"].AsString);
                Assert.Equal(nic, beforeCatalog["ProsumerNic"].AsString);
                var blocks = target is ReservationStatus.Pending or ReservationStatus.Approved;
                Assert.Equal(blocks, await catalog.HasActiveStationReservationsAsync(station.StationId, default));
                Assert.Equal(blocks, await catalog.HasActiveSlotReservationsAsync(slot.SlotId, default));
                if (blocks)
                {
                    await Assert.ThrowsAsync<ConflictException>(() => stations.DeactivateAsync(station.StationId,
                        new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc }));
                    await Assert.ThrowsAsync<ConflictException>(() => slots.AvailabilityAsync(slot.SlotId,
                        new() { AvailableSlots = 2, ExpectedUpdatedAtUtc = slot.UpdatedAtUtc }));
                    await Assert.ThrowsAsync<ConflictException>(() => slots.DeactivateAsync(slot.SlotId,
                        new() { ExpectedUpdatedAtUtc = slot.UpdatedAtUtc }));
                }
                else
                {
                    var history = await queries.QueryAsync(nic, ReservationReadView.History, new() { ReservationId = id });
                    var row = Assert.Single(history.Items);
                    Assert.Equal(target, row.Status);
                    Assert.Equal(start.UtcDateTime, row.ScheduledStartAtUtc);
                    if (target == ReservationStatus.Rejected) Assert.Equal("Integration rejection", row.RejectionRemark);
                    var changed = await slots.AvailabilityAsync(slot.SlotId,
                        new() { AvailableSlots = 2, ExpectedUpdatedAtUtc = slot.UpdatedAtUtc });
                    await slots.DeactivateAsync(slot.SlotId, new() { ExpectedUpdatedAtUtc = changed.UpdatedAtUtc });
                    await stations.DeactivateAsync(station.StationId, new() { ExpectedUpdatedAtUtc = station.UpdatedAtUtc });
                }
                Assert.Equal(beforeCatalog, await rawCollection.Find(new BsonDocument("_id", id)).SingleAsync());
                Assert.Equal(station.StationId, (await reservations.GetSlotAsync(slot.SlotId))!.StationId);
            }
            var summary = await queries.DashboardAsync(nic);
            Assert.Equal(1, summary.PendingReservations);
            Assert.Equal(1, summary.ApprovedFutureReservations);
        }
        finally { await mongo.DropDatabaseAsync(databaseName); }
    }

    private sealed class FixedClock : TimeProvider
    {
        public DateTimeOffset Current { get; set; } = new(2030, 1, 1, 0, 0, 0, TimeSpan.Zero);
        public override DateTimeOffset GetUtcNow()
        {
            // Fixed UTC time keeps every created slot within the documented booking/change window.
            return Current;
        }
    }
}
