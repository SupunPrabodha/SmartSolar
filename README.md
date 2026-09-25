# Smart Solar Microgrid Trading System

SE4040 Enterprise Application Development assignment: a shared foundation for a four-member team building a microgrid energy trading system.

Phase 0 provides account/authentication services, persistence contracts, a responsive web workspace and a native Android home screen. Member 1 now adds station management, operator slot inventory and Android Maps/nearby discovery. Other members' modules remain deferred. See [Member 1 implementation and manual checks](docs/MEMBER-1-IMPLEMENTATION-REPORT.md) for validation and the unresolved active-reservation policy.

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
| `tests/` | Unit and API/Mongo foundation tests |
| `web/smart-solar-web/` | React, Bootstrap and Vite |
| `mobile/SmartSolarMobile/` | Real native Android Gradle project, Java/XML Views |
| `scripts/` | Windows environment, bootstrap and secret setup helpers |
| `docs/` | Contracts, onboarding, acceptance and final validation report |

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

If your terminal is already in `mobile/SmartSolarMobile`, the backend path is `..\..\src\SmartSolar.Api\SmartSolar.Api.csproj`. Running `--project src/SmartSolar.Api` from that directory fails because project paths are relative to the terminal, not the open IDE file.

## Development URLs

| Component | URL |
| --- | --- |
| Web | `http://localhost:5173` |
| Web API configuration | `https://localhost:7001/api/v1` |
| Swagger | `https://localhost:7001/swagger` |
| Host HTTP health | `http://localhost:5000/health` |
| Android emulator DEBUG API | `http://10.0.2.2:5000/api/v1/` |
| Android emulator health | `http://10.0.2.2:5000/health` |
| Local MongoDB | `mongodb://127.0.0.1:27017` |

The `https` launch profile serves both development ports. The `http` profile serves only port 5000. Development HTTP deliberately avoids redirecting the emulator to a host-only HTTPS certificate. Release/production retain HTTPS; there is no trust-all certificate implementation.

## Accounts and persistence

Prosumer registration starts in `PendingActivation`; Backoffice activates accounts. Active users may log in; inactive users are rejected. Roles remain `Backoffice`, `GridOperator`, `Prosumer`, and states remain `PendingActivation`, `Active`, `Deactivated`. NIC is the Prosumer business identifier.

Web supports Backoffice/GridOperator; Android supports Prosumer/GridOperator and directs Backoffice users to web. JWT expiry, `/users/me`, invalid-session clearing and logout are common foundation behavior.

MongoDB collections are `UsersDetail`, `SolarStationInfo`, `EnergyBookingSlots`, `EnergyReservation`. Compose uses MongoDB 7, a health check, named persistent volume and localhost-only binding. Android SQLite stores only a local profile cache; passwords are never stored there.

## Validation and team workflow

CI validates .NET 8 with MongoDB, web session tests and the production build and Android debug build/local unit tests on Ubuntu. Hosted CI must be verified after the team leader's first manual push.

Planned branches: `main` for submission/release, `develop` for integration, and `feature/*`, `bugfix/*`, `docs/*` for member work. Submit reviewed PRs to `develop`. No Git initialization, branch creation, commits or push are performed by the handoff pass.

Never commit passwords, JWT signing keys, tokens, User Secrets, `.env.local`, `local.properties`, private signing files or database exports. Public templates such as `.env.example` are safe to commit.

- [Team onboarding](docs/TEAM-ONBOARDING.md): complete Windows setup and troubleshooting.
- [Phase-0 acceptance](docs/PHASE-0-ACCEPTANCE.md): evidence, manual checks and deferred scope.
- [Final report](docs/PHASE-0-FINAL-REPORT.md): actual local command results and handoff status.
- [Architecture](docs/ARCHITECTURE.md) and [API contract](docs/API-CONTRACT.md).
- [Android guide](mobile/SmartSolarMobile/README.md) and [dependencies](mobile/SmartSolarMobile/DEPENDENCIES.md).
- [Contribution guide](CONTRIBUTING.md): workflow and shared ownership rules.
