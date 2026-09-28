# API Contract — v1

Base path: `/api/v1`

QR issuance is restricted to the owning active Prosumer. GridOperators may verify and complete, but cannot issue or rotate QR references; Backoffice cannot issue, verify or complete. Verification and completion require the accepted snapshot window (`start <= server now < end`) and active, correctly linked Prosumer, station and slot records.

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
| PUT | `/users/{nic}` | Backoffice | Update editable contact fields for a Prosumer only |
| POST | `/users/staff` | Backoffice | Create Backoffice/GridOperator user |
| PATCH | `/users/{nic}/activate` | Backoffice | Activate/reactivate user |
| PATCH | `/users/{nic}/deactivate` | Backoffice | Deactivate user |

Errors use `application/problem+json` and suitable HTTP status codes.

## Shared wire format

JSON property names are camelCase. `role` is one of `Backoffice`, `GridOperator`, `Prosumer`; `status` is one of `PendingActivation`, `Active`, `Deactivated`. Enums are strings, not numbers; staff creation requires an explicit `role`. NIC is the immutable business identifier. Password hashes are never returned.

Login accepts `{ "nic": "<your NIC>", "password": "<your password>" }` and returns `accessToken`, `expiresAtUtc` (UTC timestamp) and `user`. Clients send `Authorization: Bearer <accessToken>`. Registration returns 201 and a PendingActivation Prosumer; it does not issue a token. Login returns 401 for invalid credentials and 403 for pending/deactivated accounts. Authentication rejects tokens whose stored account is missing, inactive or has a changed role.

Profile and staff DTOs require name (2-120 characters), email, phone (7-20 characters), and, for creation, password (8-100 characters) and NIC (12 digits or 9 digits plus V/X). Email/NIC are normalized server-side. Duplicate identities/emails return 409, including database uniqueness races. Invalid inputs return 400; missing users return 404; successful lifecycle changes return 204. Error bodies include `status`, `title`, optional `detail`/validation `errors`, and a trace identifier. Unexpected 500 errors do not expose exception details.

`PUT /users/{nic}` is a Backoffice-only Prosumer contact-profile operation. It accepts `fullName`, `email`, and `phoneNumber`; NIC, role, account status, and password are immutable through this endpoint. Prosumer self-profile updates use `PUT /users/me`; Android clears its token and local cached profile only after a successful self-deactivation response.

`GET /health` is outside `/api/v1`: 200/Healthy when MongoDB responds, 503 when unavailable. Swagger UI `/swagger` and OpenAPI `/swagger/v1/swagger.json` are available only in Development. CORS origins are configured in `Cors:AllowedOrigins`; these are browser access settings, not authorization.

No station, booking, reservation, Maps or QR feature endpoints are implemented in Phase 0.

## Implemented Member 3 reservation lifecycle

All paths below follow /api/v1. Backoffice is excluded. Creation/update accepts only slotId and energyAmountKwh; identity, station, status and accepted schedule come from the server.

| Method | Route | Access |
| --- | --- | --- |
| GET | /reservations | GridOperator, exact status/prosumerNic/stationId filters |
| GET | /reservations/my | Prosumer, own records |
| GET | /reservations/slots | Prosumer or GridOperator |
| POST | /reservations | Prosumer |
| POST | /reservations/prosumers/{prosumerNic} | GridOperator assistance |
| GET | /reservations/{reservationId} | Owning Prosumer or GridOperator |
| PUT | /reservations/{reservationId} | Owning Prosumer or GridOperator |
| PATCH | /reservations/{reservationId}/cancel | Owning Prosumer or GridOperator |
| PATCH | /reservations/{reservationId}/approve | GridOperator |
| PATCH | /reservations/{reservationId}/reject | GridOperator; remark required (1–500 characters) |

Create returns 201; reads and mutations return 200 summaries. Summary fields include reservationId, prosumerNic, stationId, slotId, energyAmountKwh, scheduledStartAtUtc, scheduledEndAtUtc, status, createdAtUtc, updatedAtUtc and optional rejectionRemark. Booking query summaries preserve the same rejection remark. QR credentials/internal locks are excluded.

Start must be in the future and at most seven elapsed days away. Updates/cancellations require at least twelve hours before the accepted start; replacement starts also require twelve hours and the seven-day horizon. Updates return Pending for reapproval and clear QR data. Pending can become Approved or Rejected; rejection requires a remark. Pending/Approved can become Cancelled. Terminal records cannot be updated/cancelled.

See [Member 3 API detail](MEMBER-3-API-CONTRACT.md) and [final audit](FINAL-INTEGRATION-AUDIT.md) for observed implementation limits. Snapshot, concurrency and completion findings remain blockers; these routes' existence is not an end-to-end safety guarantee.

## Member 4 steps 2–5: implemented reservation reads

These endpoints are implemented in addition to the existing Member 3 routes.
All require an active Prosumer or GridOperator. Prosumer identity comes from the
authenticated account; only its own records are visible. GridOperators can view
all reservations or narrow the list. Backoffice remains forbidden.

| Method | Route under /api/v1 | Definition |
| --- | --- | --- |
| GET | /reservations/current | Pending/Approved with accepted end strictly after server UTC now |
| GET | /reservations/pending | Exact Pending status, regardless of date |
| GET | /reservations/history | Rejected/Cancelled/Completed, plus Pending/Approved whose accepted end is at or before now |
| GET | /reservations/search | Authorized records matching controlled filters |
| GET | /reservations/dashboard-summary | Live pending and approved-future counts |

All four list endpoints accept these optional query parameters, combined with AND:

| Parameter | Validation / semantics |
| --- | --- |
| reservationId | Nonempty GUID string; exact persisted representation, trimmed |
| prosumerNic | 12 digits or 9 digits plus V/X; trimmed and uppercased; another NIC is forbidden for a Prosumer |
| stationId | Nonempty GUID string; exact persisted representation, trimmed |
| status | Pending, Approved, Rejected, Cancelled, Completed (case-insensitive names); numeric/composite/unknown values rejected |
| fromUtc | Inclusive lower bound on accepted ScheduledStartAtUtc; send ISO-8601 UTC with Z |
| toUtc | Inclusive upper bound on accepted ScheduledStartAtUtc; must be >= fromUtc; send UTC with Z |
| page | 1–10000, default 1 |
| pageSize | 1–100, default 20 |

Blank string filters are omitted. N and D GUID formats are accepted, without
rewriting their persisted string representation. Dates never filter CreatedAtUtc.
There is no free-text `query`, raw regex or raw Mongo expression parameter. Unknown
parameters have no effect under the existing MVC binding convention; they cannot
override authority. For example `status=Approved` on the pending view yields no
matches. Invalid recognized filters return 400 ProblemDetails.

Example: `GET /api/v1/reservations/search?status=Approved&fromUtc=2030-01-01T00:00:00Z&page=1&pageSize=20`.

The list response is `{ "items": [ReservationResponse], "page": 1, "pageSize": 20,
"hasMore": false }`. Items reuse the existing camelCase summary with string status,
UTC snapshots and no QrToken. Empty or out-of-range result pages return items `[]`
and hasMore `false`, with HTTP 200. Current/pending order by scheduledStartAtUtc
ascending, then reservationId ascending; history/search use start descending,
then reservationId ascending. Follow hasMore; no total count is calculated for
pages. Sorting is deterministic for unchanged data, not a cross-request snapshot.

Dashboard response (illustrative zero-result values):

```json
{
  "pendingReservations": 0,
  "approvedFutureReservations": 0,
  "generatedAtUtc": "2030-01-01T00:00:00Z"
}
```

Pending means exact Pending status. Approved future means exact Approved status
and ScheduledStartAtUtc strictly later than generatedAtUtc, captured from the
server clock. There are no dashboard identity/time/filter parameters. Prosumer
counts are own-only; GridOperator counts are operational totals. Both are live
Mongo counts, not hardcoded values or client calculations; concurrent writes may
occur between their separate reads.

Errors preserve application/problem+json: 400 invalid filters, 401 missing/invalid/
revoked authentication, 403 role or explicit owner-scope violation, 409 snapshots
requiring verified backfill, and 500 safe unexpected failure. Snapshot checks run
before temporal filtering and pagination to avoid silently hiding ambiguous
records. Approved legacy snapshots block the dashboard summary; missing Pending
snapshots do not alter its status-only pending count. See
[the handshake](MEMBER-4-RESERVATION-CONTRACT.md) for exact preflight scope.

## Member 4 steps 7–9: implemented QR issuance and verification

These endpoints implement the secure QR lifecycle for approved reservations and grid operator verification:

| Method | Route under /api/v1 | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/reservations/{reservationId}/qr` | Owning Prosumer (Active) | Generate/rotate secure opaque transaction QR reference |
| POST | `/reservations/qr/verify` | GridOperator (Active) | Server-side verification of scanned opaque QR payload |

### QR Issuance Endpoint: `POST /api/v1/reservations/{reservationId}/qr`
- **Authorization**: Active `Prosumer` owning the specified `reservationId`.
- **Preconditions**:
  - Reservation must exist.
  - Authenticated user must be the owner (`prosumerNic`).
  - Authoritative database status must be strictly `Approved`.
- **Security & Rotation**:
  - Generates a 256-bit cryptographically secure random token (`RandomNumberGenerator`).
  - Prepares opaque transport payload: `SMG1.<base64url-token>`.
  - Stores SHA-256 hash (`QrTokenHash`) and `QrIssuedAtUtc` in MongoDB `EnergyReservation` document.
  - Reissuing rotates the token hash, immediately invalidating any prior QR references.
- **Response** (200 OK):
  ```json
  {
    "reservationId": "11111111-1111-1111-1111-111111111111",
    "qrPayload": "SMG1.g8_4Xz...opaque_payload...",
    "issuedAtUtc": "2030-05-10T10:00:00Z"
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Malformed or empty reservation ID.
  - `401 Unauthorized`: Missing or invalid JWT.
  - `403 Forbidden`: User is not an active Prosumer or does not own this reservation.
  - `404 Not Found`: Reservation does not exist.
  - `409 Conflict`: Reservation status is not `Approved` (e.g. `Pending`, `Rejected`, `Cancelled`, `Completed`).

### QR Verification Endpoint: `POST /api/v1/reservations/qr/verify`
- **Authorization**: Active `GridOperator`.
- **Request Body**:
  ```json
  {
    "qrPayload": "SMG1.g8_4Xz...opaque_payload..."
  }
  ```
- **Verification Process**:
  1. Validates payload format (`SMG1.` prefix and valid length).
  2. Computes SHA-256 hash of the extracted raw token.
  3. Queries `EnergyReservation` collection by `QrTokenHash`.
  4. Verifies authoritative database status is strictly `Approved`.
  5. Requires the accepted snapshot window and active, correctly linked related records.
- **Response** (200 OK):
  ```json
  {
    "reservationId": "11111111-1111-1111-1111-111111111111",
    "prosumerNic": "200012345678",
    "stationId": "22222222-2222-2222-2222-222222222222",
    "slotId": "33333333-3333-3333-3333-333333333333",
    "energyAmountKwh": 25.0,
    "scheduledStartAtUtc": "2030-05-10T10:00:00Z",
    "scheduledEndAtUtc": "2030-05-10T11:00:00Z",
    "status": "Approved",
    "qrIssuedAtUtc": "2030-05-10T09:30:00Z",
    "eligibleForCompletion": true
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Missing or malformed QR payload (e.g., missing `SMG1.` prefix).
  - `401 Unauthorized`: Missing or invalid JWT.
  - `403 Forbidden`: Caller is not an active `GridOperator` (Prosumers and Backoffice users are forbidden).
  - `404 Not Found`: QR reference unknown, revoked, or invalidated by rotation.
  - `409 Conflict`: Known reservation found, but current status is not `Approved` (`Pending`, `Rejected`, `Cancelled`, or `Completed`).

### Step 10: Implemented Transaction Completion Endpoint: `POST /api/v1/reservations/qr/complete`
- **Authorization**: Active `GridOperator` (operator NIC resolved securely from authenticated server context).
- **Request Body**:
  ```json
  {
    "qrPayload": "SMG1.g8_4Xz...opaque_payload...",
    "reservationId": "11111111-1111-1111-1111-111111111111"
  }
  ```
- **Revalidation & Execution Process**:
  1. Authenticates caller role as active `GridOperator`.
  2. Revalidates QR payload format and computes SHA-256 hash.
  3. Re-reads current authoritative state from MongoDB.
  4. Enforces that reservation is currently `Approved` (rejects `Pending`, `Rejected`, `Cancelled`, or `Completed`).
  5. Performs atomic conditional MongoDB update (`Status == Approved` -> `Status = Completed`, setting `CompletedAtUtc` to server UTC and `CompletedByOperatorNic` to caller NIC).
  6. Replay / Duplicate Completion Protection: If already completed, returns `409 Conflict`.
- **Response** (200 OK):
  ```json
  {
    "reservationId": "11111111-1111-1111-1111-111111111111",
    "prosumerNic": "200012345678",
    "stationId": "22222222-2222-2222-2222-222222222222",
    "slotId": "33333333-3333-3333-3333-333333333333",
    "energyAmountKwh": 25.0,
    "scheduledStartAtUtc": "2030-05-10T10:00:00Z",
    "scheduledEndAtUtc": "2030-05-10T11:00:00Z",
    "status": "Completed",
    "completedAtUtc": "2030-05-10T10:15:00Z",
    "completedByOperatorNic": "199012345678",
    "updatedAtUtc": "2030-05-10T10:15:00Z"
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: Malformed or missing QR payload.
  - `401 Unauthorized`: Missing or invalid JWT.
  - `403 Forbidden`: Caller is not an active `GridOperator`.
  - `404 Not Found`: QR reference unknown or invalid.
  - `409 Conflict`: Reservation already completed, cancelled, rejected, pending, or concurrent status modification.


## Member 1 endpoints

These endpoints extend the historical Phase-0 foundation. All require a valid JWT for a currently Active account. All paths below follow `/api/v1`.

| Method | Route | Role | Success |
| --- | --- | --- | --- |
| GET | /stations?search=&includeInactive=false | Backoffice, GridOperator, Prosumer | 200 station array |
| GET | /stations/nearby?latitude=&longitude=&radiusKm=25 | All three roles | 200 nearby array |
| GET | /stations/{id} | All three roles | 200 station |
| POST | /stations | Backoffice | 201 station + Location |
| PUT | /stations/{id} | Backoffice | 200 updated station |
| PATCH | /stations/{id}/deactivate | Backoffice | 204 |
| GET | /stations/{stationId}/slots?includeInactive=false | All three roles | 200 slot array |
| GET | /slots/{id} | All three roles | 200 slot |
| POST | /stations/{stationId}/slots | GridOperator | 201 slot + Location |
| PUT | /slots/{id} | GridOperator | 200 updated slot |
| PATCH | /slots/{id}/availability | GridOperator | 200 updated slot |
| PATCH | /slots/{id}/deactivate | GridOperator | 204 |

Backoffice does not implicitly inherit GridOperator writes. Roles are global, as in the existing account model; no station assignment policy/field has been invented. Prosumer cannot request includeInactive=true (403) and cannot read inactive stations or slots, including slots under inactive parents (404). Staff may inspect inactive records. Nearby always excludes inactive stations, including for staff.

Station request example (illustrative values, not seeded data):

```json
{
  "name": "Example node",
  "address": "Example road",
  "latitude": 6.9271,
  "longitude": 79.8612,
  "capacityKwh": 50,
  "totalBatterySlots": 10,
  "operatingSchedule": [
    { "day": 1, "isClosed": false, "opensAt": "08:00", "closesAt": "17:00" },
    { "day": 2, "isClosed": false, "opensAt": "08:00", "closesAt": "17:00" },
    { "day": 3, "isClosed": false, "opensAt": "08:00", "closesAt": "17:00" },
    { "day": 4, "isClosed": false, "opensAt": "08:00", "closesAt": "17:00" },
    { "day": 5, "isClosed": false, "opensAt": "08:00", "closesAt": "17:00" },
    { "day": 6, "isClosed": true, "opensAt": null, "closesAt": null },
    { "day": 7, "isClosed": true, "opensAt": null, "closesAt": null }
  ]
}
```

Name: 2–120 characters after required-field validation; address: 3–300; both are trimmed and rechecked after trimming. Latitude must be finite -90..90 and longitude finite -180..180. CapacityKwh > 0; TotalBatterySlots is an integer > 0. Schedule semantics are in [DATABASE.md](DATABASE.md#member-1-station-and-slot-contract). Station response adds stationId, isActive, createdAtUtc, updatedAtUtc.

PUT is a complete editable-field update. Include **expectedUpdatedAtUtc with the exact updatedAtUtc from the latest response**. This timestamp is also required in both deactivation PATCH bodies, and in slot PUT/availability PATCH. Missing timestamp returns 400; stale timestamp returns 409. Reload and review before retrying. Clients cannot set IsActive through PUT, change IDs/parent references, or bypass soft-deactivation guards.

Slot POST/PUT body: `{ "startAtUtc": "2030-01-01T08:00:00Z", "endAtUtc": "2030-01-01T09:00:00Z", "totalSlots": 5, "availableSlots": 5 }`; PUT additionally needs expectedUpdatedAtUtc. Send ISO timestamps with Z or an explicit offset; the API stores and returns UTC, supporting millisecond precision. Slot response adds slotId, stationId, isActive, createdAtUtc, updatedAtUtc. Availability PATCH: `{ "availableSlots": 3, "expectedUpdatedAtUtc": "<latest timestamp>" }`. Deactivation PATCH: `{ "expectedUpdatedAtUtc": "<latest timestamp>" }`.

Start must precede end, TotalSlots > 0, 0 <= AvailableSlots <= TotalSlots. Parent station must exist and be active for creation/editing usable inventory. TotalSlots cannot exceed TotalBatterySlots. Active inventory windows for one station cannot overlap; touching endpoints are allowed. Slot lists are ordered by start then ID and are published inventory, not a definition of current/future reservations. Station schedule describes operating hours; this version does not invent a policy linking slot windows to those hours.

Nearby requires latitude and longitude; radiusKm defaults to 25 and accepts 0.1..500, finite. Response shape: `[{ "station": { "...station fields..." }, "distanceKm": 1.25 }]`, nearest first then station ID. Radius is inclusive. Distance is a great-circle estimate from the supplied coordinates, not road distance, travel time or Google's place search. Lists return actual persisted records; no demo records or totals are synthesized. Search is literal case-insensitive name/address text, max 120 characters.

Validation: 400. Missing/hidden record: 404. Inactive parent, overlapping windows, capacity conflict, stale write or protected reservation reference: 409. Anonymous/expired/inactive-account session: 401. Wrong role: 403. Errors retain application/problem+json and detail/field errors. Service operations are asynchronous and propagate cancellation.

Station deactivation returns 409 when a referencing reservation is **Pending or Approved**. The same exact status filter protects slot edits, availability changes and deactivation. Rejected, Cancelled and Completed references do not trigger this guard; other validation and expectedUpdatedAtUtc checks still apply. The existing error status/body shape, routes, DTOs and role rules are unchanged. There are no station/slot reactivation, deletion, reservation, QR or completion endpoints in this change.
