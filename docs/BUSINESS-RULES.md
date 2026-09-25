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

## Implemented Member 1 rules and remaining dependency

- Backoffice manages station metadata, the seven-day UTC operating schedule and soft deactivation.
- GridOperator manages slot inventory. All three roles can discover active stations and published active slots through the API; web staff can inspect inactive history.
- Positive energy capacity and battery-slot count, valid finite GPS coordinates, nonempty name/address and complete nonambiguous weekly hours are enforced in C#.
- Slot windows require start < end, positive total and bounded availability. Each inventory window's total is at most station TotalBatterySlots. Station capacity cannot be reduced below an active slot total.
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

The active-status definition is resolved. Cross-record coordination with future Member 3 writers remains a separate integration dependency: before integrating booking allocation/lifecycle writes or deploying multiple API instances, establish reviewed atomicity across related records. The existing in-process gate and per-document timestamp comparison remain unchanged; they do not lock another process or a future reservation writer.
