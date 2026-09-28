# Business Rules

Authoritative validation belongs in the C# service layer. The integrated implementation now includes Member 3 lifecycle and Member 4 QR/completion; historical checkpoint text below is not the current implementation boundary. See [final audit](FINAL-INTEGRATION-AUDIT.md) for unresolved acceptance blockers.

## Current integrated reservation behavior

Create writes Pending and consumes one available slot; accepted start/end are copied from the server's slot. Seven-day and twelve-hour boundaries, ownership and overlap validation run in ReservationRules/ReservationService. Update returns Pending and clears QR fields; moving acquires replacement capacity and releases the old place. Approval retains capacity. Cancellation/rejection persist a terminal state then release one place through a separate conditional write, not a multi-document transaction. Rejection remarks are retained in lifecycle and booking-query summaries.

Completion conditionally changes Approved to Completed and records operator/time once. It does not release slot capacity because Completed consumed its published booking place. Verification and completion use the accepted reservation window and require active, correctly linked Prosumer, station and slot records.

Reservation count capacity uses atomic slot decrements. Catalog and reservation allocation/capacity writes acquire the singleton `CatalogWriteGate` first, then the durable per-Prosumer lock when required. Aggregate energy checks and catalog protection therefore serialize within the supported single-IIS-instance deployment; Mongo compare-and-update checks remain the per-document boundary.

## Implemented common foundation

- Registration creates a Prosumer in PendingActivation; Backoffice activation is required before login.
- Only Active accounts receive tokens. Every authenticated request checks current stored status/role.
- Backoffice controls activation/reactivation and staff creation. Prosumer self-deactivation is preserved from the starter.
- NIC is immutable and normalized; normalized email and NIC must be unique. Passwords are stored only as salted one-way hashes.
- Staff role is explicitly required and limited to Backoffice or GridOperator. User profile changes cannot change identity, role or account status.
- Backoffice may update only a Prosumer's editable contact fields through the managed profile route. Backoffice activates pending Prosumers and is the sole authority for reactivating a deactivated account.

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

**Historical checkpoint note:** the descriptions of future orchestration below
record Checkpoint 1. The current repository includes Member 3 ReservationService,
repositories, endpoints and accepted snapshots; Member 4 does not modify those
write rules. The inspected current contract is documented in
[MEMBER-4-RESERVATION-CONTRACT.md](MEMBER-4-RESERVATION-CONTRACT.md).

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

## Member 4 steps 2–5: implemented read semantics

- Current: Pending or Approved with accepted ScheduledEndAtUtc > server UTC now.
  Ongoing bookings remain current until the accepted end; terminal statuses never
  appear here, even with a future schedule.
- Pending: exact Pending status, including past Pending reservations.
- History: Rejected, Cancelled or Completed regardless of date, plus Pending or
  Approved with accepted ScheduledEndAtUtc <= now. This classification does not
  write Completed, release capacity or alter Member 3 conflict rules.
- Search: exact controlled identifier/NIC/station/status filters and inclusive
  accepted-start date bounds, applied in MongoDB within authorized scope.
- Dashboard pending count: exact Pending status, without a date restriction.
- Dashboard approved-future count: exact Approved status AND accepted
  ScheduledStartAtUtc > the captured server UTC instant. Approved at/past now and
  future Pending/Rejected/Cancelled/Completed never contribute to this count.

Every operation uses the injected TimeProvider and authoritative stored account
role/state. Prosumer identity comes from authenticated context; a supplied other
NIC is forbidden. GridOperators retain operational reads; Backoffice is excluded.
Lists are bounded and ordered in MongoDB; clients do not decide membership or
counts. Pending and history can overlap because elapsed time alone never resolves
the Pending lifecycle state.

Read DTOs reuse accepted snapshots. Missing/invalid snapshots trigger 409 within
the scoped candidate set before time filtering/pagination. The dashboard checks
Approved snapshots before calculating approved-future; its Pending count remains
status-only, including legacy Pending. No guessed slot schedule, default date,
automatic status change or backfill is permitted. See the handshake for full
legacy handling and the chosen current/history interpretation.

## Member 4 steps 7–9: implemented QR issuance and verification rules

### QR Generation & Display Rules (Step 7)
- **Status Gate**: A transaction QR can ONLY be issued for a reservation whose authoritative status is strictly `Approved`.
- **Rejection of Non-Approved States**: Attempting to generate a QR for `Pending`, `Rejected`, `Cancelled`, or `Completed` reservations is rejected with HTTP 409 Conflict.
- **Ownership Scope**: A Prosumer can only request a QR for their own reservation; requests for reservations belonging to another Prosumer are rejected with HTTP 403 Forbidden.
- **Opaque Reference**: The QR payload contains only an opaque reference (`SMG1.<256-bit-random-token>`) and zero authoritative business fields (no NIC, no status, no energy amount, no station info).
- **Secure Randomness**: Tokens are generated using `System.Security.Cryptography.RandomNumberGenerator`.
- **Database Hashing**: The database stores only a SHA-256 hash (`QrTokenHash`) and issuance timestamp (`QrIssuedAtUtc`) inside the `EnergyReservation` document. Raw tokens are never persisted or logged.
- **Single Active Reference / Rotation**: Reissuing a QR for an existing Approved reservation replaces `QrTokenHash`, invalidating previous QR references.

### Grid Operator QR Scanning Rules (Step 8)
- **Native Android Flow**: Native camera QR scanning using `DecoratedBarcodeView` (ZXing Android Embedded).
- **Runtime Camera Permission**: The app requests only `android.permission.CAMERA` with graceful fallback for permission denial and settings navigation.
- **Zero Local Authority**: The mobile app performs format validation (`SMG1.` prefix) for UX only, but NEVER decides validity or status locally.

### Server Verification Rules (Step 9)
- **Role Requirement**: Server-side verification is strictly restricted to authenticated active `GridOperator` users (HTTP 403 Forbidden for Prosumer or Backoffice).
- **Authoritative Database State**: Verification queries MongoDB by `SHA-256(scanned_token)` and validates that the reservation is currently `Approved`.
- **Accepted Window and Eligibility**: Verification requires `ScheduledStartAtUtc <= server now < ScheduledEndAtUtc`, an active referenced Prosumer, active station and active slot, and a slot-to-station linkage matching the reservation. Accepted snapshots are authoritative; mutable slot times are not consulted.
- **Rejection Matrix**:
  - Unknown/invalid/rotated token: HTTP 404 Not Found
  - Malformed/empty payload: HTTP 400 Bad Request
  - `Pending`, `Rejected`, `Cancelled`, or `Completed` reservation: HTTP 409 Conflict
- **Trusted Server Payload**: On successful verification, the server returns authoritative database details and sets `eligibleForCompletion = true`.

### Transaction Completion Rules (Step 10)
- **Role Authorization**: Completion is restricted exclusively to authenticated active `GridOperator` accounts. The operator NIC is derived directly from the server-side JWT context (never trusted from client payloads).
- **Full Server Revalidation**: Completion does NOT trust earlier client verification screens. The server re-reads MongoDB state to re-verify existence, QR token hash match, and `Approved` status.
- **Authoritative Status Transition**: Successful completion transitions status from `Approved` to `Completed`.
- **Completion Metadata**: The server records `CompletedAtUtc` (server clock UTC instant) and `CompletedByOperatorNic` on the `EnergyReservation` document.
- **Single-Execution / Replay Protection**:
  - An atomic conditional MongoDB update is executed requiring `Status == Approved`.
  - Attempting to complete an already completed reservation returns `409 Conflict`.
  - Attempting to complete a `Cancelled`, `Rejected`, or `Pending` reservation returns `409 Conflict`.
- **Query Effect**: Completed reservations automatically move from active views (`Current`, `Dashboard approvedFuture`) into `History`.


## Implemented Member 1 rules and remaining dependency

- Backoffice manages station metadata, the seven-day UTC operating schedule and soft deactivation.
- GridOperator manages slot inventory. All three roles can discover active stations and published active slots through the API; web staff can inspect inactive history.
- Positive energy capacity and battery-slot count, valid finite GPS coordinates, nonempty name/address and complete nonambiguous weekly hours are enforced in C#.
- Slot windows require start < end, positive total and bounded availability. Each inventory window's total is at most station TotalBatterySlots. Station capacity cannot be reduced below the sum of energy allocated by active Pending/Approved reservations; the exact allocated-energy boundary is allowed.
- Active inventory windows cannot overlap within one station. Their half-open boundary convention permits one ending exactly when the next starts. This is the Member 1 conflict policy, not a Member 3 booking-window rule.
- No claim is made that a published active slot is a currently bookable reservation. There is no past/future booking policy, 7-day horizon, 12-hour update/cancel rule, availability allocation, schedule-to-slot enforcement or automatic reservation transition here.
- All edits require the latest updatedAtUtc; stale updates fail with 409. Single-instance related writes are serialized, with atomic per-record timestamp checks in MongoDB.
- Nearby returns only active API records within radius, ordered by server-calculated great-circle distance. Maps renders those records and never supplies enterprise station truth.

### Frozen active-reservation protection rule

The team has frozen the station/slot protection rule:

| ReservationStatus | Blocks station deactivation and protected slot mutation |
| --- | --- |
| Pending | Yes |
| Approved | Yes |
| Rejected | No |
| Cancelled | No |
| Completed | No |

The read-only reservation query matches the target StationId or SlotId and Status in Pending/Approved. A matching active reservation blocks station soft deactivation and slot update, availability change and soft deactivation with 409. Terminal-status references remain stored but do not trigger this guard. Other validation, inactive-parent and optimistic concurrency checks still apply.

The existing EnergyReservation and ReservationStatus contracts, IDs, references and string-enum BSON mappings are unchanged. Protection queries do not update reservation status or implement lifecycle transitions, approval, booking CRUD, 7-day/12-hour rules, QR or completion.

The active-status definition is resolved. Cross-record coordination with the now-integrated Member 3 writers remains unresolved: establish reviewed atomicity across related records before final acceptance or multi-instance deployment. The existing in-process gate and per-document timestamp comparison remain unchanged; they do not lock another process or a future reservation writer.
