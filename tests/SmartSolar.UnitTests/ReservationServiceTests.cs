/*
 * File: ReservationServiceTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Exercises authoritative lifecycle, ownership, capacity and recovery behavior.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using System.Text.Json;
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using Xunit;

namespace SmartSolar.UnitTests;

public sealed class ReservationServiceTests
{
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);

    [Fact]
    public async Task CreateDerivesIdentityScheduleAndPendingStatus()
    {
        // Creation uses the stored slot and authenticated NIC, consuming one place.
        var f = new Fixture();
        var result = await f.Create();
        Assert.Equal("P1", result.ProsumerNic);
        Assert.Equal(f.Slot.StationId, result.StationId);
        Assert.Equal(f.Slot.StartAtUtc, result.ScheduledStartAtUtc);
        Assert.Equal(ReservationStatus.Pending, result.Status);
        Assert.Equal(Now, result.CreatedAtUtc);
        Assert.Equal(1, f.Store.Slots[f.Slot.SlotId].AvailableSlots);
        Assert.Empty(f.Store.Locks);
        Assert.Equal(result, await f.Service.GetAsync("P1", result.ReservationId));
    }

    [Theory]
    [InlineData(604800, true)]
    [InlineData(604801, false)]
    [InlineData(0, false)]
    [InlineData(-1, false)]
    public async Task ServiceEnforcesCreateHorizon(int seconds, bool allowed)
    {
        // Exercise the actual service so timing policy cannot be accidentally omitted.
        var f = new Fixture();
        f.Slot.StartAtUtc = Now.AddSeconds(seconds);
        f.Slot.EndAtUtc = f.Slot.StartAtUtc.AddHours(1);
        if (allowed) await f.Create();
        else await Assert.ThrowsAsync<BadRequestException>(() => f.Create());
        Assert.Equal(allowed ? 1 : 2, f.Slot.AvailableSlots);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public async Task ServiceRejectsNonpositiveEnergy(int amount)
    {
        // Application validation also protects non-MVC callers.
        var f = new Fixture();
        await Assert.ThrowsAsync<BadRequestException>(() => f.Create(amount: amount));
        Assert.Empty(f.Store.Reservations);
        Assert.Equal(2, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task CreateRejectsEnergyExceedingStationCapacity()
    {
        // Requesting more energy than station's total CapacityKwh fails fast.
        var f = new Fixture();
        f.Station.CapacityKwh = 50;
        var ex = await Assert.ThrowsAsync<BadRequestException>(() => f.Create(amount: 51));
        Assert.Contains("exceeds station capacity", ex.Message);
        Assert.Empty(f.Store.Reservations);
        Assert.Equal(2, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task CreateRejectsCumulativeEnergyExceedingStationCapacity()
    {
        // Multiple prosumers on the same slot cannot exceed the station's CapacityKwh.
        var f = new Fixture();
        f.Station.CapacityKwh = 100;
        await f.Create(nic: "P1", amount: 60);
        Assert.Equal(1, f.Slot.AvailableSlots);

        var ex = await Assert.ThrowsAsync<ConflictException>(() => f.Create(nic: "P2", amount: 50));
        Assert.Contains("exceeds the station's available energy capacity", ex.Message);
        Assert.Equal(1, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task UpdateRejectsEnergyExceedingStationCapacity()
    {
        var f = new Fixture();
        f.Station.CapacityKwh = 100;
        var created = await f.Create(nic: "P1", amount: 50);

        var ex = await Assert.ThrowsAsync<BadRequestException>(() =>
            f.Service.UpdateAsync("P1", created.ReservationId, new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 150 }));
        Assert.Contains("exceeds station capacity", ex.Message);
    }

    [Fact]
    public async Task UpdateRejectsCumulativeEnergyExceedingStationCapacity()
    {
        var f = new Fixture();
        f.Station.CapacityKwh = 100;
        var r1 = await f.Create(nic: "P1", amount: 50);
        var r2 = await f.Create(nic: "P2", amount: 40);

        // P1 tries to increase from 50 to 70 (allocated = 40 (from P2), requested = 70 -> total 110 > 100)
        var ex = await Assert.ThrowsAsync<ConflictException>(() =>
            f.Service.UpdateAsync("P1", r1.ReservationId, new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 70 }));
        Assert.Contains("exceeds the station's available energy capacity", ex.Message);
    }

    [Fact]
    public async Task UpdateAllowsAdjustingEnergyWithinAvailableStationCapacity()
    {
        var f = new Fixture();
        f.Station.CapacityKwh = 100;
        var r1 = await f.Create(nic: "P1", amount: 50);
        var r2 = await f.Create(nic: "P2", amount: 40);

        // P1 adjusts from 50 to 55 (allocated = 40 (from P2), requested = 55 -> total 95 <= 100)
        var updated = await f.Service.UpdateAsync("P1", r1.ReservationId, new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 55 });
        Assert.Equal(55, updated.EnergyAmountKwh);
    }

    [Theory]
    [InlineData("missing-slot")]
    [InlineData("missing-station")]
    [InlineData("inactive-slot")]
    [InlineData("inactive-station")]
    [InlineData("full")]
    [InlineData("invalid-capacity")]
    public async Task InvalidStationSlotOrCapacityCannotCreate(string scenario)
    {
        // All prerequisite failures leave both reservations and availability untouched.
        var f = new Fixture();
        if (scenario == "missing-slot") f.Store.Slots.Clear();
        if (scenario == "missing-station") f.Store.Stations.Clear();
        if (scenario == "inactive-slot") f.Slot.IsActive = false;
        if (scenario == "inactive-station") f.Station.IsActive = false;
        if (scenario == "full") f.Slot.AvailableSlots = 0;
        if (scenario == "invalid-capacity") f.Slot.AvailableSlots = 3;
        var expected = scenario.StartsWith("missing") ? typeof(NotFoundException) : typeof(ConflictException);
        await Assert.ThrowsAsync(expected, () => f.Create());
        Assert.Empty(f.Store.Reservations);
        Assert.Empty(f.Store.Locks);
    }

    [Theory]
    [InlineData(ReservationStatus.Pending)]
    [InlineData(ReservationStatus.Approved)]
    public async Task SameProsumerOverlapRejectsButDifferentProsumerCanBook(ReservationStatus status)
    {
        // Active overlap is scoped to a Prosumer, not a global ban on sharing a slot.
        var f = new Fixture();
        var first = await f.Create();
        f.Store.Reservations[first.ReservationId].Status = status;
        await Assert.ThrowsAsync<ConflictException>(() => f.Create());
        await f.Create("P2");
        Assert.Equal(0, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task AdjacentSlotAndSelfExcludedUpdateSucceed()
    {
        // Touching endpoints are allowed and updates exclude their own reservation.
        var f = new Fixture();
        var first = await f.Create();
        var next = f.AddSlot(f.Slot.EndAtUtc);
        await f.Create(slot: next);
        var updated = await f.Service.UpdateAsync("P1", first.ReservationId,
            new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 2 });
        Assert.Equal(2, updated.EnergyAmountKwh);
        Assert.Equal(1, f.Slot.AvailableSlots);
    }

    [Theory]
    [InlineData(43200, true)]
    [InlineData(43199, false)]
    public async Task UpdateEnforcesOriginalNoticeAndReapproval(int seconds, bool allowed)
    {
        // An Approved modification clears stale QR data and returns Pending only when permitted.
        var f = new Fixture();
        f.Slot.StartAtUtc = Now.AddSeconds(seconds);
        f.Slot.EndAtUtc = f.Slot.StartAtUtc.AddHours(1);
        var created = await f.Create();
        var stored = f.Store.Reservations[created.ReservationId];
        stored.Status = ReservationStatus.Approved;
        stored.QrToken = "stale";
        var request = new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 2 };
        if (allowed)
        {
            var result = await f.Service.UpdateAsync("P1", created.ReservationId, request);
            Assert.Equal(ReservationStatus.Pending, result.Status);
            Assert.Null(f.Store.Reservations[created.ReservationId].QrToken);
        }
        else await Assert.ThrowsAsync<ConflictException>(() => f.Service.UpdateAsync("P1", created.ReservationId, request));
        Assert.Equal(1, f.Slot.AvailableSlots);
    }

    [Theory]
    [InlineData(43200, true)]
    [InlineData(43199, false)]
    public async Task CancelEnforcesNoticeForOperatorAndReleasesOnlyOnce(int seconds, bool allowed)
    {
        // Assistance has no timing bypass, and retries cannot release a second capacity unit.
        var f = new Fixture();
        f.Slot.StartAtUtc = Now.AddSeconds(seconds);
        f.Slot.EndAtUtc = f.Slot.StartAtUtc.AddHours(1);
        var created = await f.Create();
        if (allowed)
        {
            var result = await f.Service.CancelAsync("OP", created.ReservationId);
            Assert.Equal(ReservationStatus.Cancelled, result.Status);
            Assert.Equal(2, f.Slot.AvailableSlots);
            await Assert.ThrowsAsync<ConflictException>(() => f.Service.CancelAsync("OP", created.ReservationId));
            Assert.Equal(2, f.Slot.AvailableSlots);
        }
        else
        {
            await Assert.ThrowsAsync<ConflictException>(() => f.Service.CancelAsync("OP", created.ReservationId));
            Assert.Equal(1, f.Slot.AvailableSlots);
        }
    }

    [Fact]
    public async Task GridOperatorCanApprovePendingReservation()
    {
        // Approval sets status to Approved and retains allocated slot capacity.
        var f = new Fixture();
        var created = await f.Create();
        Assert.Equal(1, f.Slot.AvailableSlots);

        var approved = await f.Service.ApproveAsync("OP", created.ReservationId);
        Assert.Equal(ReservationStatus.Approved, approved.Status);
        Assert.Equal(1, f.Slot.AvailableSlots);
        Assert.Empty(f.Store.Locks);
        Assert.Equal(ReservationStatus.Approved, f.Store.Reservations[created.ReservationId].Status);
    }

    [Fact]
    public async Task ApproveRejectsNonOperatorOrNonPending()
    {
        // Only GridOperator can approve, and only when Pending.
        var f = new Fixture();
        var created = await f.Create();

        // Prosumer and Backoffice cannot approve
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.ApproveAsync("P1", created.ReservationId));
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.ApproveAsync("BO", created.ReservationId));

        // Operator approves
        await f.Service.ApproveAsync("OP", created.ReservationId);

        // Cannot approve again
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.ApproveAsync("OP", created.ReservationId));
    }

    [Fact]
    public async Task GridOperatorCanRejectPendingReservationWithRemarkAndReleasesCapacity()
    {
        // Rejection sets status to Rejected, stores RejectionRemark, and releases slot capacity.
        var f = new Fixture();
        var created = await f.Create();
        Assert.Equal(1, f.Slot.AvailableSlots);

        var request = new RejectReservationRequest { Remark = "Grid maintenance scheduled during this slot." };
        var rejected = await f.Service.RejectAsync("OP", created.ReservationId, request);

        Assert.Equal(ReservationStatus.Rejected, rejected.Status);
        Assert.Equal("Grid maintenance scheduled during this slot.", rejected.RejectionRemark);
        Assert.Equal(2, f.Slot.AvailableSlots);
        Assert.Empty(f.Store.Locks);
        Assert.Equal("Grid maintenance scheduled during this slot.", f.Store.Reservations[created.ReservationId].RejectionRemark);
    }

    [Fact]
    public async Task RejectRejectsNonOperatorOrNonPendingOrMissingRemark()
    {
        // Only GridOperator can reject, only when Pending, with a valid remark.
        var f = new Fixture();
        var created = await f.Create();

        // Prosumer and Backoffice cannot reject
        var validReq = new RejectReservationRequest { Remark = "Reason" };
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.RejectAsync("P1", created.ReservationId, validReq));
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.RejectAsync("BO", created.ReservationId, validReq));

        // Missing or whitespace remark rejected
        var invalidReq = new RejectReservationRequest { Remark = "   " };
        await Assert.ThrowsAsync<BadRequestException>(() => f.Service.RejectAsync("OP", created.ReservationId, invalidReq));

        // Operator rejects
        await f.Service.RejectAsync("OP", created.ReservationId, validReq);

        // Cannot reject already-rejected reservation
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.RejectAsync("OP", created.ReservationId, validReq));
    }

    [Theory]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    [InlineData(ReservationStatus.Completed)]
    public async Task TerminalStatesCannotUpdateOrCancel(ReservationStatus status)
    {
        // Terminal-state enforcement occurs in orchestration before any capacity mutation.
        var f = new Fixture();
        var created = await f.Create();
        f.Store.Reservations[created.ReservationId].Status = status;
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.CancelAsync("P1", created.ReservationId));
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.UpdateAsync("P1", created.ReservationId,
            new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 2 }));
        Assert.Equal(1, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task OwnershipAndRolesProtectEveryOperation()
    {
        // Other Prosumers and Backoffice cannot inspect or manipulate a reservation.
        var f = new Fixture();
        var created = await f.Create();
        foreach (var actor in new[] { "P2", "BO" })
        {
            await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.GetAsync(actor, created.ReservationId));
            await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.CancelAsync(actor, created.ReservationId));
            await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.UpdateAsync(actor, created.ReservationId,
                new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 2 }));
        }
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.CreateForAsync("P1", "P2", f.Request()));
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.CreateAsync("OP", f.Request()));
        Assert.Equal("P2", (await f.Service.CreateForAsync("OP", "P2", f.Request())).ProsumerNic);
    }

    [Fact]
    public async Task MissingInactiveAndWrongRoleAccountsReject()
    {
        // The application layer reloads stored account state for each operation.
        var f = new Fixture();
        await Assert.ThrowsAsync<UnauthorizedException>(() => f.Service.CreateAsync("", f.Request()));
        await Assert.ThrowsAsync<UnauthorizedException>(() => f.Service.CreateAsync("missing", f.Request()));
        f.Users.Items["P1"].Status = UserStatus.Deactivated;
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Create());
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.CreateForAsync("OP", "P1", f.Request()));
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.CreateForAsync("OP", "BO", f.Request()));
        await Assert.ThrowsAsync<NotFoundException>(() => f.Service.CreateForAsync("OP", "missing", f.Request()));
    }

    [Fact]
    public async Task MoveTransfersCapacityAndClearsApproval()
    {
        // Only the source place is released after the target reservation write succeeds.
        var f = new Fixture();
        var created = await f.Create();
        f.Store.Reservations[created.ReservationId].Status = ReservationStatus.Approved;
        f.Store.Reservations[created.ReservationId].QrToken = "stale";
        var next = f.AddSlot(Now.AddDays(3));
        var result = await f.Service.UpdateAsync("P1", created.ReservationId,
            new UpdateReservationRequest { SlotId = next.SlotId, EnergyAmountKwh = 2 });
        Assert.Equal(2, f.Slot.AvailableSlots);
        Assert.Equal(1, next.AvailableSlots);
        Assert.Equal(next.StartAtUtc, result.ScheduledStartAtUtc);
        Assert.Equal(ReservationStatus.Pending, result.Status);
        Assert.Null(f.Store.Reservations[created.ReservationId].QrToken);
    }

    [Theory]
    [InlineData("full")]
    [InlineData("horizon")]
    [InlineData("notice")]
    [InlineData("overlap")]
    public async Task InvalidMovePreservesOriginalReservationAndCapacity(string scenario)
    {
        // Rejected replacements must not give up the accepted source place.
        var f = new Fixture();
        var created = await f.Create();
        var next = f.AddSlot(Now.AddDays(3));
        if (scenario == "full") next.AvailableSlots = 0;
        if (scenario == "horizon") next.StartAtUtc = Now.AddDays(7).AddSeconds(1);
        if (scenario == "notice") next.StartAtUtc = Now.AddSeconds(43199);
        next.EndAtUtc = next.StartAtUtc.AddHours(1);
        if (scenario == "overlap") await f.Create(slot: next);
        var before = next.AvailableSlots;
        var request = new UpdateReservationRequest { SlotId = next.SlotId, EnergyAmountKwh = 2 };
        if (scenario == "horizon") await Assert.ThrowsAsync<BadRequestException>(() => f.Service.UpdateAsync("P1", created.ReservationId, request));
        else await Assert.ThrowsAsync<ConflictException>(() => f.Service.UpdateAsync("P1", created.ReservationId, request));
        Assert.Equal(before, next.AvailableSlots);
        Assert.Equal(1, f.Slot.AvailableSlots);
        Assert.Equal(f.Slot.SlotId, f.Store.Reservations[created.ReservationId].SlotId);
    }

    [Fact]
    public async Task SameSlotAmountUpdateDoesNotRequireAnotherPlace()
    {
        // A full slot still contains the reservation's already acquired place.
        var f = new Fixture();
        var created = await f.Create();
        await f.Create("P2");
        await f.Service.UpdateAsync("P1", created.ReservationId,
            new UpdateReservationRequest { SlotId = f.Slot.SlotId, EnergyAmountKwh = 2 });
        Assert.Equal(0, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task GetAvailableSlotsReturnsActiveSlotsWithinHorizon()
    {
        var f = new Fixture();
        var validSlot = f.AddSlot(Now.AddDays(2));
        var outOfHorizonSlot = f.AddSlot(Now.AddDays(8));
        var zeroCapacitySlot = f.AddSlot(Now.AddDays(1));
        zeroCapacitySlot.AvailableSlots = 0;

        var slots = await f.Service.GetAvailableSlotsAsync("P1");
        Assert.Contains(slots, s => s.SlotId == validSlot.SlotId);
        Assert.DoesNotContain(slots, s => s.SlotId == outOfHorizonSlot.SlotId);
        Assert.DoesNotContain(slots, s => s.SlotId == zeroCapacitySlot.SlotId);
    }

    [Fact]
    public async Task AcceptedSnapshotSurvivesSlotChangesAndLegacyFailsClosed()
    {
        // Cancellation cutoff comes from accepted history, never a newly edited slot.
        var f = new Fixture();
        var created = await f.Create();
        f.Slot.StartAtUtc = Now;
        Assert.Equal(created.ScheduledStartAtUtc, (await f.Service.GetAsync("P1", created.ReservationId)).ScheduledStartAtUtc);
        await f.Service.CancelAsync("P1", created.ReservationId);
        f.Store.Reservations[created.ReservationId].ScheduledStartAtUtc = null;
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.GetAsync("P1", created.ReservationId));
    }

    [Fact]
    public async Task ConfirmedCasMissCompensatesAcquiredTarget()
    {
        // A known unsuccessful update safely restores capacity and releases the user lock.
        var f = new Fixture();
        var created = await f.Create();
        var next = f.AddSlot(Now.AddDays(3));
        f.Store.CasMiss = true;
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.UpdateAsync("P1", created.ReservationId,
            new UpdateReservationRequest { SlotId = next.SlotId, EnergyAmountKwh = 2 }));
        Assert.Equal(2, next.AvailableSlots);
        Assert.Equal(1, f.Slot.AvailableSlots);
        Assert.Empty(f.Store.Locks);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task AmbiguousInsertNeverRestoresPotentiallyUsedCapacity(bool committed)
    {
        // Simulate a lost acknowledgement both before and after the server committed the insert.
        var f = new Fixture();
        f.Store.InsertFailure = true;
        f.Store.CommitBeforeFailure = committed;
        await Assert.ThrowsAsync<ConflictException>(() => f.Create());
        Assert.Equal(1, f.Slot.AvailableSlots);
        Assert.True(f.Store.Locks.ContainsKey("P1"));
        Assert.Equal(committed ? 1 : 0, f.Store.Reservations.Count);
        await Assert.ThrowsAsync<ConflictException>(() => f.Create());
        Assert.Equal(1, f.Slot.AvailableSlots);
    }

    [Fact]
    public async Task ReleaseFailureKeepsCancelledReservationLockedWithoutDoubleRelease()
    {
        // A partial cancellation requires reconciliation; a retry cannot free capacity again.
        var f = new Fixture();
        var created = await f.Create();
        f.Store.ReleaseFailure = true;
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.CancelAsync("P1", created.ReservationId));
        Assert.Equal(ReservationStatus.Cancelled, f.Store.Reservations[created.ReservationId].Status);
        Assert.True(f.Store.Locks.ContainsKey("P1"));
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.CancelAsync("P1", created.ReservationId));
        Assert.Equal(1, f.Store.ReleaseCalls);
    }

    [Fact]
    public async Task ClientCancellationAfterCapacityAcquisitionDoesNotAbandonWrite()
    {
        // Request cancellation is honored before mutation, but cannot interrupt an in-flight durable write.
        var f = new Fixture();
        using var source = new CancellationTokenSource();
        f.Store.AfterAcquire = source.Cancel;
        await f.Service.CreateAsync("P1", f.Request(), source.Token);
        Assert.Single(f.Store.Reservations);
        Assert.Empty(f.Store.Locks);
    }


    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task AmbiguousMoveKeepsBothPlacesUntilReconciliation(bool committed)
    {
        // Neither place can be released when the replacement write acknowledgement is lost.
        var f = new Fixture();
        var created = await f.Create();
        var next = f.AddSlot(Now.AddDays(3));
        f.Store.ReplaceFailure = true;
        f.Store.CommitBeforeFailure = committed;
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.UpdateAsync("P1", created.ReservationId,
            new UpdateReservationRequest { SlotId = next.SlotId, EnergyAmountKwh = 2 }));
        Assert.Equal(1, f.Slot.AvailableSlots);
        Assert.Equal(1, next.AvailableSlots);
        Assert.True(f.Store.Locks.ContainsKey("P1"));
        Assert.Equal(committed ? next.SlotId : f.Slot.SlotId, f.Store.Reservations[created.ReservationId].SlotId);
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.CancelAsync("P1", created.ReservationId));
    }

    [Fact]
    public async Task LostCapacityAcknowledgementKeepsLockWithoutGuessing()
    {
        // An acquired place with a lost acknowledgement cannot safely be decremented again.
        var f = new Fixture();
        f.Store.AcquireFailure = true;
        await Assert.ThrowsAsync<ConflictException>(() => f.Create());
        Assert.Empty(f.Store.Reservations);
        Assert.Equal(1, f.Slot.AvailableSlots);
        Assert.True(f.Store.Locks.ContainsKey("P1"));
    }

    [Fact]
    public async Task AssistedCreateRechecksRoleAfterLockAcquisition()
    {
        // A caller losing operator permission while waiting cannot retain assisted creation authority.
        var f = new Fixture();
        f.Store.AfterLock = () => f.Users.Items["OP"].Role = UserRole.Prosumer;
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.CreateForAsync("OP", "P1", f.Request()));
        Assert.Empty(f.Store.Reservations);
        Assert.Empty(f.Store.Locks);
        Assert.Equal(2, f.Slot.AvailableSlots);
    }


    [Fact]
    public async Task ListingAuthorizesServiceCallersAndValidatesFilters()
    {
        // Direct application calls must be as restricted as the HTTP route.
        var f = new Fixture();
        await f.Create();
        foreach (var actor in new[] { "P1", "P2", "BO" })
            await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.ListAsync(actor, new ListReservationsRequest()));
        await Assert.ThrowsAsync<BadRequestException>(() => f.Service.ListAsync("OP",
            new ListReservationsRequest { Status = (ReservationStatus)999 }));
        await Assert.ThrowsAsync<BadRequestException>(() => f.Service.ListAsync("OP",
            new ListReservationsRequest { StationId = "bad" }));
        var rows = await f.Service.ListAsync("OP", new ListReservationsRequest { ProsumerNic = " p1 ", StationId = f.Station.StationId });
        Assert.Single(rows);
        Assert.Equal("P1", rows[0].ProsumerNic);
        f.Users.Items["OP"].Status = UserStatus.Deactivated;
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.ListAsync("OP", new ListReservationsRequest()));
    }

    [Fact]
    public async Task GetMyReservationsReturnsOnlyCallerReservations()
    {
        var f = new Fixture();
        await f.Create("P1");
        await f.Create("P2");

        var p1Rows = await f.Service.GetMyReservationsAsync("P1");
        Assert.Single(p1Rows);
        Assert.Equal("P1", p1Rows[0].ProsumerNic);

        var p2Rows = await f.Service.GetMyReservationsAsync("P2");
        Assert.Single(p2Rows);
        Assert.Equal("P2", p2Rows[0].ProsumerNic);

        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.GetMyReservationsAsync("OP"));
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.GetMyReservationsAsync("BO"));
    }

    private sealed class Fixture
    {
        public MemoryReservations Store { get; } = new();
        public MemoryUsers Users { get; } = new();
        public ReservationService Service { get; }
        public SolarStation Station { get; } = new() { CapacityKwh = 100, IsActive = true };
        public EnergyBookingSlot Slot { get; }

        public Fixture()
        {
            // Every test has independent persistence and a fixed authoritative clock.
            foreach (var pair in new[] { ("P1", UserRole.Prosumer), ("P2", UserRole.Prosumer), ("OP", UserRole.GridOperator), ("BO", UserRole.Backoffice) })
                Users.Items[pair.Item1] = new User { Nic = pair.Item1, Role = pair.Item2, Status = UserStatus.Active };
            Store.Stations[Station.StationId] = Station;
            Slot = AddSlot(Now.AddDays(2));
            Service = new ReservationService(Store, Users, new SmartSolar.Infrastructure.Security.QrSecurityService(), new FixedClock());
        }

        public EnergyBookingSlot AddSlot(DateTime start)
        {
            // Provide server-owned slot IDs and schedules rather than client schedule inputs.
            var slot = new EnergyBookingSlot { StationId = Station.StationId, StartAtUtc = start, EndAtUtc = start.AddHours(1), TotalSlots = 2, AvailableSlots = 2 };
            Store.Slots[slot.SlotId] = slot;
            return slot;
        }

        public CreateReservationRequest Request(decimal amount = 1, EnergyBookingSlot? slot = null)
        {
            // Use only the two reviewed request fields.
            return new CreateReservationRequest { SlotId = (slot ?? Slot).SlotId, EnergyAmountKwh = amount };
        }

        public Task<ReservationResponse> Create(string nic = "P1", decimal amount = 1, EnergyBookingSlot? slot = null)
        {
            // Invoke real application orchestration over the isolated repository double.
            return Service.CreateAsync(nic, Request(amount, slot));
        }
    }

    private sealed class FixedClock : TimeProvider
    {
        public override DateTimeOffset GetUtcNow()
        {
            // All service boundaries use this deterministic instant.
            return new DateTimeOffset(Now);
        }
    }

    private sealed class MemoryReservations : IReservationRepository
    {
        public Dictionary<string, EnergyReservation> Reservations { get; } = [];
        public Dictionary<string, EnergyBookingSlot> Slots { get; } = [];
        public Dictionary<string, SolarStation> Stations { get; } = [];
        public Dictionary<string, string> Locks { get; } = [];
        public bool CasMiss { get; set; }
        public bool InsertFailure { get; set; }
        public bool CommitBeforeFailure { get; set; }
        public bool ReleaseFailure { get; set; }
        public bool ReplaceFailure { get; set; }
        public bool AcquireFailure { get; set; }
        public Action? AfterLock { get; set; }
        public int ReleaseCalls { get; private set; }
        public Action? AfterAcquire { get; set; }

        private static T? Copy<T>(T? value)
        {
            // Return detached objects so tests detect writes accidentally made before persistence.
            return value is null ? default : JsonSerializer.Deserialize<T>(JsonSerializer.Serialize(value));
        }

        public Task<IReadOnlyList<EnergyReservation>> ListAsync(ReservationStatus? status, string? prosumerNic, string? stationId, CancellationToken ct = default)
        {
            // Match the real repository's exact filters and deterministic ordering with detached records.
            return Task.FromResult<IReadOnlyList<EnergyReservation>>(Reservations.Values
                .Where(x => (!status.HasValue || x.Status == status.Value) &&
                    (prosumerNic is null || x.ProsumerNic == prosumerNic) && (stationId is null || x.StationId == stationId))
                .OrderByDescending(x => x.CreatedAtUtc).ThenBy(x => x.ReservationId).Select(x => Copy(x)!).ToList());
        }

        public Task<EnergyReservation?> GetAsync(string id, CancellationToken ct = default) => Task.FromResult(Copy(Reservations.GetValueOrDefault(id)));
        public Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct = default) => Task.FromResult(Copy(Slots.GetValueOrDefault(id)));
        public Task<IReadOnlyList<EnergyBookingSlot>> GetActiveSlotsAsync(CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyBookingSlot>>(Slots.Values.Where(x => x.IsActive && x.AvailableSlots > 0).Select(x => Copy(x)!).ToList());
        public Task<SolarStation?> GetStationAsync(string id, CancellationToken ct = default) => Task.FromResult(Copy(Stations.GetValueOrDefault(id)));
        public Task<IReadOnlyList<EnergyReservation>> GetActiveAsync(string nic, CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyReservation>>(Reservations.Values.Where(x => x.ProsumerNic == nic && x.Status is ReservationStatus.Pending or ReservationStatus.Approved).Select(x => Copy(x)!).ToList());
        public Task<IReadOnlyList<EnergyReservation>> GetActiveBySlotAsync(string slotId, CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyReservation>>(Reservations.Values.Where(x => x.SlotId == slotId && x.Status is ReservationStatus.Pending or ReservationStatus.Approved).Select(x => Copy(x)!).ToList());

        public Task<bool> TryLockAsync(string nic, string token, CancellationToken ct = default)
        {
            // Model non-reentrant durable ownership; actual multi-process races use Mongo tests.
            var acquired = Locks.TryAdd(nic, token);
            if (acquired) AfterLock?.Invoke();
            return Task.FromResult(acquired);
        }

        public Task UnlockAsync(string nic, string token, CancellationToken ct = default)
        {
            // Detect attempted release by the wrong operation.
            Assert.Equal(token, Locks[nic]);
            Locks.Remove(nic);
            return Task.CompletedTask;
        }

        public Task<bool> TryAcquireCapacityAsync(EnergyBookingSlot expected, CancellationToken ct = default)
        {
            // Model conditional acquisition, with an optional client-cancellation injection.
            ct.ThrowIfCancellationRequested();
            var slot = Slots[expected.SlotId];
            if (!slot.IsActive || slot.AvailableSlots <= 0) return Task.FromResult(false);
            slot.AvailableSlots--;
            if (AcquireFailure) throw new IOException("simulated lost acknowledgement");
            AfterAcquire?.Invoke();
            return Task.FromResult(true);
        }

        public Task<bool> ReleaseCapacityAsync(string slotId, CancellationToken ct = default)
        {
            // Count release attempts to detect duplicated cancellation effects.
            ReleaseCalls++;
            if (ReleaseFailure) throw new IOException("simulated lost acknowledgement");
            var slot = Slots[slotId];
            if (slot.AvailableSlots >= slot.TotalSlots) return Task.FromResult(false);
            slot.AvailableSlots++;
            return Task.FromResult(true);
        }

        public Task InsertAsync(EnergyReservation reservation, CancellationToken ct = default)
        {
            // Simulate both possible outcomes of an ambiguous network failure.
            ct.ThrowIfCancellationRequested();
            if (!InsertFailure || CommitBeforeFailure) Reservations.Add(reservation.ReservationId, Copy(reservation)!);
            if (InsertFailure) throw new IOException("simulated lost acknowledgement");
            return Task.CompletedTask;
        }

        public Task<bool> TryReplaceAsync(EnergyReservation expected, EnergyReservation replacement, CancellationToken ct = default)
        {
            // A deliberate CAS miss exercises application compensation independently of Mongo.
            ct.ThrowIfCancellationRequested();
            if (CasMiss) return Task.FromResult(false);
            if (!ReplaceFailure || CommitBeforeFailure) Reservations[replacement.ReservationId] = Copy(replacement)!;
            if (ReplaceFailure) throw new IOException("simulated lost acknowledgement");
            return Task.FromResult(true);
        }

        public Task<EnergyReservation?> GetByQrHashAsync(string qrTokenHash, CancellationToken ct = default)
        {
            if (string.IsNullOrWhiteSpace(qrTokenHash)) return Task.FromResult<EnergyReservation?>(null);
            return Task.FromResult(Copy(Reservations.Values.FirstOrDefault(x => x.QrTokenHash == qrTokenHash)));
        }

        public Task<bool> TryUpdateQrHashAsync(
            string reservationId, string? expectedHash, string newHash, DateTime issuedAtUtc, DateTime updatedAtUtc, CancellationToken ct = default)
        {
            if (!Reservations.TryGetValue(reservationId, out var existing)) return Task.FromResult(false);
            if (existing.Status != ReservationStatus.Approved || existing.QrTokenHash != expectedHash) return Task.FromResult(false);
            existing.QrTokenHash = newHash;
            existing.QrIssuedAtUtc = issuedAtUtc;
            existing.UpdatedAtUtc = updatedAtUtc;
            return Task.FromResult(true);
        }

        public Task<bool> TryCompleteReservationAsync(
            string reservationId, string qrTokenHash, string operatorNic, DateTime completedAtUtc, DateTime updatedAtUtc, CancellationToken ct = default)
        {
            if (!Reservations.TryGetValue(reservationId, out var existing)) return Task.FromResult(false);
            if (existing.Status != ReservationStatus.Approved || existing.QrTokenHash != qrTokenHash) return Task.FromResult(false);
            existing.Status = ReservationStatus.Completed;
            existing.CompletedAtUtc = completedAtUtc;
            existing.CompletedByOperatorNic = operatorNic;
            existing.UpdatedAtUtc = updatedAtUtc;
            return Task.FromResult(true);
        }
    }

    private sealed class MemoryUsers : IUserRepository
    {
        public Dictionary<string, User> Items { get; } = [];
        // Account doubles expose only the common repository contract used by the real service.
        public Task<User?> GetByNicAsync(string nic, CancellationToken cancellationToken = default) => Task.FromResult(Items.GetValueOrDefault(nic));
        public Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default) => Task.FromResult(Items.Values.FirstOrDefault(x => x.Email == email));
        public Task<IReadOnlyList<User>> GetAllAsync(CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<User>>(Items.Values.ToList());
        public Task<IReadOnlyList<User>> GetByStatusAsync(UserStatus status, CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<User>>(Items.Values.Where(x => x.Status == status).ToList());
        public Task InsertAsync(User user, CancellationToken cancellationToken = default) { Items.Add(user.Nic, user); return Task.CompletedTask; }
        public Task ReplaceAsync(User user, CancellationToken cancellationToken = default) { Items[user.Nic] = user; return Task.CompletedTask; }
    }
}
