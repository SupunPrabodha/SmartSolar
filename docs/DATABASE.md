# MongoDB Contract

Required collections:

- `UsersDetail`
- `SolarStationInfo`
- `EnergyBookingSlots`
- `EnergyReservation`

The schema retains accepted reservation schedule snapshots, account write-recovery locks and QR/completion metadata. No automatic legacy backfill is performed.

## Identifier strategy

- User/Prosumer: NIC is stored as MongoDB `_id`.
- Station/Slot/Reservation: stable GUID strings are used as IDs.
- References are stored as IDs (`ProsumerNic`, `StationId`, `SlotId`).
- Server timestamps are UTC.

Do not rename fields or collections without a reviewed architecture change because web and mobile API contracts depend on them.

## Persistence and initialization

`MongoMappings` in Infrastructure owns BSON ID/string-enum mappings. Existing PascalCase BSON field names are preserved; HTTP uses separate camelCase DTOs. `UsersDetail` has a unique normalized Email index in addition to MongoDB's unique `_id`. Additional indexes cover station active/name, slot station/start, reservation Prosumer/created and station/status. Indexes support the implemented queries; they do not replace service validation.

Initialization is repeatable and tolerates another API process creating a collection concurrently. MongoDB duplicate-key errors in user writes become HTTP 409 through the application error contract.

Local Compose uses MongoDB 7, localhost port 27017 and the named `smartsolar_mongo_data` volume (Compose prefixes its actual name). It has no database credentials and must remain local-only. Production MongoDB must use deployment-managed authentication/network restrictions; never put connection passwords into repository files.

Tests use a unique `SmartSolarTests_<guid>` database on `SMARTSOLAR_TEST_MONGO`, removing that exact test database afterward. They never clear `SmartSolarMicrogridDb`. Android SQLite `local_user` caches the API profile; it does not store passwords or perform enterprise validation.

## Accepted reservation schedules and query indexes

EnergyReservation stores nullable ScheduledStartAtUtc and ScheduledEndAtUtc accepted UTC snapshots. Creation/update copies server-resolved slot times. Missing legacy values stay null; reads and writes return 409 for invalid/missing accepted schedules in their authorized candidate scope instead of reconstructing them from mutable slot times. Repair requires trustworthy historical evidence and explicit review.

User.ReservationWriteLock is a durable per-Prosumer recovery boundary. A failed compensation can deliberately leave the lock set. It has no automatic expiry: inspect and reconcile the exact reservation and capacity writes before an authorized manual unlock. Do not clear it just to silence a conflict.

The repeatable MongoDbInitializer includes these reservation query/QR indexes:

| Index | Ascending keys | Justification |
| --- | --- | --- |
| ix_reservations_status_start | Status, ScheduledStartAtUtc | Global Pending count and Approved future range |
| ix_reservations_prosumer_status_start | ProsumerNic, Status, ScheduledStartAtUtc | Owner-scoped status counts and Approved future range |
| ux_reservations_qr_token_hash | QrTokenHash (Unique, PartialFilter: QrTokenHash is String) | Indexed server lookup for QR verification while allowing multiple null documents |

## QR and completion persistence

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
claim is made. The [API contract](API-CONTRACT.md) defines authorized scoping and repair errors.

## Station and slot contract

No collection or identifier was renamed. Station/slot IDs remain server-generated GUID strings mapped to MongoDB `_id`. Reservation references remain `StationId` and `SlotId`.

`SolarStationInfo` retains `Name`, `Address`, `Latitude`, `Longitude`, `CapacityKwh`, `TotalBatterySlots`, `IsActive`, `CreatedAtUtc`, `UpdatedAtUtc`. `OperatingSchedule` is a list of embedded `OperatingDay` objects:

| BSON field | Meaning |
| --- | --- |
| Day | ISO weekday integer: 1 Monday through 7 Sunday |
| IsClosed | Whether that entire UTC weekday is closed |
| OpensAt | Strict HH:mm UTC string, or null when closed |
| ClosesAt | Strict HH:mm UTC string; 24:00 allowed for end-of-day closing; null when closed |

Create/update requires exactly seven unique weekdays. Open days require opening before closing. A full day is 00:00–24:00; split overnight hours over adjacent days. All-closed is valid. UTC is the fixed schedule time basis; there is no inferred device timezone or new timezone field. Existing documents without this field deserialize to an empty list, displayed as **unconfigured**, and require a complete schedule on the next station edit. No bulk migration or fabricated default hours were applied.

`EnergyBookingSlots` retains the original `SlotId`/`_id`, `StationId`, `StartAtUtc`, `EndAtUtc`, `TotalSlots`, `AvailableSlots`, `IsActive`, `CreatedAtUtc`, `UpdatedAtUtc` fields. TotalSlots is battery-slot inventory and cannot exceed its parent station's TotalBatterySlots. It is not energy in kWh; no per-slot kWh field or conversion was invented.

Catalog writes preserve creation timestamps and references. Soft deactivation changes IsActive; documents are not deleted and parent deactivation does not rewrite child slot history. API DTOs are separate from domain objects. Mongo timestamps use UTC millisecond precision. Changed records advance UpdatedAtUtc by at least one millisecond, with an atomic ID + previous UpdatedAtUtc condition on each update.

The API checks for **Pending or Approved** reservations through IReservationReferenceReader against EnergyReservation, using the team's frozen rule. HasActiveStationReservationsAsync filters by StationId and those statuses; HasActiveSlotReservationsAsync filters by SlotId and those statuses. Rejected, Cancelled and Completed references do not block station deactivation or protected slot mutation. Queries use the existing ReservationStatus string-enum BSON mapping, never write reservations, and preserve every ID/reference. No schema, index or collection change is required. See [business rules](BUSINESS-RULES.md#frozen-active-reservation-protection-rule).

Nearby distance is transient Haversine distance in kilometres, calculated from stored coordinates after reading active stations. It is never persisted in MongoDB or SQLite. Existing indexes are retained; nearby is a linear scan suitable for the current development catalogue, not an indexed geospatial search or a claim of large-scale performance. No GeoJSON index or operator-assignment field is used.

A shared singleton `CatalogWriteGate` serializes station/slot consistency checks, reservation allocation/capacity changes and station energy checks on the supported single ASP.NET Core API instance hosted by IIS. The acquisition order is always `CatalogWriteGate`, then the durable per-Prosumer reservation/recovery lock when required. Atomic slot updates advance `UpdatedAtUtc`, so reservation allocation cannot be overwritten by a stale catalog write. Individual Mongo document updates retain compare-and-update protection; this remains coordination for one API process, not a distributed transaction. Completion keeps the allocated slot count because a completed reservation consumed its published booking place; Completed remains non-active for catalog protection and energy calculations.

## Embedded account and experience data

UsersDetail contains profile/contact information, salted password hashes, account/security versions, one-time verification/recovery token hashes and expiry metadata. Raw reset/verification tokens and passwords are not persisted. AvatarBytes holds the normalized JPEG inside the user document; AvatarVersion and ProfileComplete describe its current profile state. There is no local server upload directory or extra image collection.

Notifications and audit trails are bounded embedded arrays (100 retained entries per user/entity). Reservation events support notification delivery; the worker polls every ten seconds and deduplicates retained event IDs. Evicted history cannot provide permanent deduplication or a compliance archive. No Notification, Audit, QR, Transaction or PasswordReset collection is created.

EnergyReservation retains the legacy nullable QrToken field for compatibility; current issuance stores only QrTokenHash. Completion retains the accepted schedule, station/slot/Prosumer identifiers and completion operator/time. Decimal kWh values use the existing BSON mapping. The source of truth for mappings/indexes is MongoMappings/MongoDbInitializer; schema changes require review.

## Local Android persistence

SQLiteOpenHelper maintains smart_solar_local.db, schema version 1, table local_user. One profile is transactionally replaced after API verification. No password/hash, JWT, QR payload, avatar image or location is stored in this table. JWT/expiry use app-private preferences; backup/device transfer excludes session data and database files. Logout, expiry, matching 401 and account switching clear stale profile data. A cached row never authorizes a session; see [Database Inspector instructions](../mobile/SmartSolarMobile/README.md#verify-sqlite).

## Index inventory

In addition to each collection's unique _id, initialization retains ux_users_email, ix_users_name_prefix, ux_users_reset_hash (unique partial string hash), ix_stations_name_prefix, ix_stations_active_name, ix_slots_station_start, ix_reservations_prosumer_created, ix_reservations_station_status, ix_reservations_status_start, ix_reservations_prosumer_status_start and ux_reservations_qr_token_hash (unique partial string hash). Index creation is repeatable; not every filter/sort is fully covered.

Account writes compare/increment AccountVersion and preserve reservation lock fields. Legacy accounts without version fields are supported; existing active accounts are not automatically migrated to verified status. Prosumer email edits clear prior approval/verification and return active accounts to PendingActivation; already Deactivated accounts remain deactivated. Name/phone-only edits preserve state.
