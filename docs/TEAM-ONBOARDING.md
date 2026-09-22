# Team onboarding (Windows)

This guide starts from a fresh teammate checkout. If you already have the project, keep your existing checkout, local database and working User Secrets. The team leader will publish the repository manually; the commands below do not imply it has already been published.

## 1. Install prerequisites

| Tool | Required setup |
| --- | --- |
| Git | Git for Windows |
| .NET | .NET 8 SDK; `global.json` selects the installed 8.0 feature band |
| Node/npm | Node.js 22.12+; CI uses Node 22 and npm with the lockfile |
| Docker Desktop | Running Linux-container engine; port 27017 available |
| Android Studio | Open the existing project; install SDK Platform 35 / Build-Tools 35.0.0 / Platform-Tools |
| Java | JDK 17; select it under Android Studio Settings > Build Tools > Gradle > Gradle JDK |
| Emulator | API 26+; API 35 recommended for checking the target SDK behavior |

Use a checkout path without non-ASCII characters; a short path such as `C:\dev\SmartSolar` avoids Android path issues. Do not copy another developer's `local.properties`.

## 2. Clone after the team leader publishes

In PowerShell, from a parent folder for your projects:

```powershell
git clone https://github.com/SupunPrabodha/SmartSolar.git
Set-Location SmartSolar
```

All following root commands run here, beside `SmartSolarMicrogrid.sln`. Once the leader creates `develop`, use that integration branch and follow [CONTRIBUTING](../CONTRIBUTING.md). Do not initialize a second repository inside web or mobile.

## 3. Check tools and start local MongoDB

```powershell
.\scripts\check-environment.cmd
docker compose config
docker compose up -d --wait
docker compose ps
.\scripts\bootstrap-solution.cmd
```

The bootstrap helper preserves an existing solution, verifies project membership, restores, builds and runs tests. It prints an explicit skip for the Mongo integration test unless `SMARTSOLAR_TEST_MONGO` is set. To include it:

```powershell
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
try {
    .\scripts\bootstrap-solution.cmd -Configuration Release
    dotnet restore SmartSolarMicrogrid.sln
    dotnet build SmartSolarMicrogrid.sln --configuration Release
    dotnet test SmartSolarMicrogrid.sln --configuration Release
} finally {
    Remove-Item Env:SMARTSOLAR_TEST_MONGO -ErrorAction SilentlyContinue
}
```

Check each command's exit status; stop and fix any failure before continuing. Tests create a unique `SmartSolarTests_<guid>` database and remove that database afterward. They do not erase development accounts.

MongoDB 7 is bound only to `127.0.0.1:27017`. Compose keeps data in its named `smartsolar_mongo_data` volume. Normal shutdown is `docker compose stop`; do not delete the volume to troubleshoot routine startup problems.

## 4. Configure your own development secrets

```powershell
.\scripts\set-dev-secrets.cmd
dotnet dev-certs https --trust
```

The helper asks for `Jwt:Key` and seed Backoffice identity/profile/password. Use a securely generated signing key of at least 32 characters and your own development account. Secret prompts are masked and values are passed to .NET via stdin, outside source control. Do not put them in command history, Android resources or `.env.local`.

User Secrets are local development storage, not an encrypted production vault. Do not print or share `dotnet user-secrets list` output. The seed account is created in Development if absent; changing setup values is not a password-reset workflow for an existing database record. Working local secrets need not be reset.

## 5. Run the API (terminal 1, repository root)

```powershell
dotnet run --project .\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https
```

Keep it running. The profile serves both HTTPS 7001 and HTTP 5000:

- Swagger: `https://localhost:7001/swagger`.
- Web API: `https://localhost:7001/api/v1`.
- Host health: `http://localhost:5000/health`.
- Emulator API: `http://10.0.2.2:5000/api/v1/`.
- Emulator health: `http://10.0.2.2:5000/health`.

For Android-only work use `--launch-profile http` instead. It serves port 5000 only. ASP.NET Development does not redirect HTTP; other environments retain HTTPS redirection.

The clients call the API; only the API accesses MongoDB. The API must be running and MongoDB healthy before login can succeed.

## 6. Run the web app (terminal 2, repository root)

```powershell
Set-Location .\web\smart-solar-web
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
npm.cmd ci
npm.cmd run dev
```

The public local setting is `VITE_API_BASE_URL=https://localhost:7001/api/v1`. Vite variables are bundled into client code, so they must never contain secrets. Open the local address printed by Vite (normally `http://localhost:5173`). Trust the API development certificate first; do not bypass TLS validation in code.

Sign in with an active Backoffice or GridOperator account. The shell shows full name, role, account state and the last successful session/profile verification. It has a refresh action, sign out and clearly disabled module cards. Prosumers use Android and are denied the web workspace.

Run `npm.cmd test` for four focused session HTTP regressions, then `npm.cmd run build` for the production-build check. This compiles the client but does not validate live login or deploy anything.

## 7. Open and build Android

Open **the existing folder** `mobile/SmartSolarMobile` in Android Studio. Do not create a generated replacement project. Select JDK 17, let SDK Manager install the required packages, accept SDK licenses and sync.

Terminal 3, starting at repository root:

```powershell
Set-Location .\mobile\SmartSolarMobile
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Start an emulator, select `app` / `debug`, and click Run. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

DEBUG uses `http://10.0.2.2:5000/api/v1/`; `10.0.2.2` is the host alias inside Android Emulator. Emulator `localhost` is the emulator itself. The debug network config permits HTTP only to that host. Release requires a real HTTPS API endpoint and has no debug cleartext exception.

Active Prosumer/GridOperator accounts open the native home screen. Backoffice users are told to use web and their mobile session is cleared. Login/restoration/refresh use the API; SQLite contains only the cached current profile, never passwords. Follow [Android manual checks](../mobile/SmartSolarMobile/README.md#manual-emulator-checks), including Database Inspector.

## 8. Verify the account foundation

Using Swagger and your own test identities:

1. Log in as the seeded Backoffice user, use Swagger Authorize with the returned JWT without copying it into documentation/logs.
2. Register a Prosumer with a unique valid NIC/email. Confirm `PendingActivation` and rejected login.
3. As Backoffice, activate the Prosumer and verify login and `GET /api/v1/users/me`.
4. Create an active GridOperator using the common staff endpoint, if needed.
5. Verify role-specific web/mobile shells, restoration, expiry, logout and 401 clearing.
6. Deactivate only your disposable test account, refresh its session, and confirm access ends. Restore the account afterward if required.

Routes and DTOs are in [API-CONTRACT](API-CONTRACT.md). No new feature UI is required for these checks.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Project file does not exist | Run backend commands from the repository root. From Android use `dotnet run --project ..\..\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https`. |
| Compose cannot connect | Start Docker Desktop and wait for the engine. Check `docker context show` and local access permissions. |
| JWT key configuration error | Run the local secret helper once; use the Development launch profile. |
| Seed login fails after changing secrets | Existing database records keep their password hash. Use the original test credentials/account lifecycle; do not delete the database. |
| Browser cannot call HTTPS | Trust the development certificate, visit Swagger, check the API is on 7001 and restart Vite after env changes. |
| Emulator connection fails | Check emulator health URL, API port 5000, Docker health and Windows firewall. A physical phone needs a separate reviewed network setup. |
| Emulator request gets 307 | Restart the updated API in Development, using its committed launch profile. Do not disable TLS checking. |
| Android SDK/JDK error | Select JDK 17, install SDK 35 / Build-Tools 35.0.0, and let Studio create ignored `local.properties`. |
| Login says inactive | A Backoffice user must activate the account through the API. |
| Module card does nothing | Expected: feature development has not implemented that module. |

## 9. Start feature work

The leader assigns ownership for four developers. Keep shared DTOs, auth/session code and configuration coordinated. Normal flow is current `develop` -> member branch -> focused commits -> push -> PR to `develop` -> CI -> review -> merge. All Git actions are manual.

Before the first commit, review ignored local files and the final report. After the first push, check all three CI jobs; local green builds are not evidence of an executed hosted workflow.
