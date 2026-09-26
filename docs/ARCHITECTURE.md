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
| SmartSolar.Infrastructure | MongoDB mappings/repositories, password hashing and JWT implementation | Application, Domain |
| SmartSolar.Api | Controllers, middleware, JWT validation, DI, Swagger, health and hosting | Application, Infrastructure, Domain |

The API composition root wires the layers together. There are no circular references or microservices. BSON mappings stay in Infrastructure.

## Stable contracts

MongoDB collections are exactly `UsersDetail`, `SolarStationInfo`, `EnergyBookingSlots` and `EnergyReservation`. Prosumer NIC remains the primary business identifier, stored as the user document identifier. Roles are `Backoffice`, `GridOperator` and `Prosumer`; account states are `PendingActivation`, `Active` and `Deactivated`. Serialized enums are strings.

Startup creates the contracted collections and indexes idempotently. `/health` checks MongoDB connectivity. Station and slot workflows are implemented by Member 1, and account/Prosumer management by Member 2. Member 3 implements reservation lifecycle with accepted UTC schedule snapshots; Member 4 implements booking queries, dashboard counts and QR/completion. Cross-member consistency is not yet certified: see the [final audit](FINAL-INTEGRATION-AUDIT.md) before final acceptance.

## Authentication and client boundaries

The API hashes passwords, creates expiring JWTs and reloads the stored user during authenticated requests to reject inactive accounts or outdated roles. Application validation also applies outside MVC. Controllers use `/api/v1`, asynchronous services and cancellation tokens; the common error layer returns ProblemDetails.

Web provides a React/Bootstrap shared shell for Backoffice and GridOperator, including `/stations` for both roles, `/users` for Backoffice only and `/operator/reservations` for GridOperator operations. Android is a real Gradle project in `mobile/SmartSolarMobile`, using Java/XML Views for active Prosumer and GridOperator sessions. Both restore profiles through `/users/me`, handle expiry/401 and allow logout. UI role gates are navigation aids; they never grant API permissions.

The Android profile cache contains profile fields and a cache timestamp, with one current user. JWT/expiry are app-private preferences. Passwords are never persisted; neither SQLite nor client memory is an enterprise source of truth. No offline authorization or synchronization is implemented.

## Development and deployment

Web uses `https://localhost:7001/api/v1`. Android DEBUG uses `http://10.0.2.2:5000/api/v1/`, where `10.0.2.2` reaches the host from the emulator. Development omits HTTPS redirection; non-Development retains it. The current debug network-security resource permits cleartext only for 10.0.2.2, localhost and 127.0.0.1; the latter two support explicit local port forwarding. Release has no cleartext exception.

MongoDB 7 runs locally through Compose with a localhost-only port, health check and named volume. Development secrets live in .NET User Secrets. The supported coursework deployment assumes one ASP.NET Core API instance hosted by IIS. Its singleton `CatalogWriteGate` is acquired before the durable per-Prosumer reservation/recovery lock, coordinating catalog protection, reservation allocation and station energy checks. This is an in-process boundary, not a distributed transaction or multi-instance deployment design. Release Android requires a configured HTTPS API URL; signing and production secret provisioning remain manual future work.
