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
