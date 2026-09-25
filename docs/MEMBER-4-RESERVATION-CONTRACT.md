# Member 3 / Member 4 reservation handshake

This checkpoint implements Member 4 steps 2–5 only: contract inspection, booking
read views and dashboard counts. The implementation was inspected before edits;
older Checkpoint 1 descriptions are historical, not the current code contract.

## Actual Member 3 fields

| EnergyReservation field | C# type | Meaning / persistence |
| --- | --- | --- |
| ReservationId | string | Stable GUID string; defaults to GUID N format; Mongo `_id` |
| ProsumerNic | string | Owner's normalized NIC; references UsersDetail `_id` |
| StationId | string | Station GUID string; references SolarStationInfo `_id` |
| SlotId | string | Slot GUID string; references EnergyBookingSlots `_id` |
| EnergyAmountKwh | decimal | Requested energy; positive on existing lifecycle inputs |
| Status | ReservationStatus | Stored as an enum-name string; defaults to Pending |
| ScheduledStartAtUtc | DateTime? | Accepted UTC start snapshot; nullable for legacy documents |
| ScheduledEndAtUtc | DateTime? | Accepted UTC end snapshot; nullable for legacy documents |
| QrToken | string? | Existing internal field; not returned by read DTOs |
| CreatedAtUtc | DateTime | Server creation time in UTC |
| UpdatedAtUtc | DateTime | Server last modification time in UTC |

MongoMappings preserves PascalCase BSON fields and string enum serialization.
HTTP uses camelCase. The existing ReservationResponse has the same summary fields
except QrToken, and requires non-null DateTime schedule values. It is reused inside
the new page response; no competing reservation model or schedule field is added.

Accepted snapshots already exist in Member 3's code and are written on creation
and successful updates. They must not be reconstructed from a mutable or deleted
slot. End must be later than start. Existing reads reject missing/invalid snapshots
with 409 requiring verified backfill.

## Existing statuses and lifecycle

| Exact status | Enum value | Meaning consumed by Member 4 |
| --- | --- | --- |
| Pending | 0 | Awaiting approval; active for Member 3 conflict checks |
| Approved | 1 | Approved; active for Member 3 conflict checks |
| Rejected | 2 | Terminal, excluded from active booking views |
| Cancelled | 3 | Terminal cancellation |
| Completed | 4 | Terminal completion; not proof that this checkpoint implements completion |

Existing creation writes Pending. Updating Pending or Approved returns Pending
(reapproval) and clears stale QrToken. Cancelling Pending or Approved writes
Cancelled and clears QrToken. Rejected, Cancelled, Completed and undefined states
reject update/cancellation with 409. Existing seven-day, twelve-hour, capacity,
overlap, lock and recovery rules remain unchanged. Approval, rejection and
completion operations are not implemented by this checkpoint.

Before this checkpoint, IReservationRepository provided exact optional
status/ProsumerNic/StationId listing (created descending, reservation ID ascending),
single-ID lookup, active Pending/Approved owner lookup, slot/station reads, durable
owner locking, capacity acquisition/release, insert and conditional replacement.
The GridOperator collection list was unpaged. New bounded read operations use a
separate IReservationReadRepository; existing lifecycle interfaces remain intact.

Existing routes under `/api/v1/reservations`, preserved unchanged:

| Method | Suffix | Existing access |
| --- | --- | --- |
| GET | (none) | GridOperator operational list |
| POST | (none) | Prosumer creation |
| POST | /prosumers/{prosumerNic} | GridOperator assisted creation |
| GET | /{reservationId} | Owning Prosumer or GridOperator |
| PUT | /{reservationId} | Owning Prosumer or GridOperator |
| PATCH | /{reservationId}/cancel | Owning Prosumer or GridOperator |

## Member 4 read definitions

All comparisons use one captured injected TimeProvider UTC instant per operation.
These categories do not mutate the persisted status or imply completion:

| View / count | Exact definition |
| --- | --- |
| Current | Status Pending or Approved AND ScheduledEndAtUtc > now |
| Pending | Status Pending, regardless of schedule being past/current/future |
| History | Status Rejected, Cancelled or Completed, OR Pending/Approved with ScheduledEndAtUtc <= now |
| Search | All statuses within the authorized scope, intersected with supplied exact filters |
| PendingReservations | Count of Status Pending, with no time condition |
| ApprovedFutureReservations | Count of Status Approved AND ScheduledStartAtUtc > now |

Current intentionally includes ongoing bookings: a start-only cutoff would hide
them before their accepted end. At the exact end, an active booking moves into the
history view but keeps its original status. A terminal booking belongs to history
even if its schedule is future. Past Pending can appear in both pending and
history; the pending queue and the current/history partition answer different
questions. Approved starting exactly now is current but not approved-future.

Current/pending sort by accepted start ascending, then reservation ID ascending.
History/search sort by accepted start descending, then reservation ID ascending.
All four list routes accept the same filters and return bounded pages. Filters are
ANDed with the view definition; pending with status Approved therefore returns an
empty page, not a broader view. Exact field search replaces any need for unsafe
free-text regex search in this checkpoint.

## Identity, errors and legacy data

The existing JWT pipeline and current-account checks are reused. The application
service reloads the account too, so direct service calls cannot bypass role/state
checks. Prosumer scope comes from authenticated identity; an explicit different
ProsumerNic filter returns 403. An ID search for another owner's record returns
an empty page. GridOperators retain operational visibility across all owners, with
optional narrowing filters. Backoffice has no access. Missing/invalid/revoked JWTs
return 401; disallowed roles return 403.

Lists preserve the 409 backfill policy. A Mongo existence query checks snapshots
within the owner/exact-field/category-status scope **before time filtering and
pagination**. This prevents a legacy record from silently disappearing merely
because its time is unknown, or being hidden on another page. For current, only
Pending/Approved candidates are checked; for pending, only Pending candidates;
history/search check the matching exact-field scope before date classification.
Other owners' bad records cannot affect a Prosumer's request. Invalid snapshots
are never returned as DateTime.MinValue, substituted with slot times, or repaired
by these reads.

The dashboard validates Approved snapshots within its authorized scope. If any
are missing/invalid, the whole summary returns 409 rather than a misleading
approved-future count. Pending counting is status-only and includes Pending rows
with missing snapshots. Zero matches return numeric zero. CountDocumentsAsync
performs both counts in Mongo, without downloading the collection. Both use the
same time boundary but are separate reads, not a transactionally frozen snapshot
under concurrent writes. Likewise pagination is stable for unchanged data, not
snapshot isolation across requests.

## Database impact

No entity, BSON mapping or collection changes were made by Member 4. No fifth
collection was introduced. Existing reservation indexes were `_id`,
`ix_reservations_prosumer_created` (ProsumerNic ascending, CreatedAtUtc descending),
and `ix_reservations_station_status` (StationId ascending, Status ascending).

Two additive, nonunique indexes follow the existing repeatable initializer:

- `ix_reservations_status_start`: Status ascending, ScheduledStartAtUtc ascending.
  Supports global Pending counts and Approved future start ranges.
- `ix_reservations_prosumer_status_start`: ProsumerNic ascending, Status ascending,
  ScheduledStartAtUtc ascending. Supports owner-scoped equivalents and status reads.

No claim is made that these cover every history sort or legacy-snapshot validity
check. Those checks remain server-side filters; response reads are capped at
pageSize + 1 documents. Integration tests verify keys, repeatable initialization
and the exact four collection names.

## Decisions, readiness and exclusions

No missing-status blocker exists: both Approved and Completed already exist.
The chosen current/history interpretation above fills a previously undocumented
read-view definition. Member 3/team leader should review it as the client contract,
especially that elapsed Pending remains Pending and is also visible in history.
No lifecycle decision or new status is required for these read endpoints.

Legacy data still requires Member 3/team leader to identify a trustworthy snapshot
backfill source and authorize repair if 409 is encountered. This checkpoint does
not guess schedules, run migrations or implement a backfill. Approval/completion
writers remain future work; the reads consume their already-defined statuses.

Implemented:
- Backend:
  - Current, pending, history, search/filter, pending count and approved-future count.
  - STEP 7: Secure QR generation/issuance endpoint (`POST /api/v1/reservations/{reservationId}/qr`), 256-bit secure RNG token generation, SHA-256 hash storage on `EnergyReservation`, automatic QR rotation on reissue, Approved-only status enforcement, Prosumer ownership authorization.
  - STEP 9: Server QR verification endpoint (`POST /api/v1/reservations/qr/verify`), format parsing (`SMG1.` prefix), SHA-256 hash lookup via `ux_reservations_qr_token_hash` index, GridOperator role authorization, rejection of non-Approved states (`Pending`, `Rejected`, `Cancelled`, `Completed`, rotated tokens), trusted server data response with `eligibleForCompletion = true`.
  - STEP 10: Server transaction completion endpoint (`POST /api/v1/reservations/qr/complete`), full revalidation of current state, atomic transition from `Approved` to `Completed`, recording `CompletedAtUtc` and `CompletedByOperatorNic`, duplicate completion / replay rejection with HTTP 409 Conflict.
- Web UI (Step 6):
  - Operations dashboard with live API summary counts (`/operator/reservations/dashboard`)
  - Current bookings view (`/operator/reservations/current`)
  - Pending bookings queue (`/operator/reservations/pending`)
  - Booking history view (`/operator/reservations/history`)
  - Search and filter view (`/operator/reservations/search`)
- Android UI (Steps 6, 7, 8, 9, 10):
  - Dashboard with live API summary counts on Home screen
  - Current bookings screen (`CurrentBookingsActivity`)
  - Pending bookings screen (`PendingBookingsActivity`)
  - Booking history screen (`BookingHistoryActivity`)
  - Search and filter screen (`SearchBookingsActivity`)
  - STEP 7: "View Transaction QR" on Approved reservations in booking items, `ReservationQrActivity` for secure QR issuance and local bitmap rendering via ZXing.
  - STEP 8: `QrScannerActivity` with native camera scanning via ZXing `DecoratedBarcodeView`, runtime `android.permission.CAMERA` handling, and direct Grid Operator scan launcher on Home screen.
  - STEP 9: `QrVerificationResultActivity` displaying trusted server-returned verification details (Reservation ID, Prosumer NIC, Station, Slot, Time, Energy amount, Verified badge, eligibility display).
  - STEP 10: "Complete Energy Transfer" confirmation dialog, in-flight tap guard, API completion execution, transition to Completed state, and display of authoritative completion metadata.

Deferred / Out-of-Scope (Not part of Member 4 Steps 2–10):
- Station CRUD, Energy slot CRUD, Google Maps, account lifecycle, new reservation mutation workflows



