/*
 * File: ReservationQrServiceTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Tests QR issuance, reissuance/rotation, and server-side verification policies.
 * Note: Keep this header and update method-level comments as the code evolves.
 */

using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;
using SmartSolar.Infrastructure.Security;
using Xunit;

namespace SmartSolar.UnitTests;

public sealed class ReservationQrServiceTests
{
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);

    [Fact]
    public async Task ApprovedReservationOwnerCanIssueQr()
    {
        // Approved reservation returns secure opaque QR reference without sensitive internal data.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);

        var response = await f.Service.IssueQrAsync("P1", res.ReservationId);

        Assert.Equal(res.ReservationId, response.ReservationId);
        Assert.StartsWith("SMG1.", response.QrPayload);
        Assert.Equal(Now, response.IssuedAtUtc);

        // Verify stored hash in repository is SHA-256 and not raw token
        var updated = await f.Store.GetAsync(res.ReservationId);
        Assert.NotNull(updated?.QrTokenHash);
        Assert.NotEqual(response.QrPayload, updated!.QrTokenHash);
        Assert.Equal(f.Security.ComputeHash(response.QrPayload), updated.QrTokenHash);
    }

    [Theory]
    [InlineData(ReservationStatus.Pending)]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    [InlineData(ReservationStatus.Completed)]
    public async Task NonApprovedReservationCannotIssueQr(ReservationStatus status)
    {
        // Only Approved reservations can receive QR codes; other states return 409 Conflict.
        var f = new QrFixture();
        var res = f.AddReservation("P1", status);

        var ex = await Assert.ThrowsAsync<ConflictException>(() => f.Service.IssueQrAsync("P1", res.ReservationId));
        Assert.Contains("Only Approved reservations", ex.Message);
    }

    [Fact]
    public async Task OtherProsumerCannotIssueOwnersQr()
    {
        // Prosumer ownership is enforced server-side; another Prosumer receives 403 Forbidden.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);

        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.IssueQrAsync("P2", res.ReservationId));
    }

    [Fact]
    public async Task QrReissueRotatesAndInvalidatesPreviousReference()
    {
        // Re-requesting QR for the same Approved reservation rotates the hash and invalidates the previous token.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);

        var qr1 = await f.Service.IssueQrAsync("P1", res.ReservationId);
        var qr2 = await f.Service.IssueQrAsync("P1", res.ReservationId);

        Assert.NotEqual(qr1.QrPayload, qr2.QrPayload);

        // First QR is now revoked and rejected on verification
        await Assert.ThrowsAsync<NotFoundException>(() =>
            f.Service.VerifyQrAsync("OP", new VerifyReservationQrRequest { QrPayload = qr1.QrPayload }));

        // Second QR verifies successfully
        var verified = await f.Service.VerifyQrAsync("OP", new VerifyReservationQrRequest { QrPayload = qr2.QrPayload });
        Assert.Equal(res.ReservationId, verified.ReservationId);
        Assert.Equal(ReservationStatus.Approved, verified.Status);
        Assert.True(verified.EligibleForCompletion);
    }

    [Fact]
    public async Task GridOperatorCanVerifyValidApprovedQr()
    {
        // GridOperator verifying a valid QR receives trusted server data without completing the transaction.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        var verified = await f.Service.VerifyQrAsync("OP", new VerifyReservationQrRequest { QrPayload = qr.QrPayload });

        Assert.Equal(res.ReservationId, verified.ReservationId);
        Assert.Equal("P1", verified.ProsumerNic);
        Assert.Equal(res.StationId, verified.StationId);
        Assert.Equal(res.SlotId, verified.SlotId);
        Assert.Equal(res.EnergyAmountKwh, verified.EnergyAmountKwh);
        Assert.Equal(res.ScheduledStartAtUtc, verified.ScheduledStartAtUtc);
        Assert.Equal(res.ScheduledEndAtUtc, verified.ScheduledEndAtUtc);
        Assert.Equal(ReservationStatus.Approved, verified.Status);
        Assert.True(verified.EligibleForCompletion);

        // Authoritative reservation state is still Approved (NOT completed)
        var persisted = await f.Store.GetAsync(res.ReservationId);
        Assert.Equal(ReservationStatus.Approved, persisted?.Status);
    }

    [Fact]
    public async Task ProsumerCannotVerifyQr()
    {
        // Prosumer is forbidden from calling the GridOperator verification use case.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            f.Service.VerifyQrAsync("P1", new VerifyReservationQrRequest { QrPayload = qr.QrPayload }));
    }

    [Fact]
    public async Task VerificationRejectsMalformedQrPayload()
    {
        // Malformed payload returns 400 BadRequest.
        var f = new QrFixture();
        await Assert.ThrowsAsync<BadRequestException>(() =>
            f.Service.VerifyQrAsync("OP", new VerifyReservationQrRequest { QrPayload = "INVALID_FORMAT_NOT_SMG1" }));
    }

    [Fact]
    public async Task VerificationRejectsNonExistentOrRandomQr()
    {
        // Unrecognized token returns 404 NotFound.
        var f = new QrFixture();
        var randomPayload = f.Security.GeneratePayload();

        await Assert.ThrowsAsync<NotFoundException>(() =>
            f.Service.VerifyQrAsync("OP", new VerifyReservationQrRequest { QrPayload = randomPayload }));
    }

    [Theory]
    [InlineData(ReservationStatus.Pending)]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    [InlineData(ReservationStatus.Completed)]
    public async Task VerificationRejectsReservationInIneligibleStatus(ReservationStatus status)
    {
        // If reservation state changed after QR issuance (e.g. cancelled/completed), verification returns 409 Conflict.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        // State changes to non-approved in database
        res.Status = status;
        f.Store.Reservations[res.ReservationId] = res;

        var ex = await Assert.ThrowsAsync<ConflictException>(() =>
            f.Service.VerifyQrAsync("OP", new VerifyReservationQrRequest { QrPayload = qr.QrPayload }));
        Assert.Contains("not in an Approved state", ex.Message);
    }

    [Fact]
    public async Task GridOperatorCanCompleteApprovedReservation()
    {
        // GridOperator can successfully complete an energy transfer for an Approved reservation.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        var response = await f.Service.CompleteTransferAsync("OP", new CompleteReservationTransferRequest(qr.QrPayload));

        Assert.NotNull(response);
        Assert.Equal(res.ReservationId, response.ReservationId);
        Assert.Equal(ReservationStatus.Completed, response.Status);
        Assert.Equal("OP", response.CompletedByOperatorNic);
        Assert.Equal(Now, response.CompletedAtUtc);

        var stored = f.Store.Reservations[res.ReservationId];
        Assert.Equal(ReservationStatus.Completed, stored.Status);
        Assert.Equal("OP", stored.CompletedByOperatorNic);
        Assert.Equal(Now, stored.CompletedAtUtc);
    }

    [Fact]
    public async Task SecondCompletionAttemptIsRejectedWithConflict()
    {
        // Replaying completion or trying to complete an already completed reservation is rejected.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        // First completion succeeds
        await f.Service.CompleteTransferAsync("OP", new CompleteReservationTransferRequest(qr.QrPayload));

        // Second completion fails with ConflictException
        var ex = await Assert.ThrowsAsync<ConflictException>(() =>
            f.Service.CompleteTransferAsync("OP", new CompleteReservationTransferRequest(qr.QrPayload, res.ReservationId)));
        Assert.Contains("already been completed", ex.Message);
    }

    [Fact]
    public async Task ProsumerCannotCompleteReservation()
    {
        // Prosumer is forbidden from completing transactions.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            f.Service.CompleteTransferAsync("P1", new CompleteReservationTransferRequest(qr.QrPayload)));
    }

    [Theory]
    [InlineData(ReservationStatus.Pending)]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    public async Task IneligibleStatusCannotBeCompleted(ReservationStatus status)
    {
        // Non-approved status cannot be completed.
        var f = new QrFixture();
        var res = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", res.ReservationId);

        res.Status = status;
        f.Store.Reservations[res.ReservationId] = res;

        var ex = await Assert.ThrowsAsync<ConflictException>(() =>
            f.Service.CompleteTransferAsync("OP", new CompleteReservationTransferRequest(qr.QrPayload, res.ReservationId)));
        Assert.Contains("not in an Approved state", ex.Message);
    }

    [Fact]
    public async Task InvalidQrReferenceCannotComplete()
    {
        // Unknown QR payload cannot complete a transaction.
        var f = new QrFixture();
        var randomPayload = f.Security.GeneratePayload();

        await Assert.ThrowsAsync<NotFoundException>(() =>
            f.Service.CompleteTransferAsync("OP", new CompleteReservationTransferRequest(randomPayload)));
    }


    [Theory]
    [InlineData("OP")]
    [InlineData("BO")]
    public async Task NonProsumerCannotIssueOrRotateQr(string actor)
    {
        var f = new QrFixture();
        var reservation = f.AddReservation("P1", ReservationStatus.Approved);
        await Assert.ThrowsAsync<ForbiddenException>(() => f.Service.IssueQrAsync(actor, reservation.ReservationId));
        Assert.Null(reservation.QrTokenHash);
    }

    [Theory]
    [InlineData(-1, false)]
    [InlineData(0, true)]
    [InlineData(3599999, true)]
    [InlineData(3600000, false)]
    [InlineData(3600001, false)]
    public async Task TransferUsesInclusiveStartExclusiveEnd(int milliseconds, bool eligible)
    {
        var f = new QrFixture();
        var reservation = f.AddReservation("P1", ReservationStatus.Approved);
        f.Clock.Current = Now.AddDays(-1);
        var qr = await f.Service.IssueQrAsync("P1", reservation.ReservationId);
        f.Clock.Current = Now.AddMilliseconds(milliseconds);
        if (eligible)
        {
            var verified = await f.Service.VerifyQrAsync("OP", new() { QrPayload = qr.QrPayload });
            Assert.True(verified.EligibleForCompletion);
            await f.Service.CompleteTransferAsync("OP", new(qr.QrPayload));
            Assert.Equal(4, f.Slot.AvailableSlots);
        }
        else
        {
            await Assert.ThrowsAsync<ConflictException>(() => f.Service.VerifyQrAsync("OP", new() { QrPayload = qr.QrPayload }));
            await Assert.ThrowsAsync<ConflictException>(() => f.Service.CompleteTransferAsync("OP", new(qr.QrPayload)));
            Assert.Equal(ReservationStatus.Approved, reservation.Status);
        }
    }

    [Theory]
    [InlineData("missing-start")]
    [InlineData("missing-end")]
    [InlineData("inverted")]
    [InlineData("owner-missing")]
    [InlineData("owner-inactive")]
    [InlineData("owner-pending")]
    [InlineData("owner-wrong-role")]
    [InlineData("station-missing")]
    [InlineData("station-inactive")]
    [InlineData("slot-missing")]
    [InlineData("slot-inactive")]
    [InlineData("slot-parent")]
    public async Task TransferRejectsInvalidAuthoritativeReferences(string condition)
    {
        var f = new QrFixture();
        var reservation = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", reservation.ReservationId);
        switch (condition)
        {
            case "missing-start": reservation.ScheduledStartAtUtc = null; break;
            case "missing-end": reservation.ScheduledEndAtUtc = null; break;
            case "inverted": reservation.ScheduledEndAtUtc = Now.AddSeconds(-1); break;
            case "owner-missing": f.Users.Items.Remove("P1"); break;
            case "owner-inactive": f.Users.Items["P1"].Status = UserStatus.Deactivated; break;
            case "owner-pending": f.Users.Items["P1"].Status = UserStatus.PendingActivation; break;
            case "owner-wrong-role": f.Users.Items["P1"].Role = UserRole.GridOperator; break;
            case "station-missing": f.Store.Stations.Clear(); break;
            case "station-inactive": f.Station.IsActive = false; break;
            case "slot-missing": f.Store.Slots.Clear(); break;
            case "slot-inactive": f.Slot.IsActive = false; break;
            case "slot-parent": f.Slot.StationId = "different-station"; break;
        }
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.VerifyQrAsync("OP", new() { QrPayload = qr.QrPayload }));
        await Assert.ThrowsAsync<ConflictException>(() => f.Service.CompleteTransferAsync("OP", new(qr.QrPayload)));
        Assert.Equal(ReservationStatus.Approved, reservation.Status);
        Assert.Null(reservation.CompletedAtUtc);
    }

    [Fact]
    public async Task TransferUsesAcceptedSnapshotInsteadOfMutableSlotTimes()
    {
        var f = new QrFixture();
        var reservation = f.AddReservation("P1", ReservationStatus.Approved);
        var qr = await f.Service.IssueQrAsync("P1", reservation.ReservationId);
        f.Slot.StartAtUtc = Now.AddDays(1);
        f.Slot.EndAtUtc = Now.AddDays(1).AddHours(1);
        var verified = await f.Service.VerifyQrAsync("OP", new() { QrPayload = qr.QrPayload });
        Assert.Equal(Now, verified.ScheduledStartAtUtc);
        await f.Service.CompleteTransferAsync("OP", new(qr.QrPayload));
        Assert.Equal(ReservationStatus.Completed, reservation.Status);
    }

    private sealed class QrFixture
    {
        public MemoryReservations Store { get; } = new();
        public MemoryUsers Users { get; } = new();
        public QrSecurityService Security { get; } = new();
        public ReservationService Service { get; }
        public FixedClock Clock { get; } = new();
        public SolarStation Station { get; } = new() { CapacityKwh = 100, IsActive = true };
        public EnergyBookingSlot Slot { get; }

        public QrFixture()
        {
            foreach (var (nic, role) in new[] { ("P1", UserRole.Prosumer), ("P2", UserRole.Prosumer), ("OP", UserRole.GridOperator), ("BO", UserRole.Backoffice) })
            {
                Users.Items[nic] = new User { Nic = nic, Role = role, Status = UserStatus.Active };
            }
            Store.Stations[Station.StationId] = Station;
            Slot = new EnergyBookingSlot
            {
                StationId = Station.StationId,
                StartAtUtc = Now,
                EndAtUtc = Now.AddHours(1),
                TotalSlots = 5,
                AvailableSlots = 4,
                IsActive = true
            };
            Store.Slots[Slot.SlotId] = Slot;
            Service = new ReservationService(Store, Users, Security, Clock, new CatalogWriteGate());
        }

        public EnergyReservation AddReservation(string nic, ReservationStatus status)
        {
            var res = new EnergyReservation
            {
                ReservationId = Guid.NewGuid().ToString("N"),
                ProsumerNic = nic,
                StationId = Station.StationId,
                SlotId = Slot.SlotId,
                EnergyAmountKwh = 25.0m,
                Status = status,
                ScheduledStartAtUtc = Slot.StartAtUtc,
                ScheduledEndAtUtc = Slot.EndAtUtc,
                CreatedAtUtc = Now,
                UpdatedAtUtc = Now
            };
            Store.Reservations[res.ReservationId] = res;
            return res;
        }
    }

    private sealed class FixedClock : TimeProvider
    {
        public DateTime Current { get; set; } = Now;
        public override DateTimeOffset GetUtcNow() => new(Current);
    }

    private sealed class MemoryReservations : IReservationRepository
    {
        public Dictionary<string, EnergyReservation> Reservations { get; } = [];
        public Dictionary<string, EnergyBookingSlot> Slots { get; } = [];
        public Dictionary<string, SolarStation> Stations { get; } = [];

        public Task<IReadOnlyList<EnergyReservation>> ListAsync(ReservationStatus? status, string? prosumerNic, string? stationId, CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyReservation>>(Reservations.Values.Where(x => (!status.HasValue || x.Status == status.Value) && (prosumerNic is null || x.ProsumerNic == prosumerNic)).ToList());
        public Task<EnergyReservation?> GetAsync(string id, CancellationToken ct = default) => Task.FromResult(Reservations.GetValueOrDefault(id));
        public Task<EnergyBookingSlot?> GetSlotAsync(string id, CancellationToken ct = default) => Task.FromResult(Slots.GetValueOrDefault(id));
        public Task<SolarStation?> GetStationAsync(string id, CancellationToken ct = default) => Task.FromResult(Stations.GetValueOrDefault(id));
        public Task<IReadOnlyList<EnergyReservation>> GetActiveAsync(string nic, CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyReservation>>(Reservations.Values.Where(x => x.ProsumerNic == nic && x.Status is ReservationStatus.Pending or ReservationStatus.Approved).ToList());
        public Task<IReadOnlyList<EnergyReservation>> GetActiveBySlotAsync(string slotId, CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyReservation>>(Reservations.Values.Where(x => x.SlotId == slotId && x.Status is ReservationStatus.Pending or ReservationStatus.Approved).ToList());
        public Task<IReadOnlyList<EnergyBookingSlot>> GetActiveSlotsAsync(CancellationToken ct = default) =>
            Task.FromResult<IReadOnlyList<EnergyBookingSlot>>(Slots.Values.Where(x => x.IsActive && x.AvailableSlots > 0).ToList());
        public Task<bool> TryLockAsync(string nic, string token, CancellationToken ct = default) => Task.FromResult(true);
        public Task UnlockAsync(string nic, string token, CancellationToken ct = default) => Task.CompletedTask;
        public Task<bool> TryAcquireCapacityAsync(EnergyBookingSlot expected, CancellationToken ct = default) => Task.FromResult(true);
        public Task<bool> ReleaseCapacityAsync(string slotId, CancellationToken ct = default) => Task.FromResult(true);
        public Task InsertAsync(EnergyReservation reservation, CancellationToken ct = default) { Reservations[reservation.ReservationId] = reservation; return Task.CompletedTask; }
        public Task<bool> TryReplaceAsync(EnergyReservation expected, EnergyReservation replacement, CancellationToken ct = default)
        {
            Reservations[replacement.ReservationId] = replacement;
            return Task.FromResult(true);
        }
        public Task<EnergyReservation?> GetByQrHashAsync(string qrTokenHash, CancellationToken ct = default)
        {
            if (string.IsNullOrWhiteSpace(qrTokenHash)) return Task.FromResult<EnergyReservation?>(null);
            return Task.FromResult(Reservations.Values.FirstOrDefault(x => x.QrTokenHash == qrTokenHash));
        }
        public Task<bool> TryUpdateQrHashAsync(string reservationId, string? expectedHash, string newHash, DateTime issuedAtUtc, DateTime updatedAtUtc, CancellationToken ct = default)
        {
            if (!Reservations.TryGetValue(reservationId, out var existing)) return Task.FromResult(false);
            if (existing.Status != ReservationStatus.Approved || existing.QrTokenHash != expectedHash) return Task.FromResult(false);
            existing.QrTokenHash = newHash;
            existing.QrIssuedAtUtc = issuedAtUtc;
            existing.UpdatedAtUtc = updatedAtUtc;
            return Task.FromResult(true);
        }

        public Task<bool> TryCompleteReservationAsync(string reservationId, string qrTokenHash, string operatorNic, DateTime completedAtUtc, DateTime updatedAtUtc, CancellationToken ct = default)
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
        public Task<User?> GetByNicAsync(string nic, CancellationToken cancellationToken = default) => Task.FromResult(Items.GetValueOrDefault(nic));
        public Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default) => Task.FromResult(Items.Values.FirstOrDefault(x => x.Email == email));
        public Task<IReadOnlyList<User>> GetAllAsync(CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<User>>(Items.Values.ToList());
        public Task<IReadOnlyList<User>> GetByStatusAsync(UserStatus status, CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<User>>(Items.Values.Where(x => x.Status == status).ToList());
        public Task InsertAsync(User user, CancellationToken cancellationToken = default) { Items[user.Nic] = user; return Task.CompletedTask; }
        public Task ReplaceAsync(User user, CancellationToken cancellationToken = default) { Items[user.Nic] = user; return Task.CompletedTask; }
    }
}
