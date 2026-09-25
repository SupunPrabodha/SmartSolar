# API Contract — v1

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

Errors use `application/problem+json` and suitable HTTP status codes.

## Shared wire format

JSON property names are camelCase. `role` is one of `Backoffice`, `GridOperator`, `Prosumer`; `status` is one of `PendingActivation`, `Active`, `Deactivated`. Enums are strings, not numbers; staff creation requires an explicit `role`. NIC is the immutable business identifier. Password hashes are never returned.

Login accepts `{ "nic": "<your NIC>", "password": "<your password>" }` and returns `accessToken`, `expiresAtUtc` (UTC timestamp) and `user`. Clients send `Authorization: Bearer <accessToken>`. Registration returns 201 and a PendingActivation Prosumer; it does not issue a token. Login returns 401 for invalid credentials and 403 for pending/deactivated accounts. Authentication rejects tokens whose stored account is missing, inactive or has a changed role.

Profile and staff DTOs require name (2-120 characters), email, phone (7-20 characters), and, for creation, password (8-100 characters) and NIC (12 digits or 9 digits plus V/X). Email/NIC are normalized server-side. Duplicate identities/emails return 409, including database uniqueness races. Invalid inputs return 400; missing users return 404; successful lifecycle changes return 204. Error bodies include `status`, `title`, optional `detail`/validation `errors`, and a trace identifier. Unexpected 500 errors do not expose exception details.

`GET /health` is outside `/api/v1`: 200/Healthy when MongoDB responds, 503 when unavailable. Swagger UI `/swagger` and OpenAPI `/swagger/v1/swagger.json` are available only in Development. CORS origins are configured in `Cors:AllowedOrigins`; these are browser access settings, not authorization.

No station, booking, reservation, Maps or QR feature endpoints are implemented in Phase 0.

## Member 3 Checkpoint 1: planned reservation contract

**Historical checkpoint note:** the following section records the earlier design.
The current repository now implements these Member 3 routes and accepted schedule
snapshots. See the Member 4 section below and
[the inspected contract](MEMBER-4-RESERVATION-CONTRACT.md) for current behavior.

Checkpoint 1 adds request/response types and isolated application policy tests only.
**The following reservation routes are planned, not implemented or available.**

| Method | Planned route under /api/v1 | Planned access | Purpose |
| --- | --- | --- | --- |
| POST | /reservations | Active Prosumer | Create own Pending reservation |
| GET | /reservations/{reservationId} | Owning Prosumer or GridOperator | Inspect summary |
| PUT | /reservations/{reservationId} | Owning Prosumer or GridOperator | Modify, returning to Pending |
| PATCH | /reservations/{reservationId}/cancel | Owning Prosumer or GridOperator | Cancel with the same cutoff |
| POST | /reservations/prosumers/{prosumerNic} | GridOperator | Assisted creation for an active Prosumer |
| GET | /reservations | GridOperator | Limited operational management list |

Backoffice access is not extended by this proposal. A future Prosumer list/entry-point
contract needs to be finalized with the Android flow; no Member 4 history/search
endpoint is introduced.

CreateReservationRequest and UpdateReservationRequest both accept only:
```json
{
  "slotId": "11111111111111111111111111111111",
  "energyAmountKwh": 1.5
}
```

SlotId must parse as a nonempty GUID; both existing N and D string formats work.
EnergyAmountKwh is a decimal strictly greater than zero. No minimum trade size or
maximum energy limit is invented; station/slot energy validation remains a service concern.
The future service must invoke the existing application RequestValidation mechanism,
then verify referenced records and apply authoritative policy.

ReservationResponse contains reservationId, prosumerNic, stationId, slotId,
energyAmountKwh, scheduledStartAtUtc, scheduledEndAtUtc, status, createdAtUtc and
updatedAtUtc. Dates are UTC; status uses the existing string enum. No QR credential
is returned. Scheduled response fields do not add fields to MongoDB entities.

Identity, station, schedule, status and timestamps must be resolved server-side.
The DTOs cannot bind client-supplied prosumerNic, stationId, status, qrToken or
schedule fields. Existing JSON behavior ignores extra properties; this is not
evidence of endpoint authorization, which is deferred.

Planned successful responses: 201 for creation, 200 with the summary for retrieval,
update and cancellation. Existing ProblemDetails conventions remain: 400 for input,
schedule or horizon errors; 401 for missing/invalid authentication; 403 for access
restrictions; 404 for missing resources; 409 for cutoff, state, overlap or capacity
conflicts. No new global JSON/error behavior is introduced.

See BUSINESS-RULES.md for exact policy and DATABASE.md for the unresolved accepted
schedule persistence decision. There are no DTO-to-entity mappings or reservation
controllers in Checkpoint 1.

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


