/*
 * File: ReservationRules.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Provides testable reservation policy without orchestration or persistence.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Services;

public sealed class ReservationRules
{
    private readonly TimeProvider _clock;

    public ReservationRules(TimeProvider clock)
    {
        // Require explicit clock injection so tests never depend on wall-clock time.
        _clock = clock ?? throw new ArgumentNullException(nameof(clock));
    }

    public void ValidateCreate(DateTime startAtUtc, DateTime endAtUtc)
    {
        // Capture one server instant for the complete booking-window decision.
        ValidateBookingWindow(startAtUtc, endAtUtc, _clock.GetUtcNow().UtcDateTime);
    }

    public ReservationStatus ValidateUpdate(
        ReservationStatus status, DateTime existingStartAtUtc, DateTime newStartAtUtc, DateTime newEndAtUtc)
    {
        // Protect the accepted schedule cutoff and require reapproval after any update.
        var now = _clock.GetUtcNow().UtcDateTime;
        EnsureActive(status);
        EnsureNotice(existingStartAtUtc, now);
        ValidateBookingWindow(newStartAtUtc, newEndAtUtc, now);
        EnsureNotice(newStartAtUtc, now);
        return ReservationStatus.Pending;
    }

    public ReservationStatus ValidateCancellation(ReservationStatus status, DateTime scheduledStartAtUtc)
    {
        // Cancellation has the same inclusive notice cutoff for both active states.
        var now = _clock.GetUtcNow().UtcDateTime;
        EnsureActive(status);
        EnsureNotice(scheduledStartAtUtc, now);
        return ReservationStatus.Cancelled;
    }

    public ReservationStatus ValidateApproval(ReservationStatus status, DateTime scheduledStartAtUtc)
    {
        // Approval is permitted only for Pending reservations whose start time is in the future.
        var now = _clock.GetUtcNow().UtcDateTime;
        if (status != ReservationStatus.Pending)
            throw new ConflictException("Only Pending reservations may be approved.");
        EnsureUtc(scheduledStartAtUtc);
        if (scheduledStartAtUtc <= now)
            throw new ConflictException("Cannot approve an expired or past reservation.");
        return ReservationStatus.Approved;
    }

    public ReservationStatus ValidateRejection(ReservationStatus status, string remark)
    {
        // Rejection is permitted only for Pending reservations with a mandatory non-empty remark.
        if (status != ReservationStatus.Pending)
            throw new ConflictException("Only Pending reservations may be rejected.");
        if (string.IsNullOrWhiteSpace(remark))
            throw new BadRequestException("Rejection remark is required.");
        return ReservationStatus.Rejected;
    }

    public static bool Conflicts(
        ReservationStatus existingStatus, DateTime existingStartAtUtc, DateTime existingEndAtUtc,
        DateTime requestedStartAtUtc, DateTime requestedEndAtUtc)
    {
        // The caller scopes records to the same Prosumer and excludes the updated reservation.
        EnsureSchedule(existingStartAtUtc, existingEndAtUtc);
        EnsureSchedule(requestedStartAtUtc, requestedEndAtUtc);
        return existingStatus is ReservationStatus.Pending or ReservationStatus.Approved
            && existingStartAtUtc < requestedEndAtUtc && requestedStartAtUtc < existingEndAtUtc;
    }

    private static void ValidateBookingWindow(DateTime start, DateTime end, DateTime now)
    {
        // The lower boundary is exclusive and seven elapsed UTC days is inclusive.
        EnsureSchedule(start, end);
        var untilStart = start - now;
        if (untilStart <= TimeSpan.Zero || untilStart > TimeSpan.FromDays(7))
            throw new BadRequestException("Reservation start must be in the future and within seven days.");
    }

    private static void EnsureNotice(DateTime start, DateTime now)
    {
        // Exactly twelve elapsed hours is permitted, with no rounding or timezone conversion.
        EnsureUtc(start);
        if (start - now < TimeSpan.FromHours(12))
            throw new ConflictException("Reservation changes require at least twelve hours of notice.");
    }

    private static void EnsureActive(ReservationStatus status)
    {
        // Reject terminal and unrecognized states rather than reopening a reservation.
        if (status is not (ReservationStatus.Pending or ReservationStatus.Approved))
            throw new ConflictException("Only Pending or Approved reservations may be changed.");
    }

    private static void EnsureSchedule(DateTime start, DateTime end)
    {
        // Persisted slot intervals must be UTC and have positive duration.
        EnsureUtc(start);
        EnsureUtc(end);
        if (end <= start)
            throw new BadRequestException("Reservation end must be after its start.");
    }

    private static void EnsureUtc(DateTime value)
    {
        // Ambiguous server timestamps must be corrected at their source.
        if (value.Kind != DateTimeKind.Utc)
            throw new BadRequestException("Reservation schedules must use UTC timestamps.");
    }
}
