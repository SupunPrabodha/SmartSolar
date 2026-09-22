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
