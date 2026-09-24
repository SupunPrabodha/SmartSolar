# MongoDB Database Design & Persistence (Member 3 Reservation Management)

## Required Collections

- `UsersDetail` (Collection: `UsersDetail`)
- `SolarStationInfo` (Collection: `SolarStationInfo`)
- `EnergyBookingSlots` (Collection: `EnergyBookingSlots`)
- `EnergyReservation` (Collection: `EnergyReservation`)

---

## Identifier Strategy & Mapping

- **User/Prosumer**: `Nic` (string, e.g. `951234567V` or 12-digit) mapped to MongoDB `_id`.
- **Station**: `StationId` (string GUID, e.g. `11223344556677889900aabbccddeeff`) mapped to MongoDB `_id`.
- **Slot**: `SlotId` (string GUID) mapped to MongoDB `_id`.
- **Reservation**: `ReservationId` (string GUID) mapped to MongoDB `_id`.
- Foreign entity references are stored as string identifier fields (`ProsumerNic`, `StationId`, `SlotId`).
- All persisted server timestamps are standard UTC `DateTime`.

---

## Member 3 Schema Details

### 1. `EnergyReservation` Entity Schema
| Field | BSON Type | Nullable | Purpose / Description |
|---|---|---|---|
| `ReservationId` | String (GUID) | No | Primary key (`_id`). Stable unique reservation identifier. |
| `ProsumerNic` | String | No | Owner prosumer business identity (`NIC`). |
| `StationId` | String (GUID) | No | Referenced Solar Station identifier. |
| `SlotId` | String (GUID) | No | Referenced booking slot identifier. |
| `EnergyAmountKwh` | Decimal128 | No | Reserved energy amount in kWh. Strictly `> 0`. |
| `Status` | String (Enum) | No | Enum stored as string: `Pending`, `Approved`, `Rejected`, `Cancelled`, `Completed`. |
| `ScheduledStartAtUtc` | UTC DateTime | Yes (Legacy) | Snapshot of accepted slot start time. Required for authoritative rule checks. |
| `ScheduledEndAtUtc` | UTC DateTime | Yes (Legacy) | Snapshot of accepted slot end time. Required for half-open interval overlap checks. |
| `QrToken` | String | Yes | Transaction security token generated upon staff approval. Cleared on modify/cancel. |
| `CreatedAtUtc` | UTC DateTime | No | Server timestamp when reservation was initially created. |
| `UpdatedAtUtc` | UTC DateTime | No | Server timestamp of last modification. Used for optimistic concurrency CAS. |

### 2. `EnergyBookingSlot` Entity Schema (Capacity Management)
| Field | BSON Type | Purpose / Description |
|---|---|---|
| `SlotId` | String (GUID) | Primary key (`_id`). |
| `StationId` | String (GUID) | Owning solar station ID. |
| `StartAtUtc` | UTC DateTime | Slot window start instant. |
| `EndAtUtc` | UTC DateTime | Slot window end instant. |
| `TotalSlots` | Int32 | Maximum concurrent reservations allowed for this slot. |
| `AvailableSlots` | Int32 | Remaining available reservation slots. Decremented conditionally on create/move; incremented on cancel/move. |
| `IsActive` | Boolean | Administrative slot status flag. |

### 3. `UsersDetail` Entity Schema (Reservation Concurrency Lock)
| Field | BSON Type | Nullable | Purpose / Description |
|---|---|---|---|
| `ReservationWriteLock` | String (GUID) | Yes | Durable non-expiring reservation mutex token per Prosumer. Prevents concurrent overlapping bookings. |

---

## MongoDB Indexes

Configured via `MongoDbInitializer`:

1. **`UsersDetail` Collection**:
   - `_id` (Unique, default)
   - `ux_users_email`: `{ Email: 1 }` (Unique)
2. **`SolarStationInfo` Collection**:
   - `_id` (Unique, default)
   - `ix_stations_active_name`: `{ IsActive: 1, Name: 1 }`
3. **`EnergyBookingSlots` Collection**:
   - `_id` (Unique, default)
   - `ix_slots_station_start`: `{ StationId: 1, StartAtUtc: 1 }`
4. **`EnergyReservation` Collection**:
   - `_id` (Unique, default)
   - `ix_reservations_prosumer_created`: `{ ProsumerNic: 1, CreatedAtUtc: -1 }` (Accelerates Prosumer history queries)
   - `ix_reservations_station_status`: `{ StationId: 1, Status: 1 }` (Accelerates station-level aggregation and filtering)

---

## Concurrency & Atomicity Strategy (Standalone Safe)

1. **Slot Capacity Allocation**:
   - Conditional atomic update with filter: `AvailableSlots > 0 AND AvailableSlots <= TotalSlots AND IsActive = true`.
   - Decrements `AvailableSlots` by 1 using `$inc: -1`.
   - Confirmed CAS miss prevents overbooking even across independent API instances.
2. **Slot Capacity Release**:
   - Conditional atomic update with filter: `AvailableSlots < TotalSlots AND AvailableSlots >= 0`.
   - Increments `AvailableSlots` by 1 using `$inc: 1`. Prevents overflowing total configured slots.
3. **Prosumer Write Mutex Lock**:
   - Conditional update on `UsersDetail`: `ReservationWriteLock == null`. Sets a unique GUID token.
   - Released exclusively by the matching lock token upon successful completion.
   - Protects against multi-slot overlap races across separate API processes.
4. **Optimistic Concurrency on Updates (CAS)**:
   - Updates compare all existing entity state including `UpdatedAtUtc`. A mismatch rejects with 409 Conflict and triggers compensation.
5. **Legacy Document Policy**:
   - Missing schedule snapshots in legacy documents are handled gracefully by fail-closed policy (requires verified backfill, rejecting operations with 409).
