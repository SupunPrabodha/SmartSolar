# Business Rules & Lifecycle Validation (Member 3 Reservation Management)

All authoritative business logic is enforced within the C# application layer (`ReservationRules.cs` and `ReservationService.cs`).

---

## 1. Booking Horizon Rules

- **UTC Enforcement**: All start and end timestamps must have `DateTimeKind.Utc`. Local or unspecified timestamps are rejected with `400 Bad Request`.
- **Duration Validity**: Schedule duration must be strictly positive (`EndAtUtc > StartAtUtc`). Zero or negative durations are rejected with `400 Bad Request`.
- **Lower Horizon Boundary**: Reservation start must be strictly in the future (`StartAtUtc > Server UtcNow`). Past and current instants (`<= UtcNow`) are rejected with `400 Bad Request`.
- **Upper Horizon Boundary (7-Day Rule)**: 
  - A reservation start time must not exceed 7 days from the server instant (`StartAtUtc - UtcNow <= 7 days`).
  - Exactly 7 days (604,800 seconds) is permitted (inclusive upper boundary).
  - 7 days + 1 second (604,801 seconds) is rejected with `400 Bad Request`.

---

## 2. Modification & Cancellation Notice (12-Hour Cutoff)

- **Update Notice Rule**:
  - The existing accepted reservation start time must be at least 12 hours away from the server instant (`ScheduledStartAtUtc - UtcNow >= 12 hours`).
  - Exactly 12 hours (43,200 seconds) notice is permitted.
  - Less than 12 hours (e.g. 43,199 seconds / 11h 59m 59s) is rejected with `409 Conflict`.
  - The new replacement slot start time must also satisfy `>= 12 hours` notice and `<= 7 days` horizon. Moving to a later date cannot bypass the 12-hour cutoff on the current schedule.
- **Cancellation Notice Rule**:
  - Cancellation requires at least 12 hours notice before the scheduled start time (`ScheduledStartAtUtc - UtcNow >= 12 hours`).
  - Exactly 12 hours notice is permitted.
  - Less than 12 hours notice is rejected with `409 Conflict`.
- **Operator Assistance Notice**:
  - Grid Operator assisted modifications and cancellations obey the exact same 12-hour cutoff and 7-day horizon rules.

---

## 3. Station Energy Capacity & Overbooking Rules

- **Station Energy Limit**:
  - `EnergyAmountKwh` must be strictly greater than zero (`> 0`).
  - `EnergyAmountKwh` cannot exceed the total `CapacityKwh` configured on the parent `SolarStationInfo`.
- **Cumulative Slot Energy Allocation**:
  - The sum of active reservations (`Pending` and `Approved`) on a given slot plus the newly requested kWh must not exceed the station's `CapacityKwh` (`AllocatedKwh + RequestedKwh <= Station.CapacityKwh`).
  - Over-allocation is rejected with `409 Conflict`.
- **Slot Capacity Count**:
  - Booking slots enforce maximum concurrent reservations via `AvailableSlots > 0` decrement. Full or inactive slots are rejected with `409 Conflict`.

---

## 4. Overlap & Conflict Prevention

- **Per-Prosumer Serialization**:
  - A single Prosumer cannot have overlapping active reservations across any stations or slots.
  - Active reservation states that consume capacity: `Pending` and `Approved`.
  - Inactive / terminal states that do not conflict: `Rejected`, `Cancelled`, `Completed`.
- **Half-Open Interval Check**:
  - Evaluated using: `ExistingStart < RequestedEnd AND RequestedStart < ExistingEnd`.
  - **Adjacency is allowed**: Back-to-back intervals (e.g. 10:00–11:00 and 11:00–12:00) do NOT overlap.
  - Subsets, supersets, and partial overlaps are strictly rejected with `409 Conflict`.

---

## 5. Status State Machine & Transitions

```
                 [ Create ]
                     │
                     ▼
                 ┌─────────┐
                 │ Pending │◀─────────────┐
                 └────┬────┘              │
                      │                   │
         ┌────────────┼────────────┐      │ Update
         │ (Approve)  │ (Reject)   │      │ (Notice >= 12h)
         │ Future     │ + Remark   │      │
         ▼            ▼            │      │
   ┌──────────┐  ┌──────────┐      │      │
   │ Approved │  │ Rejected │      │      │
   └─────┬────┘  └──────────┘      │      │
         │                         │      │
         ├─────────────────────────┼──────┘
         │                         │
         │ (Cancel)                │ (Cancel)
         │ Notice >= 12h           │ Notice >= 12h
         ▼                         ▼
   ┌───────────┐             ┌───────────┐
   │ Cancelled │             │ Cancelled │
   └───────────┘             └───────────┘
         │
         │ (Complete - Member 4)
         ▼
   ┌───────────┐
   │ Completed │
   └───────────┘
```

- **Approval Policy**:
  - Only reservations in `Pending` status can be approved.
  - The scheduled start time must be strictly in the future (`ScheduledStartAtUtc > UtcNow`). Attempting to approve an expired schedule returns `409 Conflict`.
  - Authorized role: `GridOperator`.
  - Transitions status to `Approved`. The slot capacity remains allocated.
- **Rejection Policy**:
  - Only reservations in `Pending` status can be rejected.
  - A non-empty rejection remark is strictly required (`1..500` characters). Empty, null, or whitespace-only remarks are rejected with `400 Bad Request`.
  - Authorized role: `GridOperator`.
  - Transitions status to `Rejected`, records `RejectionRemark`, and atomically releases 1 slot capacity back to `EnergyBookingSlots`.
  - Prosumers can inspect the rejection reason on their Android client.
- **Reapproval Policy**: Modifying an `Approved` reservation reverts its status to `Pending` and clears any existing `QrToken`.
- **Cancellation Policy**: Transitions status to `Cancelled`, clears `QrToken`, and atomically releases 1 available slot capacity back to the slot.
- **Terminal States**: `Rejected`, `Cancelled`, and `Completed` are immutable. Any attempt to update, approve, reject, or cancel a terminal reservation is rejected with `409 Conflict`.

---

## 6. Role & Ownership Authorization

- **Prosumer (Self-Service)**:
  - Can create reservations for their own NIC only (`POST /api/v1/reservations`).
  - Can view their own reservations (`GET /api/v1/reservations/my`, `GET /api/v1/reservations/{id}`).
  - Can view rejection remark on rejected reservations.
  - Can update or cancel their own active reservations (`PUT /api/v1/reservations/{id}`, `PATCH /api/v1/reservations/{id}/cancel`).
  - Can view available slots (`GET /api/v1/reservations/slots`).
  - Cannot access or manipulate another Prosumer's reservation (returns `403 Forbidden`).
  - Cannot approve or reject reservations (returns `403 Forbidden`).
- **Grid Operator (Assisted & Operational Management)**:
  - Can list reservations with filters (`GET /api/v1/reservations`).
  - Can create assisted reservations on behalf of active Prosumers (`POST /api/v1/reservations/prosumers/{nic}`).
  - Can view, update, and cancel any reservation subject to all business rules.
  - Can approve pending reservations (`PATCH /api/v1/reservations/{id}/approve`).
  - Can reject pending reservations with a mandatory remark (`PATCH /api/v1/reservations/{id}/reject`).
- **Backoffice**:
  - Strictly prohibited from reservation operations (returns `403 Forbidden`).
