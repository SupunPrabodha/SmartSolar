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
