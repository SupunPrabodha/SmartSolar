# Business Rules

Authoritative validation must be implemented in the C# service layer.

## Implemented common foundation

- Registration creates a Prosumer in PendingActivation; Backoffice activation is required before login.
- Only Active accounts receive tokens. Every authenticated request checks current stored status/role.
- Backoffice controls activation/reactivation and staff creation. Prosumer self-deactivation is preserved from the starter.
- NIC is immutable and normalized; normalized email and NIC must be unique. Passwords are stored only as salted one-way hashes.
- Staff role is explicitly required and limited to Backoffice or GridOperator. User profile changes cannot change identity, role or account status.

## Assignment rules reserved for later feature packages

The table below preserves the starter contract. Station deactivation, reservation timing, Maps/QR and transaction behavior are **not implemented or certified in Phase 0**.

| Rule | Server responsibility |
|---|---|
| Prosumer identity | NIC is the business identifier. |
| Reactivation | Deactivated accounts can only be reactivated by Backoffice. |
| Node deactivation | Reject deactivation when active energy reservations exist. |
| Reservation horizon | A reservation must be scheduled within 7 days. |
| Reservation update | Require at least 12 hours' notice. |
| Reservation cancellation | Require at least 12 hours' notice. |
| QR | Only approved reservations may produce a transaction QR. |
| Transaction completion | Operator QR data must be verified by the server before completion. |

## Member 3 Checkpoint 1: executable policy foundation

ReservationRules in Application defines the policies below without a complete
ReservationService, repository, controller or persistence mutation. The API
composition root registers TimeProvider.System and ReservationRules. Each create,
update or cancellation validation reads the injected clock once; tests substitute
a fixed clock. Existing authentication clocks and behavior are unchanged.

These rules are tested in isolation. They are not yet reachable through reservation
HTTP endpoints and do not by themselves authorize requests or prevent races.

### UTC timing

All schedule inputs must be DateTimeKind.Utc, with end strictly after start.
Local/unspecified timestamps and invalid intervals raise BadRequestException (400
under existing middleware). Comparisons use elapsed UTC time without rounding.

- Create: start > now and start - now <= 7 days. Exactly seven days is allowed;
  seven days plus one second is rejected with 400. Past/current starts are rejected.
- Update: the existing accepted start must have at least 12 hours remaining.
  Exactly 12 hours is allowed; 11:59:59 is rejected with ConflictException (409).
  Replacement schedules must also be in the future, within seven days, and at
  least 12 hours away. Moving later cannot bypass the original cutoff.
- Cancel: the accepted start must have at least 12 hours remaining. Exactly
  12 hours is allowed; 11:59:59 is rejected with 409.
- GridOperator assistance must apply these same rules in the future service.

The accepted schedule source must be resolved as described in DATABASE.md before
implementing lifecycle operations. The policy accepts server-resolved timestamps,
never a client's claimed schedule.

### Status transitions

| Operation | Current status | Result |
| --- | --- | --- |
| Create (planned orchestration) | None | Pending |
| Update | Pending | Pending |
| Update | Approved | Pending (reapproval required) |
| Cancel | Pending or Approved | Cancelled |
| Update or cancel | Rejected, Cancelled, Completed or unknown enum | Reject with 409 |

The validator returns the allowed target status; it does not mutate an entity.
A future successful update must clear any existing QrToken together with the
status/schedule write, including Pending records with stale data. Cancellation
must also clear stale QrToken data. No token is generated, scanned or completed
by this checkpoint. Approval, rejection and completion workflows remain outside
Member 3's implemented scope.

### Overlap

For reservations of the same Prosumer, Pending and Approved are active.
Rejected, Cancelled and Completed do not cause booking conflicts.
The exact predicate is existingStart < requestedEnd AND requestedStart < existingEnd.
Adjacent intervals are allowed; identical/containing/partially overlapping
intervals conflict.

ReservationRules.Conflicts evaluates one interval pair. The future service must
scope records to the authenticated/assisted Prosumer, exclude the reservation being
updated, and turn a true result into ConflictException. The predicate itself neither
queries records nor performs ownership checks.

### Deferred orchestration requirements

The future service must recheck active account/permitted role and ownership,
resolve station through SlotId, validate active slot/station and energy amount,
check availability/overlap, and persist changes safely. Requests have no authoritative
identity, schedule, status or QR fields.

Capacity must use an atomic conditional slot update and compensate failures using
the existing standalone MongoDB topology. Cancellation must release capacity once,
and moving slots must not lose or duplicate capacity. Serializing competing
reservations for the same Prosumer needs its own persistence strategy; a capacity
decrement alone does not prevent overlapping bookings across different slots.
No concurrency guarantee or service-level validation is claimed by Checkpoint 1.
