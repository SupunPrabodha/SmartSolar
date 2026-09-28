/*
 * File: ReservationService.cs
 * Project: Smart Solar Microgrid Trading System
 * Purpose: Orchestrates authorized reservation lifecycle and conservative standalone write recovery.
 * Note: Keep this header and update method-level comments as the code evolves.
 */
using SmartSolar.Application.Abstractions.Persistence;
using SmartSolar.Application.Abstractions.Reservations;
using SmartSolar.Application.Abstractions.Security;
using SmartSolar.Application.DTOs.Reservations;
using SmartSolar.Application.Exceptions;
using SmartSolar.Domain.Entities;
using SmartSolar.Domain.Enums;

namespace SmartSolar.Application.Services;

public sealed class ReservationService : IReservationService
{
    private readonly IReservationRepository _reservations;
    private readonly IUserRepository _users;
    private readonly IQrSecurityService _qrSecurity;
    private readonly TimeProvider _clock;
    private readonly CatalogWriteGate _gate;

    public ReservationService(
        IReservationRepository reservations,
        IUserRepository users,
        IQrSecurityService qrSecurity,
        TimeProvider clock,
        CatalogWriteGate gate)
    {
        // Keep persistence and security services outside authoritative application policy.
        _reservations = reservations;
        _users = users;
        _qrSecurity = qrSecurity;
        _clock = clock;
        _gate = gate;
    }

    public async Task<ReservationResponse> CreateAsync(string actorNic, CreateReservationRequest request, CancellationToken ct = default)
    {
        // Self-service identity is derived only from the trusted authenticated caller.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.Prosumer) throw new ForbiddenException("Only Prosumers may create their own reservations.");
        return await CreateCoreAsync(actor.Nic, actor.Nic, request, ct);
    }

    public async Task<ReservationResponse> CreateForAsync(string actorNic, string prosumerNic, CreateReservationRequest request, CancellationToken ct = default)
    {
        // Assisted creation is explicit and limited to current active GridOperators.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.GridOperator) throw new ForbiddenException("Only GridOperators may assist another Prosumer.");
        var prosumer = await ProsumerAsync(prosumerNic, ct);
        return await CreateCoreAsync(actor.Nic, prosumer.Nic, request, ct);
    }

    private async Task<ReservationResponse> CreateCoreAsync(string actorNic, string nic, CreateReservationRequest request, CancellationToken ct)
    {
        // Validate request constraints before acquiring any capacity.
        ArgumentNullException.ThrowIfNull(request);
        RequestValidation.EnsureValid(request);
        return await WithLockAsync(nic, async write =>
        {
            var actor = await ActorAsync(actorNic, ct);
            if (actor.Nic != nic && actor.Role != UserRole.GridOperator)
                throw new ForbiddenException("Only GridOperators may assist another Prosumer.");
            await ProsumerAsync(nic, ct);
            var (slot, station) = await ValidSlotAndStationAsync(request.SlotId, ct);
            var now = _clock.GetUtcNow();
            var rules = new ReservationRules(new OperationClock(now));
            rules.ValidateCreate(slot.StartAtUtc, slot.EndAtUtc);
            await EnsureNoOverlapAsync(nic, null, slot.StartAtUtc, slot.EndAtUtc, ct);
            await EnsureStationEnergyCapacityAsync(station, slot.SlotId, request.EnergyAmountKwh, null, ct);
            ct.ThrowIfCancellationRequested();
            write.Uncertain = true;
            if (!await _reservations.TryAcquireCapacityAsync(slot, CancellationToken.None))
            {
                write.Uncertain = false;
                throw new ConflictException("Slot is full, inactive or changed. Refresh and retry.");
            }
            var reservation = new EnergyReservation
            {
                ProsumerNic = nic, StationId = slot.StationId, SlotId = slot.SlotId,
                EnergyAmountKwh = request.EnergyAmountKwh, Status = ReservationStatus.Pending,
                ScheduledStartAtUtc = slot.StartAtUtc, ScheduledEndAtUtc = slot.EndAtUtc,
                CreatedAtUtc = now.UtcDateTime, UpdatedAtUtc = now.UtcDateTime
            };
            // An ambiguous insert must retain capacity and the lock; never guess whether it committed.
            await _reservations.InsertAsync(reservation, CancellationToken.None);
            write.Uncertain = false;
            return Response(reservation);
        }, ct);
    }

    public async Task<IReadOnlyList<ReservationResponse>> ListAsync(string actorNic, ListReservationsRequest request, CancellationToken ct = default)
    {
        // Enforce the operational role independently of MVC; this is not a Prosumer history API.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.GridOperator)
            throw new ForbiddenException("Only GridOperators may list reservations.");
        ArgumentNullException.ThrowIfNull(request);
        RequestValidation.EnsureValid(request);
        var nic = string.IsNullOrWhiteSpace(request.ProsumerNic) ? null : request.ProsumerNic.Trim().ToUpperInvariant();
        var station = string.IsNullOrWhiteSpace(request.StationId) ? null : request.StationId.Trim();
        var reservations = await _reservations.ListAsync(request.Status, nic, station, ct);
        // Preserve the existing fail-closed policy for records missing accepted schedule snapshots.
        return reservations.Select(Response).ToList();
    }

    public async Task<IReadOnlyList<ReservationResponse>> GetMyReservationsAsync(string actorNic, CancellationToken ct = default)
    {
        // Active prosumers can inspect their own reservation history.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.Prosumer)
            throw new ForbiddenException("Only Prosumers may access their own reservations.");

        var reservations = await _reservations.ListAsync(null, actor.Nic, null, ct);
        return reservations.Select(Response).ToList();
    }

    public async Task<IReadOnlyList<AvailableSlotResponse>> GetAvailableSlotsAsync(string actorNic, CancellationToken ct = default)
    {
        // Active Prosumers and GridOperators can inspect available slots for booking.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.Prosumer && actor.Role != UserRole.GridOperator)
            throw new ForbiddenException("Only Prosumers and GridOperators may view available slots.");

        var slots = await _reservations.GetActiveSlotsAsync(ct);
        var now = _clock.GetUtcNow().UtcDateTime;
        var maxHorizon = now.AddDays(7);

        var result = new List<AvailableSlotResponse>();
        foreach (var slot in slots.Where(x => x.IsActive && x.AvailableSlots > 0
                     && x.StartAtUtc > now && x.StartAtUtc <= maxHorizon).OrderBy(x => x.StartAtUtc))
        {
            // Discovery must not offer inventory whose parent cannot accept a booking.
            if (await _reservations.GetStationAsync(slot.StationId, ct) is { IsActive: true })
                result.Add(new(slot.SlotId, slot.StationId, slot.StartAtUtc, slot.EndAtUtc, slot.AvailableSlots, slot.TotalSlots));
        }
        return result;
    }

    public async Task<ReservationResponse> GetAsync(string actorNic, string reservationId, CancellationToken ct = default)
    {
        // Enforce ownership before exposing reservation data or legacy schedule errors.
        var actor = await ActorAsync(actorNic, ct);
        var reservation = await RequiredAsync(reservationId, ct);
        EnsureOwner(actor, reservation);
        return Response(reservation);
    }

    public async Task<ReservationResponse> UpdateAsync(string actorNic, string reservationId, UpdateReservationRequest request, CancellationToken ct = default)
    {
        // Re-read under the Prosumer lock so concurrent updates cannot use a stale schedule.
        ArgumentNullException.ThrowIfNull(request);
        var actor = await ActorAsync(actorNic, ct);
        var initial = await RequiredAsync(reservationId, ct);
        EnsureOwner(actor, initial);
        RequestValidation.EnsureValid(request);
        return await WithLockAsync(initial.ProsumerNic, async write =>
        {
            actor = await ActorAsync(actorNic, ct);
            await ProsumerAsync(initial.ProsumerNic, ct);
            var current = await RequiredAsync(reservationId, ct);
            EnsureOwner(actor, current);
            EnsureSameProsumer(initial, current);
            var accepted = Schedule(current);
            var (slot, station) = await ValidSlotAndStationAsync(request.SlotId, ct);
            var sameSlot = slot.SlotId == current.SlotId;
            if (sameSlot && (slot.StartAtUtc != accepted.Start || slot.EndAtUtc != accepted.End || slot.StationId != current.StationId))
                throw new ConflictException("The accepted slot schedule changed; select a different slot or cancel.");
            var now = _clock.GetUtcNow();
            var status = new ReservationRules(new OperationClock(now)).ValidateUpdate(
                current.Status, accepted.Start, slot.StartAtUtc, slot.EndAtUtc);
            await EnsureNoOverlapAsync(current.ProsumerNic, current.ReservationId, slot.StartAtUtc, slot.EndAtUtc, ct);
            await EnsureStationEnergyCapacityAsync(station, slot.SlotId, request.EnergyAmountKwh, current.ReservationId, ct);
            ct.ThrowIfCancellationRequested();
            write.Uncertain = true;
            if (!sameSlot && !await _reservations.TryAcquireCapacityAsync(slot, CancellationToken.None))
            {
                write.Uncertain = false;
                throw new ConflictException("Replacement slot is full, inactive or changed.");
            }
            var replacement = new EnergyReservation
            {
                ReservationId = current.ReservationId, ProsumerNic = current.ProsumerNic,
                StationId = slot.StationId, SlotId = slot.SlotId, EnergyAmountKwh = request.EnergyAmountKwh,
                Status = status, QrToken = null, QrTokenHash = null, QrIssuedAtUtc = null,
                ScheduledStartAtUtc = slot.StartAtUtc,
                ScheduledEndAtUtc = slot.EndAtUtc, CreatedAtUtc = current.CreatedAtUtc, UpdatedAtUtc = now.UtcDateTime
            };
            if (!await _reservations.TryReplaceAsync(current, replacement, CancellationToken.None))
            {
                // A confirmed CAS miss is safe to compensate; an exception is not.
                if (!sameSlot) await ReleaseAsync(slot.SlotId);
                write.Uncertain = false;
                throw new ConflictException("Reservation changed concurrently. Refresh and retry.");
            }
            if (!sameSlot) await ReleaseAsync(current.SlotId);
            write.Uncertain = false;
            return Response(replacement);
        }, ct);
    }

    public async Task<ReservationResponse> CancelAsync(string actorNic, string reservationId, CancellationToken ct = default)
    {
        // Commit cancellation once before releasing capacity; failed writes retain the recovery lock.
        var actor = await ActorAsync(actorNic, ct);
        var initial = await RequiredAsync(reservationId, ct);
        EnsureOwner(actor, initial);
        return await WithLockAsync(initial.ProsumerNic, async write =>
        {
            actor = await ActorAsync(actorNic, ct);
            var current = await RequiredAsync(reservationId, ct);
            EnsureOwner(actor, current);
            EnsureSameProsumer(initial, current);
            var accepted = Schedule(current);
            var now = _clock.GetUtcNow();
            var status = new ReservationRules(new OperationClock(now)).ValidateCancellation(current.Status, accepted.Start);
            var replacement = new EnergyReservation
            {
                ReservationId = current.ReservationId, ProsumerNic = current.ProsumerNic,
                StationId = current.StationId, SlotId = current.SlotId, EnergyAmountKwh = current.EnergyAmountKwh,
                Status = status, QrToken = null, QrTokenHash = null, QrIssuedAtUtc = null,
                ScheduledStartAtUtc = accepted.Start, ScheduledEndAtUtc = accepted.End,
                CreatedAtUtc = current.CreatedAtUtc, UpdatedAtUtc = now.UtcDateTime
            };
            ct.ThrowIfCancellationRequested();
            write.Uncertain = true;
            if (!await _reservations.TryReplaceAsync(current, replacement, CancellationToken.None))
            {
                write.Uncertain = false;
                throw new ConflictException("Reservation changed concurrently. Refresh and retry.");
            }
            await ReleaseAsync(current.SlotId);
            write.Uncertain = false;
            return Response(replacement);
        }, ct);
    }

    public async Task<ReservationResponse> ApproveAsync(
        string actorNic,
        string reservationId,
        CancellationToken ct = default)
    {
        // Only active GridOperators can approve reservations.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.GridOperator)
            throw new ForbiddenException("Only GridOperators may approve reservations.");

        var initial = await RequiredAsync(reservationId, ct);
        return await WithLockAsync(initial.ProsumerNic, async write =>
        {
            actor = await ActorAsync(actorNic, ct);
            if (actor.Role != UserRole.GridOperator)
                throw new ForbiddenException("Only GridOperators may approve reservations.");

            var current = await RequiredAsync(reservationId, ct);
            EnsureSameProsumer(initial, current);
            var accepted = Schedule(current);
            var now = _clock.GetUtcNow();
            var status = new ReservationRules(new OperationClock(now))
                .ValidateApproval(current.Status, accepted.Start);
            await EnsureRelatedEligibilityAsync(current, ct);

            var replacement = new EnergyReservation
            {
                ReservationId = current.ReservationId,
                ProsumerNic = current.ProsumerNic,
                StationId = current.StationId,
                SlotId = current.SlotId,
                EnergyAmountKwh = current.EnergyAmountKwh,
                Status = status,
                QrToken = null,
                RejectionRemark = null,
                ScheduledStartAtUtc = accepted.Start,
                ScheduledEndAtUtc = accepted.End,
                CreatedAtUtc = current.CreatedAtUtc,
                UpdatedAtUtc = now.UtcDateTime
            };

            ct.ThrowIfCancellationRequested();
            write.Uncertain = true;

            if (!await _reservations.TryReplaceAsync(
                    current,
                    replacement,
                    CancellationToken.None))
            {
                write.Uncertain = false;
                throw new ConflictException(
                    "Reservation changed concurrently. Refresh and retry.");
            }

            write.Uncertain = false;
            return Response(replacement);
        }, ct);
    }

    public async Task<ReservationResponse> RejectAsync(
        string actorNic,
        string reservationId,
        RejectReservationRequest request,
        CancellationToken ct = default)
    {
        // Only active GridOperators can reject reservations with a mandatory remark.
        ArgumentNullException.ThrowIfNull(request);
        RequestValidation.EnsureValid(request);

        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.GridOperator)
            throw new ForbiddenException("Only GridOperators may reject reservations.");

        var initial = await RequiredAsync(reservationId, ct);
        return await WithLockAsync(initial.ProsumerNic, async write =>
        {
            actor = await ActorAsync(actorNic, ct);
            if (actor.Role != UserRole.GridOperator)
                throw new ForbiddenException("Only GridOperators may reject reservations.");

            var current = await RequiredAsync(reservationId, ct);
            EnsureSameProsumer(initial, current);
            var accepted = Schedule(current);
            var now = _clock.GetUtcNow();
            var status = new ReservationRules(new OperationClock(now))
                .ValidateRejection(current.Status, request.Remark);

            var replacement = new EnergyReservation
            {
                ReservationId = current.ReservationId,
                ProsumerNic = current.ProsumerNic,
                StationId = current.StationId,
                SlotId = current.SlotId,
                EnergyAmountKwh = current.EnergyAmountKwh,
                Status = status,
                QrToken = null,
                RejectionRemark = request.Remark.Trim(),
                ScheduledStartAtUtc = accepted.Start,
                ScheduledEndAtUtc = accepted.End,
                CreatedAtUtc = current.CreatedAtUtc,
                UpdatedAtUtc = now.UtcDateTime
            };

            ct.ThrowIfCancellationRequested();
            write.Uncertain = true;

            if (!await _reservations.TryReplaceAsync(
                    current,
                    replacement,
                    CancellationToken.None))
            {
                write.Uncertain = false;
                throw new ConflictException(
                    "Reservation changed concurrently. Refresh and retry.");
            }

            await ReleaseAsync(current.SlotId);
            write.Uncertain = false;
            return Response(replacement);
        }, ct);
    }

    public Task<ReservationQrResponse> IssueQrAsync(string actorNic, string reservationId, CancellationToken ct = default)
    {
        // Serialize related-record validation and QR writes with catalog/lifecycle mutations.
        return WithCatalogAsync(() => IssueQrCoreAsync(actorNic, reservationId, ct), ct);
    }

    private async Task<ReservationQrResponse> IssueQrCoreAsync(string actorNic, string reservationId, CancellationToken ct = default)
    {
        // Only the owning active Prosumer may issue or rotate the reference.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.Prosumer)
            throw new ForbiddenException("Only the owning Prosumer may issue a reservation QR.");
        var reservation = await RequiredAsync(reservationId, ct);
        EnsureOwner(actor, reservation);

        if (reservation.Status != ReservationStatus.Approved)
        {
            throw new ConflictException($"Only Approved reservations can produce a transaction QR code. Current status is '{reservation.Status}'.");
        }

        // Validate accepted schedule snapshot.
        _ = Schedule(reservation);

        var now = _clock.GetUtcNow();
        var qrPayload = _qrSecurity.GeneratePayload();
        var qrHash = _qrSecurity.ComputeHash(qrPayload);

        // Reissue/rotate QR reference by replacing previous stored hash atomically.
        var updated = await _reservations.TryUpdateQrHashAsync(
            reservation.ReservationId,
            reservation.QrTokenHash,
            qrHash,
            now.UtcDateTime,
            now.UtcDateTime,
            ct);

        if (!updated)
        {
            throw new ConflictException("Reservation was modified concurrently. Refresh and retry.");
        }

        return new ReservationQrResponse(reservation.ReservationId, qrPayload, now.UtcDateTime);
    }

    public Task<ReservationVerificationResponse> VerifyQrAsync(string actorNic, VerifyReservationQrRequest request, CancellationToken ct = default)
    {
        // Serialize related-record validation and QR writes with catalog/lifecycle mutations.
        return WithCatalogAsync(() => VerifyQrCoreAsync(actorNic, request, ct), ct);
    }

    private async Task<ReservationVerificationResponse> VerifyQrCoreAsync(string actorNic, VerifyReservationQrRequest request, CancellationToken ct = default)
    {
        // QR verification is reserved exclusively for active GridOperators.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.GridOperator)
        {
            throw new ForbiddenException("Only GridOperators may verify reservation QR codes.");
        }

        ArgumentNullException.ThrowIfNull(request);
        RequestValidation.EnsureValid(request);

        if (!_qrSecurity.IsValidPayloadFormat(request.QrPayload))
        {
            throw new BadRequestException("The provided QR payload is malformed or uses an unsupported format.");
        }

        var qrHash = _qrSecurity.ComputeHash(request.QrPayload);
        var reservation = await _reservations.GetByQrHashAsync(qrHash, ct);

        if (reservation is null)
        {
            throw new NotFoundException("QR code reference is invalid, expired, or was revoked by reissuance.");
        }

        if (reservation.Status != ReservationStatus.Approved)
        {
            throw new ConflictException($"Reservation is not in an Approved state (current status: '{reservation.Status}').");
        }

        var schedule = await EligibleTransferAsync(reservation, _clock.GetUtcNow().UtcDateTime, ct);

        return new ReservationVerificationResponse(
            reservation.ReservationId,
            reservation.ProsumerNic,
            reservation.StationId,
            reservation.SlotId,
            reservation.EnergyAmountKwh,
            schedule.Start,
            schedule.End,
            reservation.Status,
            reservation.QrIssuedAtUtc,
            EligibleForCompletion: true
        );
    }

    public Task<ReservationCompletionResponse> CompleteTransferAsync(string actorNic, CompleteReservationTransferRequest request, CancellationToken ct = default)
    {
        // Serialize related-record validation and QR writes with catalog/lifecycle mutations.
        return WithCatalogAsync(() => CompleteTransferCoreAsync(actorNic, request, ct), ct);
    }

    private async Task<ReservationCompletionResponse> CompleteTransferCoreAsync(string actorNic, CompleteReservationTransferRequest request, CancellationToken ct = default)
    {
        // Transaction completion is strictly restricted to active GridOperators.
        var actor = await ActorAsync(actorNic, ct);
        if (actor.Role != UserRole.GridOperator)
        {
            throw new ForbiddenException("Only GridOperators may complete energy transfer reservations.");
        }

        ArgumentNullException.ThrowIfNull(request);
        RequestValidation.EnsureValid(request);

        if (!_qrSecurity.IsValidPayloadFormat(request.QrPayload))
        {
            throw new BadRequestException("The provided QR payload is malformed or uses an unsupported format.");
        }

        var qrHash = _qrSecurity.ComputeHash(request.QrPayload);
        var reservation = await _reservations.GetByQrHashAsync(qrHash, ct);

        if (reservation is null)
        {
            if (!string.IsNullOrWhiteSpace(request.ReservationId))
            {
                var byId = await _reservations.GetAsync(request.ReservationId, ct);
                if (byId is not null)
                {
                    if (byId.Status == ReservationStatus.Completed)
                        throw new ConflictException("This reservation has already been completed.");
                    if (byId.Status != ReservationStatus.Approved)
                        throw new ConflictException($"Reservation is not in an Approved state (current status: '{byId.Status}').");
                }
            }
            throw new NotFoundException("QR code reference is invalid, expired, or was revoked by reissuance.");
        }

        if (!string.IsNullOrWhiteSpace(request.ReservationId) && reservation.ReservationId != request.ReservationId)
        {
            throw new ConflictException("QR reference does not match the specified reservation.");
        }

        if (reservation.Status == ReservationStatus.Completed)
        {
            throw new ConflictException("This reservation has already been completed.");
        }

        if (reservation.Status != ReservationStatus.Approved)
        {
            throw new ConflictException($"Reservation is not in an Approved state (current status: '{reservation.Status}').");
        }

        var now = _clock.GetUtcNow();
        var schedule = await EligibleTransferAsync(reservation, now.UtcDateTime, ct);

        // Completed consumes its published booking place; do not release AvailableSlots.
        // Atomically transitions from Approved to Completed in MongoDB; single execution guaranteed.
        var completed = await _reservations.TryCompleteReservationAsync(
            reservation.ReservationId,
            qrHash,
            actor.Nic,
            now.UtcDateTime,
            now.UtcDateTime,
            ct);

        if (!completed)
        {
            var current = await _reservations.GetAsync(reservation.ReservationId, ct);
            if (current?.Status == ReservationStatus.Completed)
            {
                throw new ConflictException("This reservation has already been completed.");
            }
            throw new ConflictException("Reservation status changed concurrently or is no longer eligible for completion.");
        }

        return new ReservationCompletionResponse(
            reservation.ReservationId,
            reservation.ProsumerNic,
            reservation.StationId,
            reservation.SlotId,
            reservation.EnergyAmountKwh,
            schedule.Start,
            schedule.End,
            ReservationStatus.Completed,
            now.UtcDateTime,
            actor.Nic,
            now.UtcDateTime
        );
    }

    private async Task<(DateTime Start, DateTime End)> EligibleTransferAsync(
        EnergyReservation reservation, DateTime now, CancellationToken ct)
    {
        // Accepted snapshots, never mutable slot times, determine the transfer window.
        var schedule = Schedule(reservation);
        if (now < schedule.Start || now >= schedule.End)
            throw new ConflictException("Transfer verification and completion require the accepted scheduled window.");
        await EnsureRelatedEligibilityAsync(reservation, ct);
        return schedule;
    }

    private async Task EnsureRelatedEligibilityAsync(EnergyReservation reservation, CancellationToken ct)
    {
        // Revalidate related identity and activity before approval or a transfer operation.
        var prosumer = await _users.GetByNicAsync(reservation.ProsumerNic, ct);
        if (prosumer is null || prosumer.Role != UserRole.Prosumer || prosumer.Status != UserStatus.Active)
            throw new ConflictException("The reservation requires an active Prosumer.");
        var station = await _reservations.GetStationAsync(reservation.StationId, ct);
        var slot = await _reservations.GetSlotAsync(reservation.SlotId, ct);
        if (station is null || !station.IsActive || slot is null || !slot.IsActive
            || slot.StationId != reservation.StationId)
            throw new ConflictException("The reservation station/slot linkage is missing, inactive or invalid.");
    }

    private async Task<T> WithCatalogAsync<T>(Func<Task<T>> action, CancellationToken ct)
    {
        // Supported deployment: one API instance, one singleton gate; always acquire it first.
        await _gate.Mutex.WaitAsync(ct);
        try { return await action(); }
        finally { _gate.Mutex.Release(); }
    }

    private Task<T> WithLockAsync<T>(string nic, Func<WriteState, Task<T>> action, CancellationToken ct)
    {
        // Lock order is catalog gate -> durable Prosumer lock; release in reverse order.
        return WithCatalogAsync(() => WithProsumerLockAsync(nic, action, ct), ct);
    }

    private async Task<T> WithProsumerLockAsync<T>(string nic, Func<WriteState, Task<T>> action, CancellationToken ct)
    {
        // Locks never expire automatically: a crashed writer must not overlap a replacement writer.
        var token = Guid.NewGuid().ToString("N");
        if (!await _reservations.TryLockAsync(nic, token, ct))
            throw new ConflictException("A reservation operation is in progress or requires reconciliation.");
        var state = new WriteState();
        try
        {
            return await action(state);
        }
        catch (Exception) when (state.Uncertain)
        {
            // Keep the durable lock and conservative capacity on any ambiguous mutation outcome.
            throw new ConflictException("Reservation write outcome requires reconciliation before further changes.");
        }
        finally
        {
            // After writes begin, finish without client cancellation to avoid abandoning compensation.
            if (!state.Uncertain)
                await _reservations.UnlockAsync(nic, token, CancellationToken.None);
        }
    }

    private async Task<User> ActorAsync(string nic, CancellationToken ct)
    {
        // Reload current server account state; caller identity alone grants no authority.
        if (string.IsNullOrWhiteSpace(nic)) throw new UnauthorizedException("Authentication is required.");
        var user = await _users.GetByNicAsync(nic.Trim().ToUpperInvariant(), ct);
        if (user is null) throw new UnauthorizedException("Authenticated account is unavailable.");
        if (user.Status != UserStatus.Active || user.Role is not (UserRole.Prosumer or UserRole.GridOperator))
            throw new ForbiddenException("An active Prosumer or GridOperator account is required.");
        return user;
    }

    private async Task<User> ProsumerAsync(string nic, CancellationToken ct)
    {
        // Assisted booking cannot target staff or inactive accounts.
        if (string.IsNullOrWhiteSpace(nic)) throw new BadRequestException("Prosumer NIC is required.");
        var user = await _users.GetByNicAsync(nic.Trim().ToUpperInvariant(), ct)
            ?? throw new NotFoundException("Prosumer not found.");
        if (user.Role != UserRole.Prosumer || user.Status != UserStatus.Active)
            throw new ForbiddenException("An active Prosumer account is required.");
        return user;
    }

    private static void EnsureOwner(User actor, EnergyReservation reservation)
    {
        // Backoffice is intentionally not granted reservation operational access.
        if (actor.Role != UserRole.GridOperator && actor.Nic != reservation.ProsumerNic)
            throw new ForbiddenException("This reservation belongs to another Prosumer.");
    }

    private static void EnsureSameProsumer(EnergyReservation initial, EnergyReservation current)
    {
        // Identity changes must not allow a write under the wrong Prosumer lock.
        if (initial.ProsumerNic != current.ProsumerNic)
            throw new ConflictException("Reservation ownership changed.");
    }

    private async Task<EnergyReservation> RequiredAsync(string id, CancellationToken ct)
    {
        // Preserve string identifiers and convert missing records to the common error contract.
        return await _reservations.GetAsync(id, ct) ?? throw new NotFoundException("Reservation not found.");
    }

    private async Task<(EnergyBookingSlot Slot, SolarStation Station)> ValidSlotAndStationAsync(string id, CancellationToken ct)
    {
        // Resolve station exclusively through the persisted slot reference.
        var slot = await _reservations.GetSlotAsync(id, ct) ?? throw new NotFoundException("Slot not found.");
        if (!slot.IsActive) throw new ConflictException("Slot is inactive.");
        var station = await _reservations.GetStationAsync(slot.StationId, ct) ?? throw new NotFoundException("Station not found.");
        if (!station.IsActive) throw new ConflictException("Station is inactive.");
        if (station.CapacityKwh <= 0)
            throw new ConflictException("Station energy capacity is invalid and requires repair.");
        if (slot.TotalSlots <= 0 || slot.AvailableSlots < 0 || slot.AvailableSlots > slot.TotalSlots)
            throw new ConflictException("Slot capacity is invalid and requires repair.");
        return (slot, station);
    }

    private async Task EnsureStationEnergyCapacityAsync(
        SolarStation station, string slotId, decimal requestedKwh, string? excludedReservationId, CancellationToken ct)
    {
        if (requestedKwh > station.CapacityKwh)
            throw new BadRequestException($"Requested energy amount ({requestedKwh} kWh) exceeds station capacity ({station.CapacityKwh} kWh).");

        var activeOnSlot = await _reservations.GetActiveBySlotAsync(slotId, ct);
        var allocatedKwh = activeOnSlot
            .Where(r => r.ReservationId != excludedReservationId)
            .Sum(r => r.EnergyAmountKwh);

        if (allocatedKwh + requestedKwh > station.CapacityKwh)
        {
            var remaining = station.CapacityKwh - allocatedKwh;
            throw new ConflictException($"The requested energy amount ({requestedKwh} kWh) exceeds the station's available energy capacity for this slot ({remaining} kWh remaining out of {station.CapacityKwh} kWh).");
        }
    }

    private async Task EnsureNoOverlapAsync(string nic, string? excludedId, DateTime start, DateTime end, CancellationToken ct)
    {
        // The durable Prosumer lock protects this read-check-write sequence across API instances.
        foreach (var existing in await _reservations.GetActiveAsync(nic, ct))
        {
            if (existing.ReservationId == excludedId) continue;
            var schedule = Schedule(existing);
            if (ReservationRules.Conflicts(existing.Status, schedule.Start, schedule.End, start, end))
                throw new ConflictException("An active reservation overlaps the requested slot.");
        }
    }

    private async Task ReleaseAsync(string slotId)
    {
        // Never release beyond TotalSlots; a failed release leaves the user locked for repair.
        if (!await _reservations.ReleaseCapacityAsync(slotId, CancellationToken.None))
            throw new ConflictException("Slot capacity requires reconciliation.");
    }

    private static (DateTime Start, DateTime End) Schedule(EnergyReservation reservation)
    {
        // Legacy reservations need a verified backfill, not a guess from a mutable slot.
        if (reservation.ScheduledStartAtUtc is not DateTime start || reservation.ScheduledEndAtUtc is not DateTime end
            || start.Kind != DateTimeKind.Utc || end.Kind != DateTimeKind.Utc || end <= start)
            throw new ConflictException("Reservation schedule requires verified backfill before use.");
        return (start, end);
    }

    private static ReservationResponse Response(EnergyReservation reservation)
    {
        // Return only the reviewed summary contract, never internal lock or QR data.
        var schedule = Schedule(reservation);
        return new ReservationResponse(reservation.ReservationId, reservation.ProsumerNic,
            reservation.StationId, reservation.SlotId, reservation.EnergyAmountKwh,
            schedule.Start, schedule.End, reservation.Status, reservation.CreatedAtUtc, reservation.UpdatedAtUtc,
            reservation.RejectionRemark);
    }

    private sealed class WriteState
    {
        public bool Uncertain { get; set; }
    }

    private sealed class OperationClock(DateTimeOffset now) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow()
        {
            // Use the same captured server instant for all rules and timestamps of one operation.
            return now;
        }
    }
}
