/*
 * File: ReservationRulesTests.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Defines deterministic Member 3 timing, overlap and transition expectations.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Application.Exceptions;
using SmartSolar.Application.Services;
using SmartSolar.Domain.Enums;
using Xunit;

namespace SmartSolar.UnitTests;

public sealed class ReservationRulesTests
{
    private static readonly DateTime Now = new(2030, 1, 1, 0, 0, 0, DateTimeKind.Utc);
    private readonly ReservationRules _rules = new(new FixedClock());

    [Theory]
    [InlineData(1)]
    [InlineData(604800)]
    public void CreateAllowsFutureThroughExactlySevenDays(int seconds)
    {
        // Both a near-future booking and the inclusive upper boundary are valid.
        var start = Now.AddSeconds(seconds);
        _rules.ValidateCreate(start, start.AddHours(1));
    }

    [Theory]
    [InlineData(-1)]
    [InlineData(0)]
    [InlineData(604801)]
    public void CreateRejectsPastCurrentAndBeyondSevenDays(int seconds)
    {
        // Reject both the exclusive lower boundary and one second beyond the horizon.
        var start = Now.AddSeconds(seconds);
        Assert.Throws<BadRequestException>(() => _rules.ValidateCreate(start, start.AddHours(1)));
    }

    [Theory]
    [InlineData(ReservationStatus.Pending)]
    [InlineData(ReservationStatus.Approved)]
    public void UpdateAtExactlyTwelveHoursReturnsPending(ReservationStatus status)
    {
        // Approved changes require reapproval; the current schedule controls the cutoff.
        var start = Now.AddHours(12);
        Assert.Equal(ReservationStatus.Pending, _rules.ValidateUpdate(status, start, start, start.AddHours(1)));
    }

    [Fact]
    public void UpdateBelowTwelveHoursRejectsEvenWhenMovingLater()
    {
        // Moving to a later slot cannot bypass the existing reservation cutoff.
        Assert.Throws<ConflictException>(() => _rules.ValidateUpdate(
            ReservationStatus.Pending, Now.AddSeconds(43199), Now.AddDays(2), Now.AddDays(2).AddHours(1)));
    }

    [Fact]
    public void UpdateRejectsReplacementBelowTwelveHours()
    {
        // A permitted existing reservation cannot move into the near-term cutoff.
        Assert.Throws<ConflictException>(() => _rules.ValidateUpdate(
            ReservationStatus.Pending, Now.AddDays(2), Now.AddSeconds(43199), Now.AddHours(13)));
    }

    [Fact]
    public void UpdateRejectsReplacementBeyondSevenDays()
    {
        // Replacement schedules obey the same booking horizon as creation.
        Assert.Throws<BadRequestException>(() => _rules.ValidateUpdate(
            ReservationStatus.Pending, Now.AddDays(2), Now.AddDays(7).AddSeconds(1), Now.AddDays(8)));
    }

    [Theory]
    [InlineData(ReservationStatus.Pending)]
    [InlineData(ReservationStatus.Approved)]
    public void CancelAtExactlyTwelveHoursReturnsCancelled(ReservationStatus status)
    {
        // Both active states can be cancelled at the inclusive notice boundary.
        Assert.Equal(ReservationStatus.Cancelled, _rules.ValidateCancellation(status, Now.AddHours(12)));
    }

    [Fact]
    public void CancelBelowTwelveHoursRejects()
    {
        // One second below the minimum notice must fail.
        Assert.Throws<ConflictException>(() => _rules.ValidateCancellation(ReservationStatus.Pending, Now.AddSeconds(43199)));
    }

    [Theory]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    [InlineData(ReservationStatus.Completed)]
    [InlineData((ReservationStatus)999)]
    public void TerminalOrUnknownStatesRejectUpdateAndCancellation(ReservationStatus status)
    {
        // Terminal and unknown states must never reopen through Member 3 operations.
        var start = Now.AddDays(2);
        Assert.Throws<ConflictException>(() => _rules.ValidateUpdate(status, start, start, start.AddHours(1)));
        Assert.Throws<ConflictException>(() => _rules.ValidateCancellation(status, start));
    }

    [Fact]
    public void ApprovePendingInFutureReturnsApproved()
    {
        // Approval is permitted only for Pending reservations whose start time is in the future.
        var start = Now.AddHours(2);
        Assert.Equal(ReservationStatus.Approved, _rules.ValidateApproval(ReservationStatus.Pending, start));
    }

    [Theory]
    [InlineData(ReservationStatus.Approved)]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    [InlineData(ReservationStatus.Completed)]
    public void ApproveNonPendingRejects(ReservationStatus status)
    {
        // Approving a non-Pending reservation must fail with ConflictException.
        var start = Now.AddHours(2);
        Assert.Throws<ConflictException>(() => _rules.ValidateApproval(status, start));
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public void ApproveExpiredOrPastScheduleRejects(int seconds)
    {
        // Approving an expired or current schedule must fail with ConflictException.
        var start = Now.AddSeconds(seconds);
        Assert.Throws<ConflictException>(() => _rules.ValidateApproval(ReservationStatus.Pending, start));
    }

    [Fact]
    public void RejectPendingWithRemarkReturnsRejected()
    {
        // Rejection is permitted for Pending reservations with a non-empty remark.
        Assert.Equal(ReservationStatus.Rejected, _rules.ValidateRejection(ReservationStatus.Pending, "Insufficient solar generation."));
    }

    [Theory]
    [InlineData(ReservationStatus.Approved)]
    [InlineData(ReservationStatus.Rejected)]
    [InlineData(ReservationStatus.Cancelled)]
    [InlineData(ReservationStatus.Completed)]
    public void RejectNonPendingRejects(ReservationStatus status)
    {
        // Rejecting a non-Pending reservation must fail with ConflictException.
        Assert.Throws<ConflictException>(() => _rules.ValidateRejection(status, "Some remark"));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    public void RejectWithoutRemarkRejects(string? remark)
    {
        // Rejecting without a remark must fail with BadRequestException.
        Assert.Throws<BadRequestException>(() => _rules.ValidateRejection(ReservationStatus.Pending, remark!));
    }

    [Theory]
    [InlineData(ReservationStatus.Pending, true)]
    [InlineData(ReservationStatus.Approved, true)]
    [InlineData(ReservationStatus.Rejected, false)]
    [InlineData(ReservationStatus.Cancelled, false)]
    [InlineData(ReservationStatus.Completed, false)]
    public void OnlyActiveReservationsConflict(ReservationStatus status, bool expected)
    {
        // An overlapping interval consumes availability only in an active status.
        Assert.Equal(expected, ReservationRules.Conflicts(status, Now.AddHours(10), Now.AddHours(11),
            Now.AddHours(10.5), Now.AddHours(11.5)));
    }

    [Theory]
    [InlineData(9, 10, false)]
    [InlineData(11, 12, false)]
    [InlineData(9, 12, true)]
    [InlineData(10, 11, true)]
    public void OverlapAllowsAdjacencyAndRejectsContainment(int start, int end, bool expected)
    {
        // Intervals are half-open, so touching endpoints do not overlap.
        Assert.Equal(expected, ReservationRules.Conflicts(ReservationStatus.Pending,
            Now.AddHours(10), Now.AddHours(11), Now.AddHours(start), Now.AddHours(end)));
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public void InvalidScheduleDurationRejects(int hours)
    {
        // Empty and reversed schedules are invalid server data for a booking.
        var start = Now.AddDays(1);
        Assert.Throws<BadRequestException>(() => _rules.ValidateCreate(start, start.AddHours(hours)));
    }

    [Theory]
    [InlineData(DateTimeKind.Local)]
    [InlineData(DateTimeKind.Unspecified)]
    public void NonUtcScheduleRejects(DateTimeKind kind)
    {
        // Do not silently interpret ambiguous timestamps using the host timezone.
        var start = DateTime.SpecifyKind(Now.AddDays(1), kind);
        Assert.Throws<BadRequestException>(() => _rules.ValidateCreate(start, start.AddHours(1)));
    }

    [Fact]
    public void EachOperationReadsInjectedClockOnce()
    {
        // A single instant is shared by old-slot and replacement-slot checks.
        var clock = new CountingClock();
        var rules = new ReservationRules(clock);
        rules.ValidateCreate(Now.AddDays(1), Now.AddDays(1).AddHours(1));
        rules.ValidateUpdate(ReservationStatus.Pending, Now.AddDays(1), Now.AddDays(1), Now.AddDays(1).AddHours(1));
        rules.ValidateCancellation(ReservationStatus.Pending, Now.AddDays(1));
        Assert.Equal(3, clock.Reads);
    }

    private sealed class FixedClock : TimeProvider
    {
        public override DateTimeOffset GetUtcNow()
        {
            // All rule tests use a fixed instant independent of the real clock.
            return new DateTimeOffset(Now);
        }
    }

    private sealed class CountingClock : TimeProvider
    {
        public int Reads { get; private set; }
        public override DateTimeOffset GetUtcNow()
        {
            // Detect extra time reads that could split an exact boundary decision.
            Reads++;
            return new DateTimeOffset(Now);
        }
    }
}
