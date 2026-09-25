# MongoDB Contract

Required collections:

- `UsersDetail`
- `SolarStationInfo`
- `EnergyBookingSlots`
- `EnergyReservation`

## Identifier strategy

- User/Prosumer: NIC is stored as MongoDB `_id`.
- Station/Slot/Reservation: stable GUID strings are used as IDs.
- References are stored as IDs (`ProsumerNic`, `StationId`, `SlotId`).
- Server timestamps are UTC.

Do not rename fields or collections without a reviewed architecture change because web and mobile API contracts depend on them.

## Foundation persistence

`MongoMappings` in Infrastructure owns BSON ID/string-enum mappings. Existing PascalCase BSON field names are preserved; HTTP uses separate camelCase DTOs. `UsersDetail` has a unique normalized Email index in addition to MongoDB's unique `_id`. Other starter indexes cover station active/name, slot station/start, reservation Prosumer/created and station/status. These indexes are not completed feature workflows.

Initialization is repeatable and tolerates another API process creating a collection concurrently. MongoDB duplicate-key errors in user writes become HTTP 409 through the application error contract.

Local Compose uses MongoDB 7, localhost port 27017 and the named `smartsolar_mongo_data` volume (Compose prefixes its actual name). It has no database credentials and must remain local-only. Production MongoDB must use deployment-managed authentication/network restrictions; never put connection passwords into repository files.

Tests use a unique `SmartSolarTests_<guid>` database on `SMARTSOLAR_TEST_MONGO`, removing that exact test database afterward. They never clear `SmartSolarMicrogridDb`. Android SQLite `local_user` caches the API profile; it does not store passwords or perform enterprise validation.

## Member 3 Checkpoint 1 contract review

**Historical checkpoint note:** this section describes the earlier checkpoint.
The current Member 3 code has since implemented nullable accepted schedule
snapshots and reservation persistence; see the current contract below. Member 4
does not add those fields or change their mappings.

No persisted entity, collection, identifier, BSON mapping or index changes were made.

| Existing entity | Relevant existing fields |
| --- | --- |
| EnergyReservation | ReservationId, ProsumerNic, StationId, SlotId, EnergyAmountKwh, Status, QrToken, CreatedAtUtc, UpdatedAtUtc |
| EnergyBookingSlot | SlotId, StationId, StartAtUtc, EndAtUtc, TotalSlots, AvailableSlots, IsActive, CreatedAtUtc, UpdatedAtUtc |
| SolarStation | StationId, Name, Address, Latitude, Longitude, CapacityKwh, TotalBatterySlots, IsActive, CreatedAtUtc, UpdatedAtUtc |

ReservationStatus remains Pending, Approved, Rejected, Cancelled, Completed.
Pending is already the entity default. Only the user repository exists in Phase 0;
there is no station/slot reservation repository or capacity mutation to reuse yet.

AvailableSlots/TotalSlots can support a later conditional capacity decrement
without a new collection. Slot capacity counts reservations, not kWh; the precise
relationship between station CapacityKwh and requested energy must be settled with
the station/slot owner. Checkpoint 1 performs no availability or energy allocation.

### Proposed shared contract change requiring team review (not applied)

An EnergyReservation currently stores only SlotId, not its accepted schedule.
Reading the referenced slot is sufficient **only if its schedule is immutable for
the reservation's lifetime and the slot is retained**. The repository currently
contains no implementation enforcing that guarantee.

If slot schedules may change, the smallest reservation extension is nullable UTC
ScheduledStartAtUtc and ScheduledEndAtUtc snapshot fields on EnergyReservation.
Creation and a successful slot change would copy server-resolved slot times; other
operations would use the accepted snapshots. The response DTO already has these
names. This would avoid coupling Member 3 cutoff/overlap correctness to mutable
Member 1 slot data.

This extension is proposed, not an agreed or implemented schema migration.
Before lifecycle implementation, team review must choose snapshots or enforce
retained immutable slot schedules. If snapshots are selected, add BSON compatibility
tests and an explicit legacy-data policy. Missing legacy snapshots must never be
treated as DateTime.MinValue or silently reconstructed from a possibly changed slot.
A trustworthy backfill or a conflict requiring repair is necessary.

No index is added before query implementation. Existing starter indexes and
standalone MongoDB topology remain unchanged. Atomic capacity acquisition,
compensation, exactly-once release, concurrent same-Prosumer conflict prevention,
and their integration tests belong to later checkpoints.

## Current contract inspected for Member 4 steps 2–5

EnergyReservation already has nullable DateTime ScheduledStartAtUtc and
ScheduledEndAtUtc accepted snapshots in the current Member 3 implementation.
Missing legacy values remain null. Member 3 creation/update writes these snapshots;
reads and writes reject invalid/missing accepted schedules rather than guessing
from slots. User also already contains the Member 3 ReservationWriteLock field for
standalone write recovery. These are discoveries of existing schema, not changes
introduced by this Member 4 checkpoint. The earlier snapshot proposal above is
historical. No automatic legacy backfill is performed.

Member 4 adds these nonunique reservation indexes and partial QR index through the existing
repeatable MongoDbInitializer:

| Index | Ascending keys | Justification |
| --- | --- | --- |
| ix_reservations_status_start | Status, ScheduledStartAtUtc | Global Pending count and Approved future range |
| ix_reservations_prosumer_status_start | ProsumerNic, Status, ScheduledStartAtUtc | Owner-scoped status counts and Approved future range |
| ux_reservations_qr_token_hash | QrTokenHash (Unique, PartialFilter: QrTokenHash is String) | High-speed O(1) server lookup for QR verification while allowing multiple null documents |

## Steps 7–10: QR and Completion persistence extensions

To support secure QR issuance, verification, and transaction completion without creating separate collections:
- `EnergyReservation` document includes:
  - `QrTokenHash` (`string?`): Hexadecimal SHA-256 hash of the 256-bit cryptographically random token. The raw token is NEVER stored in the database.
  - `QrIssuedAtUtc` (`DateTime?`): Server UTC timestamp of token issuance/rotation.
  - `CompletedAtUtc` (`DateTime?`): Server UTC timestamp recorded when transaction is marked as Completed.
  - `CompletedByOperatorNic` (`string?`): NIC of the authenticated Grid Operator who executed the completion.
- `MongoMappings` configures `SetIgnoreIfNull(true)` for `QrTokenHash`, `QrIssuedAtUtc`, `CompletedAtUtc`, and `CompletedByOperatorNic`.
- `ReservationReadRepository` explicitly excludes `QrTokenHash` from read projections to ensure hashes are never exposed via list/search/history endpoints.
- **Explicit Architecture Confirmation**: NO new MongoDB collections (e.g. `QrCodes`, `Transactions`, `QrTransactions`, `CompletedReservations`) were created. All reservation lifecycle and completion data resides in `EnergyReservation`.

Existing `_id`, `ix_reservations_prosumer_created` and
`ix_reservations_station_status` remain unchanged. Index keys and repeated
initialization are covered by Mongo-backed tests.

Read filters, sorting and pagination execute in MongoDB. Lists fetch at most
pageSize + 1 summaries and omit QrToken and QrTokenHash from the projection; dashboard uses
CountDocumentsAsync, not full collection loading. A scoped existence query detects
invalid snapshots before date filtering/pagination. These additive indexes do not
cover every history/snapshot-validity predicate; no blanket query-performance
claim is made. The API contract and
[Member 4 handshake](MEMBER-4-RESERVATION-CONTRACT.md) define scoping and repair errors.

