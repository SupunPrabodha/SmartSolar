> Historical snapshot of initial setup. Current status, builds, Android cleanup and handoff decisions are in [PHASE-0-FINAL-REPORT.md](PHASE-0-FINAL-REPORT.md). The template/manual-project statements below no longer describe the current project.

# Phase 0 initialization report

Date: 2026-09-21. Final classification: **READY AFTER MANUAL ACTIONS**.

Repository-side common foundation work is complete. Live API smoke verification awaits real development secrets; native Android project creation/build awaits Android Studio. No later station/reservation/Maps/QR/dashboard package was implemented.

## 1. What was inspected

- Entire source/template/documentation inventory, existing six-project solution, every csproj and its references/packages, Directory.Build.props, .editorconfig and .gitignore.
- Scripts, Compose, GitHub CI, backend services/controllers/entities/security/configuration, React shell, Java/XML Android templates, empty test projects, database notes and IIS notes.
- SDK/tool versions, Docker CLI versus actual server, Git repository status, secret-like values and assignment headers.
- Existing solution entries were valid. The starter could not compile because Domain used BSON attributes without the MongoDB assembly, contradicting the intended dependency boundary.

## 2. What changed

- Bootstrap now preserves/validates the solution, ensures all six projects are included without duplication, restores/builds/tests, and propagates failures. Scripts resolve paths relative to their own location. Added .cmd wrappers with process-only execution-policy bypass and a PASS/WARNING/FAIL environment checker.
- Pinned SDK selection to .NET 8. Moved BSON mapping from Domain into Infrastructure, preserving IDs, string enums and collection names.
- Added string-enum JSON, active-account/role checks during JWT validation, bounded MongoDB readiness check, JWT configuration checks, shared application input validation, required explicit staff role, exact ProblemDetails content type, request cancellation handling, duplicate-key conflict mapping, and concurrent collection-creation handling.
- Seed configuration now rejects incomplete/invalid values. Secret prompts mask key/password and send JSON through stdin; values are not stored in the repository. Seed remains Development-only and idempotent.
- Updated vulnerable MongoDB/authentication dependencies to MongoDB.Driver 3.12.0 and Microsoft .NET 8 packages 8.0.29. Framework remains net8.0; dependency direction is preserved.
- Compose now binds MongoDB 7 to localhost, retains a named volume and checks health.
- React now parses ProblemDetails JSON, restores sessions through `/users/me`, handles loading/expiry/401, and uses a configured React Vite plugin. Updated React Router to 7.18.4 and Vite/plugin to 7.3.6/5.2.0, retained React 18 and Bootstrap, generated a lockfile and declared Node >=22.12.
- Native template now caches an API profile in SQLite, tracks token expiry, clears sessions on 401, exposes current-user Retrofit call, and cancels login calls on Activity destruction. Added exact Android Studio/manifest/emulator instructions. No Android build was fabricated.
- Added meaningful authentication/password, middleware error-formatting, BSON mapping and live MongoDB tests. CI builds/tests the solution with a MongoDB service and builds the web shell using npm ci. Added setup/troubleshooting and clarified current versus future contracts.

## 3. Files added

- `global.json`
- `scripts/check-environment.ps1`, `scripts/check-environment.cmd`
- `scripts/bootstrap-solution.cmd`, `scripts/set-dev-secrets.cmd`
- `src/SmartSolar.Infrastructure/Persistence/MongoMappings.cs`
- `src/SmartSolar.Application/Services/RequestValidation.cs`
- `src/SmartSolar.Api/Configuration/MongoHealthCheck.cs`
- `src/SmartSolar.Api/Properties/launchSettings.json`
- `tests/SmartSolar.UnitTests/AuthFoundationTests.cs`
- `tests/SmartSolar.IntegrationTests/ApiContractTests.cs`, `tests/SmartSolar.IntegrationTests/MongoFoundationTests.cs`
- `web/smart-solar-web/vite.config.js`, `web/smart-solar-web/package-lock.json`
- `docs/INITIALIZATION-REPORT.md`

## 4. Files modified

- `README.md`, `.gitignore`, `docker-compose.yml`, `.github/workflows/ci.yml`
- `scripts/bootstrap-solution.ps1`, `scripts/set-dev-secrets.ps1`
- `src/SmartSolar.Domain/Entities/User.cs`, `SolarStation.cs`, `EnergyBookingSlot.cs`, `EnergyReservation.cs`
- `src/SmartSolar.Application/Services/AuthService.cs`, `UserService.cs`; `DTOs/Users/CreateStaffRequest.cs`
- `src/SmartSolar.Infrastructure/SmartSolar.Infrastructure.csproj`; `Persistence/MongoDbInitializer.cs`, `Persistence/Repositories/UserRepository.cs`
- `src/SmartSolar.Api/SmartSolar.Api.csproj`, `Program.cs`, `SmartSolar.Api.http`, `Middleware/ExceptionHandlingMiddleware.cs`, `Seed/DevelopmentDataSeeder.cs`
- `tests/SmartSolar.UnitTests/SmartSolar.UnitTests.csproj`
- `web/smart-solar-web/package.json`; `src/api/apiClient.js`, `src/auth/AuthContext.jsx`, `src/routes/ProtectedRoute.jsx`, `src/pages/LoginPage.jsx`, `src/pages/HomePage.jsx`
- `mobile/SmartSolarMobile/README.md`; template Java `data/local/AppDatabaseHelper.java`, `data/remote/api/ApiService.java`, `data/remote/interceptor/AuthInterceptor.java`, `ui/auth/LoginActivity.java`, `util/SessionManager.java`
- `docs/ARCHITECTURE.md`, `docs/API-CONTRACT.md`, `docs/DATABASE.md`, `docs/BUSINESS-RULES.md`

Solution project membership was retained. Generated bin/obj/node_modules/dist files are ignored artifacts, not source additions. No .env.local, real secrets, Android local.properties, Git repository or remote was created.

## 5. Commands executed

Inspection included `rg --files --hidden`, file reads, `dotnet --info`, `dotnet --version`, `node --version`, `npm --version`, `git --version`, `git status`, `docker --version`, `docker compose version`, `docker version`, `java -version`, and `javac -version` (through the checker).

Validation/repair commands included:

```powershell
.\scripts\check-environment.cmd
.\scripts\bootstrap-solution.cmd
.\scripts\bootstrap-solution.cmd -Configuration Release
dotnet restore SmartSolarMicrogrid.sln
dotnet build SmartSolarMicrogrid.sln --no-restore
dotnet test SmartSolarMicrogrid.sln
dotnet list SmartSolarMicrogrid.sln package --vulnerable --include-transitive
docker compose config
docker compose up -d --wait
docker compose ps
dotnet run --project src/SmartSolar.Api --no-build --launch-profile https
```

Mongo test runs set `$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'` in the child terminal. Bootstrap was also run from `scripts` to verify location independence and repeated solution preservation. PowerShell scripts were parsed through the PowerShell parser. Every handwritten C# file was checked for the assignment header.

Web commands included initial `npm install`, `npm audit --json`, package version queries, explicit patched package installs, `npm ci`, `npm run build` with `VITE_API_BASE_URL=https://localhost:7001/api/v1`, final `npm audit`, and offline package-lock metadata synchronization.

Initial sandbox restrictions blocked Docker configuration/socket access and some npm/NuGet requests. Those checks were rerun with approved external access and succeeded; the permission errors were not mistaken for a stopped Docker server. No execution policy or operating-system setting was changed.

## 6. Backend build result

**PASS**: Debug and Release builds, six projects, zero warnings and zero errors. Initial 21 BSON-related compile errors were repaired. Domain has no persistence package dependency. SDK: 8.0.423; runtime: 8.0.29.

## 7. Test result

**PASS: 12 tests, zero failures, zero skips when MongoDB is enabled.**

- 4 unit tests: registration/normalization/pending activation; activation/login/deactivation; duplicate identity/invalid credentials; salted password verification.
- 7 in-process integration/contract cases: middleware status and application/problem+json behavior, hidden internal exception detail, and BSON identifiers.
- 1 live MongoDB integration test: repeatable initialization, exact four collection names, NIC `_id`, string enum storage, unique email conflicts, repository round-trip and status update. Uses and removes only a generated test database.

An initial run without `SMARTSOLAR_TEST_MONGO` explicitly skipped the database test. This is **TEST NOT EXECUTED DUE TO ENVIRONMENT**, not a passing database test. Subsequent live runs passed. These tests do not claim an end-to-end HTTP authentication/Swagger/seeding smoke test.

## 8. React build result

**PASS**: npm ci and production build with Vite 7.3.6; 45 modules transformed. Final npm audit: **0 vulnerabilities**. Node 22.12.0, npm 10.9.0. Initial dependency audit reported five vulnerabilities; package updates resolved them. Interactive browser login was not verified because API secrets are absent.

## 9. Docker/MongoDB and API runtime result

**PASS**: Docker Desktop server available (engine 29.3.1), Compose v5.1.1, valid Compose configuration. MongoDB 7 container is running and healthy on `127.0.0.1:27017`, using the named persistent volume. Live MongoDB repository/initializer tests passed.

**API SMOKE TEST BLOCKED BY CONFIGURATION**: The normal API startup attempt correctly rejected missing `Jwt:Key`. No User Secrets file was present for the project's ID, and no replacement/fake key was generated. Therefore live `/health`, Swagger and development Backoffice seeding were **not executed/verified**. Configure real secrets and run the smoke commands below. The MongoDB tests establish database connectivity but do not substitute for these API checks.

## 10. Android foundation status

**TEMPLATE REVIEWED; BUILD NOT EXECUTED.** JDK/javac 17.0.16 are installed. There is no Gradle Android project/wrapper to build and no Android SDK environment variable was configured. Android Studio project creation, SDK selection and first build are manual actions. Java/XML, Retrofit, app-private expiring sessions and SQLite profile cache are preserved. Feature screens remain deferred.

## 11. Security/secrets check

No real JWT key, database password, admin password, private key or cloud API credential was found in repository source/configuration. The starter HTTP sample contained the literal example `StrongPass123!`; it was replaced by a process-environment placeholder. Unit tests contain clearly named test-only passwords and a non-JWT fake token, never runtime signing credentials.

Final NuGet vulnerability audit reports no vulnerable direct/transitive packages across the solution; final npm audit reports zero. These are advisory snapshots, not a guarantee against future vulnerabilities. Sources used for dependency/action verification: [MongoDB.Driver package](https://www.nuget.org/packages/MongoDB.Driver), [JWT Bearer 8.0.29](https://www.nuget.org/packages/Microsoft.AspNetCore.Authentication.JwtBearer/8.0.29), [Snappier advisory](https://github.com/advisories/GHSA-pggp-6c3x-2xmx), [SharpCompress advisory](https://github.com/advisories/GHSA-6c8g-7p36-r338), [checkout releases](https://github.com/actions/checkout/releases), [setup-dotnet](https://github.com/actions/setup-dotnet), [setup-node releases](https://github.com/actions/setup-node/releases).

Ignore rules cover .NET/Node/Android artifacts, machine state, environment files, local.properties and signing keystores, while allowing .env.example. All 41 handwritten C# files retain project headers. The secret script was syntax-checked but not run interactively because doing so would require your own credentials.

## 12. Remaining blockers and limits

- Real development secrets and local HTTPS trust are required for API/browser/seeding smoke verification.
- Android Studio must generate the real Java/XML Gradle project and run its first build.
- This extracted workspace has no `.git`; `git status` reports “not a git repository.” No Git initialization, remote, commit, push or account change was made. Coordinate the team's existing repository separately before parallel branch work.
- GitHub-hosted CI was updated but not executed remotely; no GitHub access/push was requested. Equivalent backend/web commands passed locally.
- IIS deployment, production MongoDB credentials and later feature workflows are outside Phase 0 and were not provisioned.

## 13. Exact MANUAL ACTIONS

1. Run `scripts\set-dev-secrets.cmd` from the root and enter your real development JWT key and five Backoffice fields. Keep them out of chat/source control.
2. Run `dotnet dev-certs https --trust` and accept Windows certificate trust if required. Start the API, check health/Swagger, then log in and call `/users/me` to verify seeded Backoffice identity.
3. Follow `mobile/SmartSolarMobile/README.md` in Android Studio to generate/import the in-place Java/XML project, select installed SDK/JDK versions, add manifest/dependencies, sync, build and test login/SQLite.
4. Before team branching, coordinate source control with the team owner using the intended existing repository. No remote URL is available to supply an exact clone command; do not invent one or create a nested repository.

Docker Desktop is already running now. On a later session, start it manually only if the environment checker reports the daemon unavailable.

## 14. Commands to run afterward

From the repository root:

```powershell
.\scripts\check-environment.cmd
docker compose up -d --wait
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
.\scripts\bootstrap-solution.cmd -Configuration Release
Remove-Item Env:SMARTSOLAR_TEST_MONGO
.\scripts\set-dev-secrets.cmd
dotnet dev-certs https --trust
dotnet run --project src/SmartSolar.Api --launch-profile https
```

In a second terminal:

```powershell
Invoke-WebRequest https://localhost:7001/health
Invoke-WebRequest https://localhost:7001/swagger/v1/swagger.json
cd web\smart-solar-web
Copy-Item .env.example .env.local
npm ci
npm run build
npm run dev
```

Open `https://localhost:7001/swagger`, log in with your seed account, authorize with the returned JWT, and call `/api/v1/users/me`. Verify role Backoffice and status Active. After Android Studio generation:

```powershell
cd mobile\SmartSolarMobile
.\gradlew.bat :app:assembleDebug
```

Do not repeat the secret script or overwrite an existing .env.local unless intentionally changing configuration.

## 15. Acceptance classification

**READY AFTER MANUAL ACTIONS**: repository foundation, repeatable bootstrap, backend tests, MongoDB and web build are validated. API runtime/seeding verification and native Android generation/build remain explicitly unverified until the manual steps above are complete.

