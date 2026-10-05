# Architecture

React Web and native Android communicate only with the ASP.NET Core REST API. The API is the **FAT SERVICE**: it owns enterprise validation, account authorization and business orchestration. Only Infrastructure talks to MongoDB. Android SQLite is an app-private profile cache.

```text
React Web --------\
                   -> ASP.NET Core REST API -> MongoDB
Native Android ---/
       |
       +-- SQLite (local profile cache)
```

## Dependency direction

| Project | Responsibilities | Project references |
| --- | --- | --- |
| SmartSolar.Domain | Enterprise entities and enums | None; no MongoDB package |
| SmartSolar.Application | DTOs, interfaces, use cases, validation and orchestration | Domain |
| SmartSolar.Infrastructure | MongoDB mappings/repositories, password hashing and QR security | Application, Domain |
| SmartSolar.Api | Controllers, middleware, JWT issuance/validation, DI, Swagger, health and hosting | Application, Infrastructure, Domain |

The API composition root wires the layers together. There are no circular references or microservices. BSON mappings stay in Infrastructure.

## Stable contracts

MongoDB collections are exactly `UsersDetail`, `SolarStationInfo`, `EnergyBookingSlots` and `EnergyReservation`. Prosumer NIC remains the primary business identifier, stored as the user document identifier. Roles are `Backoffice`, `GridOperator` and `Prosumer`; account states are `PendingActivation`, `Active` and `Deactivated`. Serialized enums are strings.

Startup creates the contracted collections and indexes idempotently. `/health` checks MongoDB connectivity. Services implement catalog, account, reservation, query and QR/completion workflows; [business rules](BUSINESS-RULES.md) and the [API contract](API-CONTRACT.md) define their boundaries.

## Authentication and client boundaries

The API hashes passwords, creates expiring JWTs and reloads the stored user during authenticated requests to reject inactive accounts or outdated roles. Application validation also applies outside MVC. Controllers use `/api/v1`, asynchronous services and cancellation tokens; the common error layer returns ProblemDetails.

Web provides a React/Bootstrap shared shell for Backoffice and GridOperator, including `/stations` for both roles, `/users` for Backoffice only and `/operator/reservations` for GridOperator operations. Web datetime-local controls convert browser-local values to UTC before API calls; UTC responses are rendered in browser-local time. Android is a real Gradle project in `mobile/SmartSolarMobile`, using Java/XML Views for active Prosumer and GridOperator sessions, anonymous Prosumer registration, pending/current/history/search booking views and QR flows. Android converts API UTC timestamps to `ZoneId.systemDefault()`. Both restore profiles through `/users/me`, handle expiry/401 and allow logout. UI role gates are navigation aids; they never grant API permissions.

The Android profile cache contains profile fields and a cache timestamp, with one current user. JWT/expiry are app-private preferences. Passwords are never persisted; neither SQLite nor client memory is an enterprise source of truth. No offline authorization or synchronization is implemented.

## Development and deployment

Web uses `https://localhost:7001/api/v1`. Android DEBUG uses `http://localhost:5000/api/v1/` with `scripts/setup-adb-reverse.cmd` forwarding host ports to the emulator or USB device. Without forwarding, localhost is the device. Development omits HTTPS redirection; non-Development retains it. The current debug network-security resource permits cleartext only for 10.0.2.2, localhost and 127.0.0.1; the latter two support explicit local port forwarding. Release has no cleartext exception.

MongoDB 7 runs locally through Compose with a localhost-only port, health check and named volume. Development secrets live in .NET User Secrets. The supported coursework deployment assumes one ASP.NET Core API instance hosted by IIS. Its singleton `CatalogWriteGate` is acquired before the durable per-Prosumer reservation/recovery lock, coordinating catalog protection, reservation allocation and station energy checks. This is an in-process boundary, not a distributed transaction or multi-instance deployment design. Release Android requires a configured HTTPS API URL; signing and production secret provisioning remain manual future work.

## Native workspace and presentation

Android uses one WorkspaceActivity with retained role-specific fragments and five destinations. Deep flows use focused activities; see [workspace architecture](ANDROID-WORKSPACE-ARCHITECTURE.md). UI role visibility never replaces API authorization.

Web uses React/Bootstrap; its station editor lazily loads Leaflet for click/drag selection with OpenStreetMap attribution. Manual latitude/longitude remains available. Valid coordinate edits and locally parsed full Google Maps coordinate links update the selected pin; invalid input preserves the last valid location. Shortened URLs are not fetched or resolved. Android uses Google Maps markers from server-stored coordinates, with coarse-location permission requested on demand and a usable station-list fallback.

Both clients share an original connected-sun vector mark and a forest/emerald/solar palette. The login photograph is a bundled decorative solar image, not live station data. Android supports light/dark themes; Web uses a light theme. Loading surfaces, semantic status text, local action progress and reduced-motion handling do not invent operational data.

## Display references

REF-, STN- and SLOT- references are presentation aids, not new identifiers. Both clients normalize valid nonzero GUIDs by trimming, lowercasing and removing hyphens, then hash the 32 ASCII characters using unsigned 64-bit FNV-1a, reduce modulo 36^10 and encode ten uppercase base-36 characters with leading zeroes. The prefix identifies reservation, station or slot. Invalid/empty/all-zero input displays Unavailable.

For GUID `11111111-1111-1111-1111-111111111111`, the suffix is `SB2J5ZFT6T` on both clients. These hashes are not guaranteed unique. Actual IDs remain in API routes, request data, relationships and QR lookup. Android reference resolution searches authorized pages (bounded to 200 pages of 100), rejects ambiguity and then queries the actual ID; it is not an indexed server reference field.

## Account experience and operational limits

Avatars, bounded notifications and audit entries are embedded in the existing Mongo documents; no extra collection or filesystem avatar store is used. Password changes revoke previous sessions through security-version checks. See [database](DATABASE.md), [API](API-CONTRACT.md) and [setup](TEAM-ONBOARDING.md).

Password-recovery delivery uses a bounded in-memory queue; restarts can lose queued work. Notification delivery polls embedded events and is not a durable external outbox. Catalog coordination is single-process; do not deploy a web garden or multiple API instances without a reviewed replacement. These limits are explicit and are not production-scale guarantees.
