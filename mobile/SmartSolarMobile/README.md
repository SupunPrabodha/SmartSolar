# Smart Solar Mobile - common foundation and Member 1

This is the authoritative native Android project: Java + XML Views in package `com.smartsolar.mobile`. All source lives in `app/src`. The integrated duplicate `template-src` / `template-res` and unused `SmartSolarMobileGenerated` IDE folder have been removed after comparison.

Phase 0 includes login, a common HomeActivity, server profile refresh, JWT expiry/401 handling and sign out. Member 1 adds Find Stations, API nearby search, Google Maps markers and station/slot availability details. Reservation, QR, transaction and dashboard workflows remain deferred.

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

Local tests cover expiry, JWT headers/401 handling, permitted mobile roles/statuses, station/slot DTOs and discovery request paths/parameters. They run without an emulator and do not certify live login, UI or SQLite behavior.

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

The main manifest requests INTERNET and ACCESS_COARSE_LOCATION and disables cleartext. Location is requested only when Find near me is tapped. LoginActivity is the exported launcher; HomeActivity and both station activities are internal. Only `app/src/debug/AndroidManifest.xml` adds the debug network-security config allowing HTTP to `10.0.2.2`. Release has no such exception. Approximate foreground location is the only requested dangerous permission; there is no fine/background location or camera permission. Maps adds normal ACCESS_NETWORK_STATE through manifest merging. There are no trust-all certificates or hostname-validation bypasses.

For future release configuration, replace the placeholder with the actual public HTTPS host:

```powershell
.\gradlew.bat :app:assembleRelease "-PsmartSolarApiBaseUrl=https://YOUR-IIS-HOST/api/v1/"
```

The property must use HTTPS, end in `/api/v1/`, and contain no credentials, query or fragment. An unconfigured release shows a configuration message and makes no request. Production signing/deployment are deferred; do not add signing secrets.

## Run the app and verify login

1. Start an API 26+ emulator with Google APIs/Google Play services in Android Studio Device Manager.
2. Select `app`, the `debug` variant and that emulator, then Run. Alternatively, run `.\gradlew.bat :app:installDebug` and launch Smart Solar Microgrid.
3. Use an **Active Prosumer or GridOperator** NIC/password. A PendingActivation Prosumer must first be activated by Backoffice through Swagger/API.
4. Confirm Home shows your real full name, role, account state, profile verification time and session expiry.
5. Tap Refresh profile: it calls `GET /api/v1/users/me`. Returning to the app also revalidates the profile. A SQLite row alone never grants access.
6. Sign out: the token/expiry and local profile are cleared, and Back cannot reopen the signed-in screen.

Backoffice users must use web; Android rejects their mobile session with a clear message. API authorization remains authoritative for every role.

Find Stations works for both supported mobile roles. My Reservations/Booking History (Prosumer) and Scan Transaction/Transaction History (GridOperator) remain disabled.

## Manual emulator checks

These checks require your running backend and test accounts. Host build/unit-test results are not runtime evidence.

1. **Prosumer login:** use an Active Prosumer; verify actual name, role, Active status, expiry, working Find Stations and the two disabled reservation/history placeholders. Refresh successfully.
2. **GridOperator login:** sign out and use an Active GridOperator; verify Find Stations is available; Scan Transaction and Transaction History remain disabled.
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
    api                auth/profile and station/slot discovery
    dto                API request/response objects
    interceptor        Authorization and invalid-session clearing
  data/repository      Background API/session/cache coordination
  ui/auth              LoginActivity
  ui/home              HomeActivity with Find Stations and remaining placeholders
  ui/stations          API station list/nearby Maps and station/slot details
  util                 Session storage/expiry and mobile navigation policy
```

Future domain/model and feature UI packages are created by their owners when needed. No fake feature classes are included.

Enterprise data goes through the REST API. Google Maps SDK separately downloads Google map imagery; Play services supplies device location. MongoDB remains server-side enterprise persistence; SQLite is local cache only. See [dependencies](DEPENDENCIES.md), [team onboarding](../../docs/TEAM-ONBOARDING.md) and [final report](../../docs/PHASE-0-FINAL-REPORT.md).

## Google Maps local configuration

1. Use a Google Cloud project under your control. Enable billing and **Maps SDK for Android**, then create a key restricted to that API and to the Android application `com.smartsolar.mobile` with your signing certificate's SHA-1. Follow [Google's Android setup](https://developers.google.com/maps/documentation/android-sdk/start) and [API key restrictions](https://developers.google.com/maps/api-security-best-practices). No Cloud project or billing change was performed by this implementation.
2. From this Android directory, obtain your debug signing fingerprint with `.\gradlew.bat :app:signingReport`. Configure only that SHA-1 in the Google Cloud key restriction. Do not share keystores or passwords. A future release build needs its actual release signing fingerprint.
3. Copy the committed blank example once:
   ```powershell
   if (-not (Test-Path .\secrets.properties)) {
       Copy-Item .\secrets.properties.example .\secrets.properties
   }
   ```
   Edit ignored `secrets.properties` locally and set `MAPS_API_KEY=<your restricted Android Maps key>`. Do not add quotes. Never paste the real value in documentation, source, tests, screenshots or reports.
4. Gradle loads this file into the `MAPS_API_KEY` manifest placeholder. `BuildConfig.MAPS_CONFIGURED` contains only a boolean. Sync and rebuild after changing the property. Verify ignore behavior from the repo root with `git check-ignore mobile/SmartSolarMobile/secrets.properties`.
5. Without a key, builds still succeed and the app offers the API station list with a map-unavailable message. It does not initialize Maps with a fake key. A configured but invalid/restricted key can leave map tiles blank; the list remains usable. Check API enablement, billing, package and SHA-1 locally.
6. The key is necessarily present in a configured APK manifest; an ignored file prevents source disclosure, not extraction from the APK. Google Cloud application/API restrictions are required. Do not upload configured build artifacts to public issues.

The selected SDKs are Maps 19.2.0 and Play services Location 21.3.0, declared in the version catalog and validated by this project's build. No new web dependency or camera/QR SDK is added.

## Member 1 manual emulator checks

These have **not** been executed in this pass. Use disposable real API data created through the web/Swagger; no mock stations are included in the app.

1. Start MongoDB and the backend using the commands above. Verify `http://10.0.2.2:5000/health`. Start a Google APIs/Play emulator, install the debug APK and sign in with an Active Prosumer.
2. On Home tap **Find Stations**. Without granting location, confirm it lists actual active stations, their address, kWh capacity and battery-slot count. An empty database shows an empty state. With no local Maps key, confirm the list and detail still work.
3. Configure the restricted key, rebuild/reinstall, and repeat. Confirm markers use the stored station latitude/longitude. Tap a marker and a list item's View station; both must open that station's freshly fetched name, address, capacity, UTC schedule and active slot inventory.
4. Set an emulator location near your test stations using Extended Controls > Location. Enable device Location and tap **Find near me**. Grant approximate location; expect API results within 25 km, nearest first, with approximate distance. Locate a test station beyond 25 km and an inactive station; neither should appear in nearby results. Distances are great-circle estimates, not driving distances.
5. Deny location, deny again, and disable it in Android Settings. Confirm a helpful message and working all-stations fallback, no repeated unsolicited prompts. Restore permission/device location and tap Find near me again. Turn location off to exercise timeout/unavailable handling.
6. Tap **Show all active stations**, then Refresh / retry. Confirm the current API list replaces earlier results. Change a station's GPS/name through Backoffice and refresh; confirm marker/detail updates. Deactivate an unreferenced station and confirm it disappears; an old detail shows unavailable after refresh.
7. As GridOperator in web, create a slot and change its availability. Reopen/refresh Android detail; confirm actual start/end UTC and counts, including zero availability. No booking button or reservation action should exist.
8. Test API/network outage on list, nearby and details. Expect an error and working retry after recovery. Also test offline Maps tiles: station API/list availability is independent. Test an invalid key locally without sharing it.
9. Rotate, background/foreground, and navigate Back while API/location calls are pending. Confirm no crash, duplicate stale rows or old-session content. Repeat with API 26 and a current target-compatible device, portrait/landscape, light/dark mode, large fonts, TalkBack and system bars.
10. Repeat discovery as Active GridOperator. Backoffice mobile login remains rejected. Expire/deactivate a disposable account while a station screen is visible; refresh/resume should require login and clear invalid session/cache. Run the foundation expiry/401/SQLite checks above as well.

Only foreground approximate location is used. Location and nearby distance are not persisted to SQLite; its version-1 profile schema remains unchanged. No emulator, Maps tiles, real location result or device SQLite execution is claimed by JVM tests.
