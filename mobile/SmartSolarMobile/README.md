# Smart Solar Mobile - Phase 0

This is the authoritative native Android project: Java + XML Views in package `com.smartsolar.mobile`. All source lives in `app/src`. The integrated duplicate `template-src` / `template-res` and unused `SmartSolarMobileGenerated` IDE folder have been removed after comparison.

Phase 0 includes login, a common HomeActivity, server profile refresh, JWT expiry/401 handling and sign out. The Prosumer/GridOperator module cards are disabled placeholders. There are no Maps, QR, reservation, station or business-dashboard workflows.

## Build

Open this folder in Android Studio. Use JDK 17, AGP 8.8.2, Gradle wrapper 8.10.2, compile/target SDK 35, minimum API 26, and Build-Tools 35.0.0. Keep generated `local.properties` private to your machine.

From the repository root in PowerShell:

```powershell
Set-Location .\mobile\SmartSolarMobile
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

The command is `.\gradlew.bat`, with no slash between `gradlew` and `.bat`.

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- JVM tests: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint: `app/build/reports/lint-results-debug.html`.

Local tests cover expiry, JWT headers/401 handling and permitted mobile roles/statuses. They run without an emulator and do not certify live login, UI or SQLite behavior.

## Start the backend

Separate terminal, **repository root**:

```powershell
docker compose up -d --wait
# Only for first-time local setup:
.\scripts\set-dev-secrets.cmd
dotnet dev-certs https --trust
dotnet run --project .\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https
```

The `https` profile serves web HTTPS 7001 and emulator HTTP 5000 together. For emulator-only development, use `--launch-profile http`. Existing working secrets need not be reset.

If your terminal is already in this Android directory:

```powershell
dotnet run --project ..\..\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https
```

A relative `src/SmartSolar.Api` path from here does not exist; the IDE's active file does not determine the shell directory.

Verify `http://localhost:5000/health` on the host and `http://10.0.2.2:5000/health` in emulator Chrome. Keep the API and MongoDB running. Development omits HTTPS redirection; non-Development retains it. Restart the API after backend changes.

## API URL and transport

`app/build.gradle.kts` supplies `BuildConfig.API_BASE_URL`:

| Variant/client | URL |
| --- | --- |
| Android DEBUG | `http://10.0.2.2:5000/api/v1/` |
| Local web | `https://localhost:7001/api/v1` |
| Android release | HTTPS endpoint supplied by `smartSolarApiBaseUrl`; empty by default |

The trailing slash is required by Retrofit. `10.0.2.2` reaches the Windows host from Android Emulator; emulator `localhost` reaches itself. This default is not a physical-phone network setup.

The main manifest requests INTERNET and disables cleartext. LoginActivity is the exported launcher; HomeActivity is internal. Only `app/src/debug/AndroidManifest.xml` adds the debug network-security config allowing HTTP to `10.0.2.2`. Release has no such exception. There are no dangerous permissions, trust-all certificates or hostname-validation bypasses.

For future release configuration, replace the placeholder with the actual public HTTPS host:

```powershell
.\gradlew.bat :app:assembleRelease "-PsmartSolarApiBaseUrl=https://YOUR-IIS-HOST/api/v1/"
```

The property must use HTTPS, end in `/api/v1/`, and contain no credentials, query or fragment. An unconfigured release shows a configuration message and makes no request. Production signing/deployment are deferred; do not add signing secrets.

## Run the app and verify login

1. Start an API 26+ emulator in Android Studio Device Manager.
2. Select `app`, the `debug` variant and that emulator, then Run. Alternatively, run `.\gradlew.bat :app:installDebug` and launch Smart Solar Microgrid.
3. Use an **Active Prosumer or GridOperator** NIC/password. A PendingActivation Prosumer must first be activated by Backoffice through Swagger/API.
4. Confirm Home shows your real full name, role, account state, profile verification time and session expiry.
5. Tap Refresh profile: it calls `GET /api/v1/users/me`. Returning to the app also revalidates the profile. A SQLite row alone never grants access.
6. Sign out: the token/expiry and local profile are cleared, and Back cannot reopen the signed-in screen.

Backoffice users must use web; Android rejects their mobile session with a clear message. API authorization remains authoritative for every role.

Prosumer placeholders: Find Stations, My Reservations, Booking History. GridOperator placeholders: Operations, Scan Transaction, Transaction History. Each says it is coming during feature development and has no action.

## Manual emulator checks

These checks require your running backend and test accounts. Host build/unit-test results are not runtime evidence.

1. **Prosumer login:** use an Active Prosumer; verify actual name, role, Active status, expiry and the three Prosumer placeholders. Refresh successfully.
2. **GridOperator login:** sign out and use an Active GridOperator; verify Operations, Scan Transaction and Transaction History remain disabled.
3. **Backoffice rejection:** sign out and enter valid Backoffice credentials; expect a web-workspace message, no Home access and no cached profile.
4. **Invalid/inactive accounts:** test wrong password, PendingActivation and Deactivated accounts; expect an error and no session/cache.
5. **Restoration:** close/reopen, background/foreground and rotate the app. Expect `/users/me` verification with a valid token. Check portrait, landscape, light/dark mode, large fonts, keyboard and system-bar insets.
6. **Expiry:** in the API terminal set `$env:Jwt__ExpiryMinutes = '1'`, restart the API, then log in to obtain a new token. Wait over one minute, first in the foreground and then in a separate run in the background. Expect login and an empty cache. Remove the temporary variable with `Remove-Item Env:Jwt__ExpiryMinutes` and restart the API.
7. **401:** deactivate a disposable logged-in Prosumer using another Backoffice session in Swagger. Tap Refresh in Android. Expect session/cache clearing and login. Reactivate the test account afterward if needed.
8. **Network failure:** stop only your test API, refresh, and expect a connection message with profile content hidden. Restart the API and retry. An unexpired token can be revalidated; no offline authorization is granted.
9. **Logout:** inspect the populated SQLite row, sign out, verify zero rows, then press Back/reopen. Login should remain required.
10. **Transport:** confirm emulator health and API login work without a 307 redirect; inspect release configuration to ensure it has no debug HTTP exception.

Never copy tokens/passwords into screenshots, reports or terminal logs.

## Verify SQLite

With the debug app running, open Android Studio **View > Tool Windows > App Inspection > Database Inspector**. Select `com.smartsolar.mobile`, enable **Keep database connections open**, and tap Refresh profile if the helper has closed its connection. Use an API 26+ emulator supported by Database Inspector.

Database: `smart_solar_local.db`, schema version 1, table `local_user`.

```sql
PRAGMA table_info(local_user);
SELECT nic, full_name, email, phone_number, role, status, updated_at_utc
FROM local_user;
SELECT COUNT(*) FROM local_user;
```

Expect one profile after login/refresh and zero after logout/expiry/401 or mobile role rejection. There are no password, password-hash or JWT columns. If Inspector cannot attach, use Device Explorer to inspect the debug app-private `data/data/com.smartsolar.mobile/databases` directory. Exported data is private; never commit it.

`SessionManager` holds JWT and expiry in app-private `smart_solar_session` SharedPreferences. Backup/device-transfer rules exclude preferences/databases and app backup is disabled. SQLite work runs on the repository worker and helpers close with try-with-resources. Schema changes require an explicit migration/version increment.

## Package organization

```text
com.smartsolar.mobile
  data/local           SQLite profile cache
  data/remote          Retrofit client
    api                login and /users/me
    dto                API request/response objects
    interceptor        Authorization and invalid-session clearing
  data/repository      Background API/session/cache coordination
  ui/auth              LoginActivity
  ui/home              HomeActivity and disabled module placeholders
  util                 Session storage/expiry and mobile navigation policy
```

Future domain/model and feature UI packages are created by their owners when needed. No fake feature classes are included.

All network traffic goes to the REST API. MongoDB remains server-side enterprise persistence; SQLite is local cache only. See [dependencies](DEPENDENCIES.md), [team onboarding](../../docs/TEAM-ONBOARDING.md) and [final report](../../docs/PHASE-0-FINAL-REPORT.md).
