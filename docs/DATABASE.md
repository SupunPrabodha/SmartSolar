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

## Member 1 station and slot contract

No collection or identifier was renamed. Station/slot IDs remain server-generated GUID strings mapped to MongoDB `_id`. Reservation references remain `StationId` and `SlotId`.

`SolarStationInfo` retains `Name`, `Address`, `Latitude`, `Longitude`, `CapacityKwh`, `TotalBatterySlots`, `IsActive`, `CreatedAtUtc`, `UpdatedAtUtc`. **The only domain-field addition is `OperatingSchedule`**, a list of embedded `OperatingDay` objects:

| BSON field | Meaning |
| --- | --- |
| Day | ISO weekday integer: 1 Monday through 7 Sunday |
| IsClosed | Whether that entire UTC weekday is closed |
| OpensAt | Strict HH:mm UTC string, or null when closed |
| ClosesAt | Strict HH:mm UTC string; 24:00 allowed for end-of-day closing; null when closed |

Create/update requires exactly seven unique weekdays. Open days require opening before closing. A full day is 00:00–24:00; split overnight hours over adjacent days. All-closed is valid. UTC is the fixed schedule time basis; there is no inferred device timezone or new timezone field. Existing documents without this field deserialize to an empty list, displayed as **unconfigured**, and require a complete schedule on the next station edit. No bulk migration or fabricated default hours were applied.

`EnergyBookingSlots` retains the original `SlotId`/`_id`, `StationId`, `StartAtUtc`, `EndAtUtc`, `TotalSlots`, `AvailableSlots`, `IsActive`, `CreatedAtUtc`, `UpdatedAtUtc` fields. TotalSlots is battery-slot inventory and cannot exceed its parent station's TotalBatterySlots. It is not energy in kWh; no per-slot kWh field or conversion was invented.

Member 1 writes preserve creation timestamps and references. Soft deactivation changes IsActive; documents are not deleted and parent deactivation does not rewrite child slot history. API DTOs are separate from domain objects. Mongo timestamps use UTC millisecond precision. Changed records advance UpdatedAtUtc by at least one millisecond, with an atomic ID + previous UpdatedAtUtc condition on each update.

The API checks for **Pending or Approved** reservations through IReservationReferenceReader against EnergyReservation, using the team's frozen rule. HasActiveStationReservationsAsync filters by StationId and those statuses; HasActiveSlotReservationsAsync filters by SlotId and those statuses. Rejected, Cancelled and Completed references do not block station deactivation or protected slot mutation. Queries use the existing ReservationStatus string-enum BSON mapping, never write reservations, and preserve every ID/reference. No schema, index or collection change is required. See [business rules](BUSINESS-RULES.md#frozen-active-reservation-protection-rule).

Nearby distance is transient Haversine distance in kilometres, calculated from stored coordinates after reading active stations. It is never persisted in MongoDB or SQLite. Existing indexes are retained; nearby is a linear scan suitable for the current development catalogue, not an indexed geospatial search or a claim of large-scale performance. No GeoJSON field, new collection, reservation scheduling field, lock document or operator assignment field was added.

A shared in-process gate serializes station/slot consistency checks on the current single API instance. Individual document updates also have Mongo compare-and-update protection. This is **not** a distributed transaction across stations, slots and reservations. Multi-instance writes and concurrent Member 3 booking allocation require a reviewed coordination contract before integration.
