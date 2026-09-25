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
