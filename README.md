# Smart Solar Microgrid Trading System

SE4040 Enterprise Application Development assignment: a React Web and native Android system for managing community solar stations, bookings and verified energy transfers.

The system includes station/slot management and Maps, account and email verification, reservation lifecycle, booking views, dashboard counts, QR verification and completion. Pending/Approved reservations protect catalog changes. The supported deployment uses one API process; see [architecture](docs/ARCHITECTURE.md) and the [manual acceptance checklist](docs/FINAL-MANUAL-ACCEPTANCE-CHECKLIST.md).

## Architecture

```text
React Web --------\
                   -> ASP.NET Core REST API -> MongoDB
Native Android ---/
       +-- SQLite (local profile cache)
```

The REST API is the **FAT SERVICE** and authoritative business-logic layer. Clients never access MongoDB directly.

| Directory | Purpose |
| --- | --- |
| `src/SmartSolar.Domain` | Entities and enums; no persistence dependency |
| `src/SmartSolar.Application` | DTOs, interfaces and application services |
| `src/SmartSolar.Infrastructure` | MongoDB persistence and security implementations |
| `src/SmartSolar.Api` | REST endpoints, middleware, DI, Swagger and health |
| `tests/` | Unit and API/Mongo integration tests |
| `web/smart-solar-web/` | React, Bootstrap and Vite |
| `mobile/SmartSolarMobile/` | Real native Android Gradle project, Java/XML Views |
| `scripts/` | Windows environment, bootstrap and secret setup helpers |
| `docs/` | Current contracts, onboarding and acceptance checklists |

## Prerequisites

Git, .NET 8 SDK, Node.js 22.12+ (Node 22 is used in CI), npm, Docker Desktop with Linux containers, Android Studio, JDK 17, Android SDK Platform 35 and Build-Tools 35.0.0. Use the committed Gradle wrapper (8.10.2 / AGP 8.8.2).

## Quick start on Windows

Run these commands in PowerShell **from the repository root**, containing `SmartSolarMicrogrid.sln`:

```powershell
.\scripts\check-environment.cmd
docker compose up -d --wait
.\scripts\bootstrap-solution.cmd
.\scripts\set-dev-secrets.cmd
dotnet dev-certs https --trust
dotnet run --project .\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https
```

The secret setup helper prompts privately for the signing key and seed password and stores development values outside the repository. It is a one-time local setup; existing users do not need to reset working secrets.

Open a second terminal at the repository root:

```powershell
Set-Location .\web\smart-solar-web
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm.cmd ci
npm.cmd run dev
```

For Android, open `mobile/SmartSolarMobile` in Android Studio and select JDK 17 as the Gradle JDK. Sync the existing project, select a supported emulator (API 26+) and run `app`. From a root terminal:

```powershell
Set-Location .\mobile\SmartSolarMobile
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Run `scripts\setup-adb-reverse.cmd` from the root with the emulator or USB device connected and authorized. Android DEBUG localhost reaches the host only while ADB reverse is active.

If your terminal is already in `mobile/SmartSolarMobile`, the backend path is `..\..\src\SmartSolar.Api\SmartSolar.Api.csproj`. Running `--project src/SmartSolar.Api` from that directory fails because project paths are relative to the terminal, not the open IDE file.

## Development URLs

| Component | URL |
| --- | --- |
| Web | `http://localhost:5173` |
| Web API configuration | `https://localhost:7001/api/v1` |
| Swagger | `https://localhost:7001/swagger` |
| Host HTTP health | `http://localhost:5000/health` |
| Android DEBUG API (ADB reverse) | `http://localhost:5000/api/v1/` |
| Android device health (ADB reverse) | `http://localhost:5000/health` |
| Local MongoDB | `mongodb://127.0.0.1:27017` |

The `https` launch profile serves both development ports. The `http` profile serves only port 5000. Development HTTP deliberately avoids redirecting the emulator to a host-only HTTPS certificate. Release/production retain HTTPS; there is no trust-all certificate implementation. API timestamps remain UTC; Web renders browser-local time and Android renders device-local time before sending local datetime inputs back as UTC.

## Accounts and persistence

Prosumer registration starts in `PendingActivation`; Backoffice approval sends a verification email. The Prosumer must verify that email before first activation/sign-in. Active users may log in; inactive users are rejected. Roles remain `Backoffice`, `GridOperator`, `Prosumer`, and states remain `PendingActivation`, `Active`, `Deactivated`. NIC is the Prosumer business identifier.

Web supports Backoffice/GridOperator: `/stations` serves both roles and `/users` is Backoffice-only. GridOperator reservation operations are under `/operator/reservations`, with dashboard/current/pending/history/search and create/detail/edit routes. Android supports anonymous Prosumer registration, both mobile roles for local-time station and booking views, Prosumer account/reservation management and QR display, and GridOperator pending/current/history/search, scanning and completion; Backoffice uses web. JWT expiry, `/users/me`, invalid-session clearing and logout are common foundation behavior.

MongoDB collections are `UsersDetail`, `SolarStationInfo`, `EnergyBookingSlots`, `EnergyReservation`. Compose uses MongoDB 7, a health check, named persistent volume and localhost-only binding. Android SQLite stores only a local profile cache; passwords are never stored there. The supported coursework deployment assumes one ASP.NET Core API process hosted by IIS so the singleton `CatalogWriteGate` coordinates catalog and reservation allocation writes.

## Validation and team workflow

CI validates backend/Mongo integration, Web tests/build and Android build/tests. Confirm the hosted run for the revision selected for submission; local success is not hosted evidence.

Use [CONTRIBUTING](CONTRIBUTING.md) for shared-contract review and validation expectations.

Never commit passwords, JWT signing keys, tokens, User Secrets, `.env.local`, `local.properties`, private signing files or database exports. Public templates such as `.env.example` are safe to commit.

- [Team onboarding](docs/TEAM-ONBOARDING.md): complete Windows setup and troubleshooting.
- [Manual acceptance](docs/FINAL-MANUAL-ACCEPTANCE-CHECKLIST.md) and [screenshot checklist](docs/FINAL-UI-SCREENSHOT-CHECKLIST.md).
- [Architecture](docs/ARCHITECTURE.md) and [API contract](docs/API-CONTRACT.md).
- [Android guide](mobile/SmartSolarMobile/README.md) and [dependencies](mobile/SmartSolarMobile/DEPENDENCIES.md).
- [Contribution guide](CONTRIBUTING.md): workflow and shared ownership rules.

## Submission and individual contributions

Repository: [SupunPrabodha/SmartSolar](https://github.com/SupunPrabodha/SmartSolar) (verified against the local origin URL; assessor access must be checked by the team).

Verified contribution allocation supplied by the team:

| Member | Name | Student ID | Contribution | Share |
| --- | --- | --- | --- | --- |
| 1 | Liyanage S. P. | IT23187450 | Stations, slots, Maps, team leadership and integration | 25% |
| 2 | Wickramathilaka N. M. | IT23165434 | Users, Prosumer lifecycle, activation/email verification and account management | 25% |
| 3 | RAMANAYAKE R. H. B. D. G. | IT23164130 | Reservation lifecycle, scheduling, availability and 7-day/12-hour rules | 25% |
| 4 | ALAHAKOON A. W. A. C. N. | IT23163522 | Booking queries, dashboard counts, QR verification and completion | 25% |

File headers follow substantive Git history and confirmed ownership. Shared foundation files use Smart Solar Development Team; leadership or merging alone does not establish authorship.

**Demo video (maximum 5 minutes): MANUAL SUBMISSION ACTION REQUIRED — real link not supplied.**

- [Rubric traceability and missing submission evidence](docs/requirements/RUBRIC-TRACEABILITY.md).
- [Reproducible IIS deployment checklist](deployment/iis/README.md).

## Validation

From the root, start local MongoDB and run:

```powershell
docker compose up -d --wait
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
try {
    dotnet build SmartSolarMicrogrid.sln --configuration Release
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
    dotnet test SmartSolarMicrogrid.sln --configuration Release
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }
} finally { Remove-Item Env:SMARTSOLAR_TEST_MONGO -ErrorAction SilentlyContinue }
```

In `web/smart-solar-web`, run `npm.cmd test` and `npm.cmd run build`. In `mobile/SmartSolarMobile`, run `.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest`. Check each exit code. Builds and host tests do not replace the manual acceptance matrix.

## Configuration and submission security

Configure SMTP privately using [onboarding](docs/TEAM-ONBOARDING.md#email-verification-and-password-recovery); approval requires a working verification sender and page URL. Production configuration and single-worker requirements are in the [IIS guide](deployment/iis/README.md).

A Maps key was historically committed. Its owner has not confirmed revocation/rotation. Removing it from current source does not revoke it: confirm revocation in Google Cloud, restrict the replacement to the Android package/signing certificate and Maps SDK, and retain it only in ignored local configuration. Review all staged files before packaging.
