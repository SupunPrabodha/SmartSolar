# API Contract — v1 (Member 3 Reservation Management)

Base path: `/api/v1`

## Foundation endpoints

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register-prosumer` | Anonymous | Register Prosumer in PendingActivation state |
| POST | `/auth/login` | Anonymous | Authenticate active user and issue JWT |
| GET | `/users/me` | Authenticated | Current user profile |
| PUT | `/users/me` | Authenticated | Update own profile |
| POST | `/users/me/deactivation-request` | Prosumer | Self-deactivate Prosumer account |
| GET | `/users` | Backoffice | List users |
| GET | `/users/pending` | Backoffice | Pending activation queue |
| GET | `/users/{nic}` | Backoffice | User details |
| POST | `/users/staff` | Backoffice | Create Backoffice/GridOperator user |
| PATCH | `/users/{nic}/activate` | Backoffice | Activate/reactivate user |
| PATCH | `/users/{nic}/deactivate` | Backoffice | Deactivate user |

Errors use `application/problem+json` and standard HTTP status codes.

---

## Shared wire format

JSON property names are camelCase. `role` is one of `Backoffice`, `GridOperator`, `Prosumer`; `status` is one of `PendingActivation`, `Active`, `Deactivated`. Enums are serialized as strings. NIC is the immutable business identifier. Passwords/hashes are never returned.

Login accepts `{ "nic": "<your NIC>", "password": "<your password>" }` and returns `accessToken`, `expiresAtUtc` (UTC timestamp) and `user`. Clients send `Authorization: Bearer <accessToken>`.

---

## Member 3: Energy Reservation Endpoints

All reservation operations require authentication and enforce strict role-based access control.

| Method | Route | Access | Purpose |
|---|---|---|---|
| `GET` | `/reservations` | GridOperator | Operational listing with exact filters (`status`, `prosumerNic`, `stationId`). Ordered by `createdAtUtc` DESC, then `reservationId`. |
| `GET` | `/reservations/my` | Prosumer | List all reservations belonging to the authenticated Prosumer. |
| `GET` | `/reservations/slots` | Prosumer, GridOperator | List active booking slots with available capacity starting within the 7-day booking horizon. |
| `POST` | `/reservations` | Prosumer | Self-service reservation creation for the authenticated Prosumer. Returns 201 with `Location` header. |
| `POST` | `/reservations/prosumers/{prosumerNic}` | GridOperator | Assisted reservation creation for an active Prosumer. Returns 201 with `Location` header. |
| `GET` | `/reservations/{reservationId}` | Owning Prosumer, GridOperator | Retrieve reservation summary details. Returns 200. |
| `PUT` | `/reservations/{reservationId}` | Owning Prosumer, GridOperator | Modify slot selection and/or energy amount. Returns updated summary in `Pending` status. |
| `PATCH` | `/reservations/{reservationId}/cancel` | Owning Prosumer, GridOperator | Cancel reservation (guarded state transition). Releases 1 slot capacity. Returns updated summary in `Cancelled` status. |
| `PATCH` | `/reservations/{reservationId}/approve` | GridOperator | Approve a pending reservation. Retains slot capacity. Returns updated summary in `Approved` status. |
| `PATCH` | `/reservations/{reservationId}/reject` | GridOperator | Reject a pending reservation with a mandatory remark. Releases 1 slot capacity. Returns updated summary in `Rejected` status. |

Backoffice accounts are strictly prohibited from accessing reservation endpoints (returns 403 Forbidden).

---

### Request and Response Contracts

#### 1. Create / Update Reservation Request Body
Both `POST /reservations`, `POST /reservations/prosumers/{prosumerNic}`, and `PUT /reservations/{reservationId}` accept:

```json
{
  "slotId": "a1b2c3d4e5f6789012345678abcdef01",
  "energyAmountKwh": 25.5
}
```

- `slotId` (string, required): Non-empty 32-character hex (N format) or hyphenated (D format) GUID string corresponding to an active `EnergyBookingSlot`.
- `energyAmountKwh` (decimal, required): Requested energy in kWh, strictly greater than zero (`> 0`). Must not exceed station energy capacity or available slot capacity.

#### 2. Reject Reservation Request Body (`PATCH /reservations/{reservationId}/reject`)
```json
{
  "remark": "Station solar panels scheduled for routine grid maintenance during this slot."
}
```

- `remark` (string, required): Non-empty reason string explaining why the reservation was rejected (1–500 characters).

#### 3. Reservation Response Summary
Returned by Create (201), GetById (200), Update (200), Cancel (200), Approve (200), and Reject (200):

```json
{
  "reservationId": "f9e8d7c6b5a43210fedcba9876543210",
  "prosumerNic": "951234567V",
  "stationId": "11223344556677889900aabbccddeeff",
  "slotId": "a1b2c3d4e5f6789012345678abcdef01",
  "energyAmountKwh": 25.5,
  "scheduledStartAtUtc": "2026-09-28T10:00:00Z",
  "scheduledEndAtUtc": "2026-09-28T11:00:00Z",
  "status": "Pending",
  "createdAtUtc": "2026-09-25T00:00:00Z",
  "updatedAtUtc": "2026-09-25T00:00:00Z",
  "rejectionRemark": null
}
```

- All timestamps are ISO 8601 UTC strings (`Z` suffix).
- `status`: String enum (`Pending`, `Approved`, `Rejected`, `Cancelled`, `Completed`).
- `rejectionRemark`: String or `null`. Present when `status` is `Rejected`.
- Internal security tokens (such as `qrToken`) and concurrency locks are never exposed over the API wire.

#### 4. Available Slots Response Item (`GET /reservations/slots`)
```json
[
  {
    "slotId": "a1b2c3d4e5f6789012345678abcdef01",
    "stationId": "11223344556677889900aabbccddeeff",
    "startAtUtc": "2026-09-28T10:00:00Z",
    "endAtUtc": "2026-09-28T11:00:00Z",
    "availableSlots": 4,
    "totalSlots": 5
  }
]
```

---

### HTTP Status Codes & Error Handling

Standard `application/problem+json` RFC 7807 responses are returned for all error scenarios:

| HTTP Status | Trigger Condition |
|---|---|
| `400 Bad Request` | Malformed JSON, non-GUID `slotId`, non-positive `energyAmountKwh`, missing/whitespace rejection remark, schedule `<= 0` duration, booking start in the past or beyond 7 days (`> 7 days`), requested energy exceeds station capacity. |
| `401 Unauthorized` | Missing, invalid, expired Bearer token, or user account not found in database. |
| `403 Forbidden` | Role unauthorized (e.g. Backoffice accessing reservations, Prosumer accessing other prosumer's reservation, Prosumer invoking operator endpoints such as approve/reject). |
| `404 Not Found` | Reservation ID, Slot ID, Station ID, or assisted Prosumer NIC does not exist. |
| `409 Conflict` | Schedule notice `< 12 hours` on update/cancel, approving an expired/past schedule, approving/rejecting a non-Pending reservation, overlapping reservation on same Prosumer, slot full / inactive, station cumulative energy capacity exceeded, terminal state modification, concurrent modification race (CAS miss), or active reservation lock requiring reconciliation. |
| `500 Internal Server Error` | Unexpected server failure (internal error details are masked). |
