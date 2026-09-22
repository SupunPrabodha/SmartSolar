> Historical integration snapshot. The final pass removed the integrated template copies and added HomeActivity, mobile role checks and CI. See the [current Android guide](../README.md) and [final report](../../../docs/PHASE-0-FINAL-REPORT.md). Earlier results below are not the current acceptance record.

# Android Phase 0 integration report

Date: 2026-09-21.

Scope: integrate the prepared common foundation into the real `mobile/SmartSolarMobile` Gradle app. No backend/web changes or later feature implementation.

## Inspection and retained configuration

Inspected generated Gradle scripts, wrapper/catalog/properties, app manifests, Java sources, Views/theme/resources, tests, prepared template sources/resources and Android documentation. The actual repository is now under `F:\Y4-S1\EAD\EAD-Assignment\Project\smart-solar-microgrid`; the earlier workspace path had been renamed. File changes were applied only to this Android directory with the required filesystem approval.

Retained package/application ID `com.smartsolar.mobile`, Java application code, XML Views, AGP 8.8.2, Gradle 8.10.2, compile/target SDK 35, min SDK 26 and Java 11 source compatibility. Build ran with JDK 17.0.16. Local SDK paths/Gradle wrapper were not rewritten.

## Files added

Under `app/src/main/java/com/smartsolar/mobile/`:

- `data/local/AppDatabaseHelper.java`
- `data/remote/RetrofitClient.java`
- `data/remote/api/ApiService.java`
- `data/remote/dto/LoginRequest.java`
- `data/remote/dto/LoginResponse.java`
- `data/remote/dto/UserResponse.java`
- `data/remote/interceptor/AuthInterceptor.java`
- `data/repository/AuthRepository.java`
- `ui/auth/LoginActivity.java`
- `util/SessionManager.java`
- `util/SessionStore.java`
- `util/SessionExpiry.java`

Other additions:

- `app/src/main/res/layout/activity_login.xml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/res/xml/debug_network_security_config.xml`
- `app/src/test/java/com/smartsolar/mobile/SessionExpiryTest.java`
- `app/src/test/java/com/smartsolar/mobile/AuthInterceptorTest.java`
- `docs/ANDROID-PHASE0-INTEGRATION.md`

## Files modified/removed

Modified:

- `app/build.gradle.kts`: dependencies, BuildConfig generation and per-variant API URL.
- `gradle/libs.versions.toml`: centralized Retrofit/OkHttp/test dependency versions; removed unused ConstraintLayout alias/version.
- `app/src/main/AndroidManifest.xml`: Internet permission, login launcher, backup and release transport settings.
- `app/src/main/res/values/strings.xml`: common login/session messages.
- `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`: exclude SQLite/preferences from backup and device transfer.
- `README.md` and `DEPENDENCIES.md`: real-project instructions, dependency inventory, manual checks.

Removed unused generated Hello World `MainActivity.java`, `activity_main.xml`, `values/colors.xml` and arithmetic-only `ExampleUnitTest.java`. The generated instrumented package-name test remains but was not executed.

`template-src` and `template-res` were retained as historical source references, outside the Gradle source sets. The merged `app/src` files are now authoritative. No Kotlin application files, feature packages, secrets, signing credentials or MongoDB client were added.

## Dependencies added

- Retrofit and converter-gson **2.11.0**.
- OkHttp and logging-interceptor **4.12.0**.
- MockWebServer **4.12.0**, test-only.
- Existing AppCompat/Material/Activity/JUnit/AndroidX test dependencies retained.
- Removed unused ConstraintLayout implementation dependency.

SQLiteOpenHelper is supplied by Android, Gson by converter-gson. No feature libraries were introduced.

## Manifest and API configuration

Main manifest declares INTERNET, the single exported `ui.auth.LoginActivity` launcher, `allowBackup=false` and `usesCleartextTraffic=false`. No location, camera, storage or other dangerous runtime permissions were requested. AndroidX adds its normal signature-level receiver permission/components during manifest merging.

Debug-only manifest references a network security configuration that allows cleartext to **10.0.2.2 only**. All other HTTP hosts are denied. Release has no reference to that debug resource. TLS/certificate/hostname verification uses the platform defaults; no unsafe trust implementation was added.

Debug `BuildConfig.API_BASE_URL` is `http://10.0.2.2:5000/api/v1/`. Release is empty until a real HTTPS IIS URL is supplied using `-PsmartSolarApiBaseUrl=https://YOUR-IIS-HOST/api/v1/`. Gradle validates that release configuration. Empty release configuration fails closed at the login UI without network calls.

Verified the generated release manifest has no debug network-security entry and disallows cleartext; generated release BuildConfig contains no emulator address. This is a static variant check, not a deployed release runtime test.

## Common behavior

Login and `GET /users/me` use Retrofit through the REST API only. A repository worker performs network, session and SQLite work off the UI thread. Login success shows the profile/session controls without introducing a dashboard. Reopening/returning to the screen and Refresh fetch current server profile. Logout clears session/cache.

JWT/expiry are stored in app-private SharedPreferences, not SQLite. Expired tokens are rejected locally and clear cached session/profile data when evaluated. An open-screen expiry timer triggers reevaluation. HTTP 401 clears only the rejected session; a delayed rejection must not clear a newer login. Authorization is omitted from anonymous login requests. Debug logs use BASIC metadata only; headers/bodies are never enabled.

SQLite stores only the current API profile in `smart_solar_local.db / local_user`. It never stores passwords, password hashes or JWTs. Offline cached rows do not grant access. Enterprise validation and MongoDB remain on the C# API.

## Verification

Executed:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:processReleaseMainManifest :app:generateReleaseBuildConfig --console=plain
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain
```

The first compilation exposed a Gradle Kotlin DSL name collision with `java.net.URI`; an explicit `import java.net.URI` fixed it. The full build/test/lint run then returned **BUILD SUCCESSFUL**. Lint prompted version-catalog/unused-resource cleanup, which was applied before final validation.

**6 local tests passed**, zero failures/errors/skips:

- UTC timestamp parsing, including the API's fractional seconds.
- Missing/malformed expiry fails closed.
- Expiry boundary and token presence.
- Bearer header plus HTTP 401 invalidation.
- Anonymous login does not send or invalidate an existing token.
- Missing-token request and HTTP 503 session preservation.

Lint has no errors. Remaining notices concern newer dependency/plugin versions; the generated compatible toolchain and prepared stable dependency versions are intentionally retained. No lint warning suppression or baseline was added.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Test results: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint details: `app/build/reports/lint-results-debug.html`.

## Manual runtime checks still required

No emulator, live Android login, SQLite runtime inspection or connected instrumentation test was executed. The build/local tests do not imply these passed.

Follow README's exact commands and emulator checklist:

1. Start MongoDB and the configured API using the `http` launch profile on port 5000.
2. Start an API 26+ emulator, run the debug app, and log in with an Active account.
3. Verify Refresh/reopening calls `/users/me`; verify invalid/pending/deactivated credentials fail.
4. Verify expiry using a newly issued one-minute token, and HTTP 401 by deactivating a non-admin test account through another Backoffice session.
5. Verify network failure shows an error without a crash or offline authorization.
6. Use Database Inspector on `local_user`: one profile after login/refresh, zero after logout/expiry/401, and no credential/token columns.
7. Verify system bars/keyboard/rotation on the emulator and use the real HTTPS endpoint for later release deployment.

The repository remains without Git metadata; no Git repository, commit, remote or push was created.
