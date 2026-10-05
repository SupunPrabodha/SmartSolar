# Business rules

The ASP.NET Core API is authoritative. UI visibility and local caches never grant permissions. All dates used for policy comparisons are UTC; browser/device-local display is presentation only.

## Accounts and authentication

- Roles: Backoffice, GridOperator, Prosumer. States: PendingActivation, Active, Deactivated. NIC is immutable; normalized NIC/email are unique.
- Prosumer registration creates PendingActivation. Backoffice approval sends a one-time verification email; only successful verification activates the Prosumer. Resend invalidates the old link and requires one minute between attempts. Email changes require renewed approval/verification; deactivation revokes pending links.
- Only Active accounts receive JWTs. Authenticated requests reload status/role and validate security version. Password reset/change invalidates previous sessions.
- Backoffice creates Backoffice/GridOperator staff, manages Prosumer contacts and controls activation/reactivation. Own-profile updates cannot change NIC or role. Prosumers may self-deactivate; reactivation requires Backoffice.
- Passwords are salted one-way hashes. Recovery uses generic acknowledgements, expiring one-time hashed tokens and atomic consumption. [API details](API-CONTRACT.md) define profile/avatar, notification and export boundaries.

## Station and slot validation

Backoffice manages stations, GPS, capacity, weekly operating schedule and soft deactivation. GridOperator manages slot inventory. All roles discover active stations/slots; staff may inspect inactive history. There is no inferred station-operator assignment.

Station name/address must be nonempty and bounded; latitude/longitude finite and within -90..90/-180..180; capacity and battery-slot count positive. The recurring schedule contains seven distinct ISO weekdays and valid UTC opening/closing intervals; see [database contract](DATABASE.md#station-and-slot-contract). The schedule is descriptive metadata: slot windows are not automatically constrained to those operating hours.

Slots require start < end, positive total, and 0 <= available <= total <= station.TotalBatterySlots. Active slot windows at one station cannot overlap; touching endpoints are permitted. Creation/editing usable inventory requires an active parent. Station capacity cannot be reduced below energy allocated by all Pending/Approved reservations referencing that station; equality is permitted.

Catalog updates require the exact latest expectedUpdatedAtUtc. Missing values return 400; stale writes return 409. IDs, references and creation timestamps remain unchanged; deactivation is soft and retains historical children.

### Frozen active-reservation protection rule

| ReservationStatus | Blocks station deactivation and protected slot mutation |
| --- | --- |
| Pending | Yes |
| Approved | Yes |
| Rejected | No |
| Cancelled | No |
| Completed | No |

The guard filters the target StationId or SlotId and these exact active statuses. A match blocks station deactivation and slot editing, availability changes and deactivation with 409. Terminal references remain stored; other validation/concurrency checks still apply.

Nearby results contain active stored stations within the inclusive requested radius, ordered by great-circle distance then ID. Radius is 0.1–500 km, default 25. Distance is transient, not driving distance or Google place-search data.

## Reservation scheduling and authorization

Creation resolves station and schedule from the selected active slot; requests supply slotId and energyAmountKwh, not authoritative status/identity/dates. Prosumer creates for self; GridOperator may assist a named active Prosumer. Backoffice is excluded from reservation operations.

- Create: accepted start > server now and <= seven elapsed days away. Exactly seven days is allowed.
- Update: at least twelve hours remain before the existing accepted start; the replacement start also satisfies twelve-hour notice and the seven-day horizon. Moving later cannot bypass the old cutoff.
- Cancellation: at least twelve hours remain before the accepted start. Exactly twelve hours is allowed; 11:59:59 is rejected.
- Same-Prosumer Pending/Approved reservations conflict when existingStart < requestedEnd AND requestedStart < existingEnd; adjacent intervals are allowed. Updates exclude their own record.
- Requested energy must be positive and within station capacity. Allocation checks the sum of active reservations for the selected slot plus the requested energy against station CapacityKwh; station metadata reduction separately considers all active station references.
- Accepted UTC start/end snapshots remain authoritative. Missing/invalid legacy snapshots cause a conflict requiring reviewed repair; never guess from current slot times.

## Lifecycle and capacity

| Operation | Required state | Result |
| --- | --- | --- |
| Create | New eligible request | Pending; consumes one available place |
| Update | Pending or Approved, notice/eligibility valid | Pending; clears QR data; reapproval required |
| Approve | Pending, future accepted start and active related records | Approved |
| Reject | Pending; 1–500 character remark | Rejected; releases one place |
| Cancel | Pending or Approved, notice valid | Cancelled; clears QR data; releases one place |
| Complete | Approved, verified eligible QR/window | Completed; retains consumed place |

Rejected, Cancelled and Completed cannot be modified/cancelled. Moving a reservation acquires replacement capacity and releases its previous place with compensation on failure. Rejection/cancellation release follows the terminal write; these are conditional single-document writes, not a Mongo multi-document transaction.

Catalog and reservation mutations acquire the singleton CatalogWriteGate before the durable per-Prosumer lock when required. The supported deployment is **one API process**. Slot allocation advances UpdatedAtUtc, so stale catalog writes cannot overwrite it. Recovery locks may remain after failed compensation and require reviewed reconciliation; see [database](DATABASE.md). This does not provide multi-instance coordination.

## Booking queries and dashboard

- Current: **Approved** with accepted ScheduledEndAtUtc > server now, including ongoing reservations.
- Pending: exact Pending, regardless of date.
- History: Rejected/Cancelled/Completed regardless of date, plus Pending/Approved whose accepted end <= now.
- Search: controlled exact identifiers/NIC/station/status and inclusive accepted-start bounds within authorized scope.
- Pending count: exact Pending, no date restriction.
- Approved-future count: exact Approved and accepted start > captured server now.

Prosumer reads/counts are own-only; GridOperator reads operational data; Backoffice is forbidden. Pending and history can overlap; elapsed time does not automatically transition a reservation. Lists are paginated in MongoDB. Invalid snapshots are checked before temporal filtering/pagination in the candidate scope; dashboard requires valid Approved snapshots while its Pending count is status-only.

## QR issuance, verification and completion

Only an owning Active Prosumer can issue/rotate a QR for an Approved reservation. The payload is SMG1 plus a 256-bit random opaque token; it contains no authoritative status, NIC, station or energy data. Mongo stores only its SHA-256 hash and issuance time. Rotation invalidates the previous reference.

Only an Active GridOperator can verify/complete. The API resolves the scanned hash, reloads authoritative state and requires Approved, accepted start <= server now < accepted end, an active Prosumer owner, active station and slot, and a matching slot-to-station link. Mutable slot times do not replace accepted snapshots. A previous verification screen never authorizes completion.

Malformed payloads return 400; unknown/rotated references 404; known ineligible states or related-record/window failures 409; wrong roles/ownership 403. Completion atomically conditions on the current approved record/token, changes to Completed and records CompletedAtUtc/CompletedByOperatorNic from server context. Replay/concurrent duplicate attempts cannot produce a second transition. Completion retains consumed slot inventory and clears the record from Current/approved-future counts while preserving history.

The scanner requests camera permission with denial/settings recovery. Local QR format checks are UX only; no client performs authoritative verification.
