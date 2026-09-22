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
