# Final enterprise experience hardening report

**Status: READY FOR ENTERPRISE EXPERIENCE MANUAL ACCEPTANCE.**
Validation date: 2026-09-29. This is an implementation and automated-validation result. Browser, physical-device/emulator, live SMTP delivery and SQLite Inspector acceptance remain unexecuted.

## Continuation boundary and defects resolved

The working tree already contained the password security service/repository, 20-minute hashed reset flow, atomic credential/session invalidation, early profile/avatar persistence, bounded notifications/audit and connected Web screens. Those changes were retained. The existing five-tab Android WorkspaceActivity remains the navigation root.

The interrupted integration run's five failures were the notification embedded-ID update, three diagnostic/validation compatibility checks and Swagger multipart generation. The authoritative read filter now targets BSON `_id`; ProblemDetails retains both `traceId` and `correlationId`; the authenticated IFormFile upload uses supported binding without the rejected annotation. Targeted tests passed before full validation.

This continuation completed client flows, shared feedback, role-safe search/export/history, notification delivery/read behavior, avatar/session lifecycle handling and regression tests. Final Web validation also caught a missing JSX brace in the slot-history panel and shared feedback state leaking between test cases; both were corrected before the final green run. A temporary C: disk-space failure was resolved by directing command-local TEMP/TMP to ignored `TestResults/enterprise-temp` on F:.

No commits, pushes, merges or deployments were performed.

## Password and session security

Forgot Password accepts NIC or email, returns a generic acknowledgement and queues only the identity/correlation reference in a bounded in-memory queue of 100. The worker performs account lookup/email asynchronously, avoiding identity-dependent SMTP latency in the HTTP response. Only eligible Active accounts receive a link. A one-minute per-account cooldown and shared authentication limiter (10 requests/minute per remote IP) bound abuse.

A cryptographically random 32-byte token is represented as 64 hex characters. Only its SHA-256 hash and request/expiry timestamps are persisted. Lifetime is **20 minutes**. A replacement request invalidates the prior token. The email link uses a URL fragment; Web consumes/removes it without a query-string request or referrer token.

Reset and authenticated Change Password reuse the existing password hasher. One conditional Mongo update changes the password hash, clears reset state, increments AccountVersion and SecurityVersion, and adds the security audit/notification. Reset checks the matching unexpired hash and version; concurrent reset has exactly one winner. Change Password verifies the current password. Central policy is 8-100 characters, reused by existing registration/staff DTOs.

New JWTs carry `security_version`. Existing request validation checks current account state, role and version through one projected user lookup; it does not fetch avatar bytes or perform a duplicate database lookup. Legacy missing-version tokens work only while the stored version is zero. After reset/change all previous JWTs fail, regardless of remaining expiry. Clients require login again, and Android clears its existing session/cache.

The existing SMTP sender now also sends reset and password-change acknowledgement emails over its established STARTTLS transport. A failed acknowledgement cannot roll back a committed password change. Raw tokens/passwords/provider diagnostics are not logged. The optional `VerificationEmail:ResetPageUrl` defaults to VerificationPageUrl's origin plus `/reset-password`; accepted page URLs are HTTPS or loopback HTTP without credentials/query/fragment. Existing verification email infrastructure and account-state rules are retained.

## Profile and avatar

My Profile exposes editable name/email/phone under existing rules; NIC, role and account state stay read-only. UserResponse adds only `profileComplete` and `avatarVersion`, not image bytes.

Authenticated own-avatar upload accepts a maximum 2,000,000-byte JPEG/PNG/still WebP, checks decoded format, dimensions (4096 per side), total pixels (12 million) and successful decoding, then normalizes to JPEG at most 512 pixels per side. Re-encoding removes input metadata. Bytes/content type/version are embedded in the same user document. GET is private/no-store; remove restores initials. Normal JSON and Android SQLite never contain images.

Completion is server-authoritative: valid required profile fields plus an avatar, followed by Save, set ProfileCompletedAtUtc. Avatar changes clear completion pending Save. Web and Android show an account-aware incomplete prompt with Complete Profile and session-only Skip. Avatar/notification/feedback state is cleared or refreshed across session changes.

Android retains Java/XML and the existing five-tab shell. The new non-exported AccountExperienceActivity provides focused profile/security, notification and anonymous recovery views from account/toolbar/login entry points. Photo Picker introduces no new storage permission.

## Notifications, feedback and activity

Each user has at most 100 persistent inbox entries with stable event ID, timestamp, priority, category, message, action/resource and read timestamp. Read-one/read-all use array updates, preserving concurrent inserts. The embedded BSON identity is `_id`, including array-filter and delivery-checkpoint queries.

Lifecycle audit entries carry a delivery checkpoint and recipient metadata. The background dispatcher retries retained pending entries approximately every 10 seconds, adding only absent notification IDs. Registration targets Backoffice; activation/reactivation targets the owner; reservation create/modify targets owner and GridOperators; approve/reject/cancel targets owner; completion targets owner and GridOperators. Password changes/reset create High security items atomically. Routine station/slot edits remain audit-only.

Web has one central feedback service and host, with Success/Info/Warning/Error, semantic icons, stable-ID deduplication, maximum three visible messages, loading-to-result update, accessible dismissal and hover/focus pause. The existing Toast adapter delegates to this host; no competing React Toastify package was introduced. Android uses one shared Snackbar helper with semantic types/deduplication and safe error references; duplicate QR completion Toast was removed.

The Web notification center has priority/unread filters, count and read actions. Its visible-page polling pauses after failure until explicit retry/focus recovery. Android provides corresponding inbox controls and authorized deep actions. Dashboard Recent Activity uses actual latest inbox data: five on Web, three on Android; empty/error states are explicit.

## Audit, search, export and diagnostics

Bounded server-authored AuditHistory arrays retain 100 events per user/station/slot/reservation. Existing conditional writes append lifecycle events without changing concurrency predicates. Coverage includes account lifecycle/profile/avatar/password, station and slot mutations, reservation lifecycle, QR issuance/successful verification and completion. Public audit DTOs expose only ID, time, actor, event and correlation ID. Own-user, staff catalog and reservation ownership/role guards apply; Backoffice operational reservation access is not broadened.

Web Ctrl/Cmd+K uses a debounced, keyboard-accessible command palette. Server search requires 2-80 characters, uses escaped case-sensitive anchored prefixes, returns five results per permitted kind and has a two-second database time limit. Backoffice searches users/stations; GridOperator stations/reservations; Prosumer active stations/own reservations. Selection goes through authorized list/detail routes.

Server CSV export applies role/owner scope and current supported filters. Explicit columns exclude credentials, reset/QR data, images and audit internals. Output is UTF-8 with BOM, CRLF, quoted/escaped fields and formula-prefix protection even after leading whitespace. Query limit is 1,001 to detect and reject exports over 1,000 rows; database time limit is five seconds. Web export buttons use active filter state and shared feedback.

Each request receives a server-generated X-Correlation-ID and scoped log reference. ProblemDetails, validation and rate-limit responses preserve both correlationId and traceId. Web CORS exposes the correlation header and Content-Disposition. Unexpected failure responses do not expose stacks/internal exception text. Mongo connection/timeouts become safe 503 responses. Clients distinguish validation, authorization, invalid session and service outage, show Retry/reference where applicable and never authorize from cached Android data alone.

## Exact persistence changes

Only these four application collections remain:

- `UsersDetail`
- `SolarStationInfo`
- `EnergyBookingSlots`
- `EnergyReservation`

No Notifications, AuditLogs, reset-token, avatar, GridFS or queue collection was added. Existing IDs, references, role/state/status strings and reservation/QR business rules remain unchanged.

| Document | Added members |
| --- | --- |
| UsersDetail | SecurityVersion (long, legacy default 0), PasswordResetTokenHash, PasswordResetExpiresAtUtc, PasswordResetRequestedAtUtc, ProfileCompletedAtUtc, AvatarBytes, AvatarContentType, AvatarVersion, Notifications, AuditHistory |
| SolarStationInfo | AuditHistory |
| EnergyBookingSlots | AuditHistory |
| EnergyReservation | AuditHistory |

Audit entries contain identity/time/actor/event/correlation and bounded delivery metadata: Delivered, RecipientNic, RecipientRole, ResourceId, Action, Category, Priority, Message. Inbox entries contain identity/time/read time/category/priority/message/action/resource. Lists default empty for legacy documents. Existing AccountVersion participates in credential/profile conditional updates; password changes increment SecurityVersion. No new SQLite table, column or schema version.

Added indexes:

- `ix_users_name_prefix`: FullName ascending.
- `ux_users_reset_hash`: unique PasswordResetTokenHash ascending, partial filter for string values.
- `ix_stations_name_prefix`: Name ascending.

Existing indexes and mappings are retained. Initializer and tests use the same four collection constants.

## API changes

All paths are under `/api/v1`; authenticated routes continue enforcing active account and current JWT version.

| Method/path | Contract |
| --- | --- |
| POST /auth/forgot-password | Anonymous `{identifier}`; generic accepted message, bounded queue/rate limit |
| POST /auth/reset-password | Anonymous `{token,newPassword}`; one-use atomic reset |
| POST /users/me/change-password | Authenticated `{currentPassword,newPassword}`; invalidates previous sessions |
| GET /users/me/avatar | Own normalized image or 404, private/no-store |
| PUT /users/me/avatar | Authenticated multipart field `file`; bounded decoded/normalized image |
| DELETE /users/me/avatar | Remove own avatar and completion state |
| GET /notifications | Own items; optional `unreadOnly`, `priority=High\|Medium\|Low`; global own unreadCount |
| POST /notifications/{id}/read | Mark own matching notification read |
| POST /notifications/read-all | Mark own retained unread entries read |
| GET /audit/{kind}/{id} | Authorized retained history; kinds users/stations/slots/reservations; users/me supported |
| GET /search?q= | Bounded role-aware prefix search |
| GET /exports/{kind}.csv | Authorized bounded CSV; kinds users/stations/reservations |

Export query members: Search, Status, StationId, ProsumerNic, FromUtc, ToUtc, ReservationId, IncludeInactive and View (history/current/pending). Dates require an offset. Unsupported kinds/filters fail rather than widening authorization. Existing own-profile endpoints now return completion/avatar metadata and Save updates completion state. Existing login gains authentication rate limiting; JWT response format remains compatible.

## Dependencies

API adds **SkiaSharp 3.119.4** and **SkiaSharp.NativeAssets.Linux.NoDependencies 3.119.4** for decoded image normalization (Windows native assets arrive through SkiaSharp). Package references/build verified with .NET 8. See [SkiaSharp package](https://www.nuget.org/packages/SkiaSharp/3.119.4) and [Linux native assets](https://www.nuget.org/packages/SkiaSharp.NativeAssets.Linux.NoDependencies/3.119.4). Runtime native loading on a deployment host still needs deployment acceptance.

No Web or Android dependency/version additions. Existing Retrofit, Material, AndroidX, React and SMTP/password infrastructure are reused.

## Final automated validation

These are fresh final results, not inherited historical counts.

| Gate | Result |
| --- | --- |
| .NET restore | Success |
| .NET Release build | Success; 0 warnings, 0 errors; 8.36 seconds |
| Backend unit tests | 219 passed, 0 failed, 0 errors, 0 skipped |
| Backend integration tests | 89 passed, 0 failed, 0 errors, 0 skipped; local Mongo enabled |
| Backend total | **308 passed** |
| Web tests | **86 passed**, 0 failed, 0 cancelled, 0 skipped |
| Web production build | Success; Vite 7.3.6; 1.58 seconds |
| Android clean/debug/JVM/lint/release manifest | **BUILD SUCCESSFUL**, 32 seconds; all 51 tasks executed |
| Android JVM tests | **71 passed**, 0 failures, 0 errors, 0 skipped |
| Android lint | **0 errors, 84 warnings** |
| git diff --check | Success; two existing Git CRLF-normalization notices, no whitespace errors |

Android warnings: 42 SetTextI18n, 21 GradleDependency, 3 AndroidGradlePluginVersion, 5 UnusedResources, 4 Overdraw, 3 UselessLeaf, 2 DisableBaselineAlignment and one each NotifyDataSetChanged, UseCompoundDrawables, MergeRootFrame, UselessParent. The new dynamically composed account UI contributes localization warnings; these are not all pre-existing. Compilation also notes deprecated API use in StationsFragment.

Commands from repository root (TEMP/TMP override was used for this machine's nearly-full C:):

```powershell
$env:TEMP = Join-Path (Get-Location) 'TestResults\enterprise-temp'
$env:TMP = $env:TEMP
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
dotnet restore SmartSolarMicrogrid.sln
dotnet build SmartSolarMicrogrid.sln --configuration Release
dotnet test SmartSolarMicrogrid.sln --configuration Release --logger 'trx;LogFilePrefix=enterprise-final' --results-directory TestResults/enterprise-final
```

Web directory:

```powershell
npm.cmd test
npm.cmd run build
```

Android directory, JDK 17:

```powershell
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest
```

Local evidence (ignored): `TestResults/enterprise-final/*.trx`, `TestResults/enterprise-web-tests.log`, `TestResults/enterprise-android-final.log`, Android `app/build/test-results/testDebugUnitTest/TEST-*.xml` and `app/build/reports/lint-results-debug.xml`. Mongo integration uses isolated disposable test databases; these are not tests against an enterprise/Atlas database.

## Repository validation

The final pattern scan covered 380 tracked/untracked nonignored text files: zero conflict-marker, credentialed Mongo URI, JWT bearer, Google key or private-key findings. The earlier credential-literal review also found no suspected live SMTP/password values in the changed source. No duplicate Android values resources were found. Private ignored secrets files and User Secrets were not copied into reports. No private profile-image binary was added. This is a scoped pattern scan, not a guarantee that historically exposed credentials have been revoked.

Only four collection constants are used. Feedback is centralized; the existing five-tab WorkspaceActivity remains authoritative and the added account Activity is a focused deep screen. Android resource compilation passed; no duplicate resource build error remains. The processed release manifest has usesCleartextTraffic=false and no debug networkSecurityConfig. No new dangerous permission was added.

## Limits and remaining acceptance

- Retention is the latest **100** notifications per user and **100** audit entries per entity, not an immutable compliance archive. Retry/deduplication applies to retained IDs/checkpoints. If an entity produces more than 100 changes before dispatch catches up, older pending events can be evicted; there is no durable external outbox.
- Password recovery queue is bounded and in memory; restart can lose unprocessed identities. Users can retry after the cooldown. A generic acknowledgement does not certify SMTP delivery.
- Authentication limiting uses the immediate remote IP; reverse-proxy forwarding/trust must be configured deliberately at deployment. No deployment configuration was changed here.
- Search is case-sensitive prefix search, not fuzzy/full-text search. Exports are capped at 1,000 and are bounded query snapshots, not streaming bulk reporting.
- Audit captures new operations only; historical events cannot be reconstructed. Cross-platform inbox/avatar visibility requires refresh/polling; no push/FCM.
- Some Android dynamically assembled text still needs localization cleanup, reflected in lint. Visual quality, accessibility, lifecycle behavior and physical-device performance remain manual gates.
- Browser/emulator/device, real SMTP, native image decoding on a deployed Linux/IIS host, Maps/camera, SQLite Inspector, hosted CI and IIS acceptance were not executed.
- Earlier credential exposure/rotation status is not resolved by this code pass; use current private configuration and confirm rotations with the credential owner.

See [manual acceptance](ENTERPRISE-EXPERIENCE-MANUAL-ACCEPTANCE.md) for exact setup and scenarios, including known/unknown recovery, invalid/replaced/expired/concurrent reset, old JWT rejection, cross-platform profile/inbox, recipient scopes, CSV formula protection, outage recovery and accessibility.

## Exact files changed

The following is the full current working-tree inventory for this feature pass. M = modified; A = newly added. No removals.

```text
A docs/ENTERPRISE-EXPERIENCE-MANUAL-ACCEPTANCE.md
A docs/FINAL-ENTERPRISE-EXPERIENCE-HARDENING-REPORT.md
M mobile/SmartSolarMobile/app/src/main/AndroidManifest.xml
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/api/ApiService.java
A mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/dto/NotificationInbox.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/dto/UserResponse.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/interceptor/AuthInterceptor.java
A mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/account/AccountExperienceActivity.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/auth/LoginActivity.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/QrVerificationResultActivity.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/AccountFragment.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/HomeFragment.java
M mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/WorkspaceActivity.java
A mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/util/EnterpriseFeedback.java
A mobile/SmartSolarMobile/app/src/main/res/layout/activity_account_experience.xml
M mobile/SmartSolarMobile/app/src/main/res/layout/activity_login.xml
M mobile/SmartSolarMobile/app/src/main/res/layout/fragment_account.xml
M mobile/SmartSolarMobile/app/src/main/res/layout/fragment_home.xml
M mobile/SmartSolarMobile/app/src/main/res/values/strings.xml
A mobile/SmartSolarMobile/app/src/test/java/com/smartsolar/mobile/EnterpriseApiTest.java
A src/SmartSolar.Api/Configuration/NotificationWorker.cs
A src/SmartSolar.Api/Configuration/PasswordRecoveryQueue.cs
M src/SmartSolar.Api/Configuration/SmtpVerificationEmailSender.cs
M src/SmartSolar.Api/Controllers/AuthController.cs
A src/SmartSolar.Api/Controllers/ExperienceController.cs
A src/SmartSolar.Api/Controllers/PasswordSecurityController.cs
A src/SmartSolar.Api/Middleware/CorrelationMiddleware.cs
M src/SmartSolar.Api/Middleware/ExceptionHandlingMiddleware.cs
M src/SmartSolar.Api/Program.cs
A src/SmartSolar.Api/Security/AvatarNormalizer.cs
M src/SmartSolar.Api/Security/JwtTokenService.cs
A src/SmartSolar.Api/Security/RequestIdentity.cs
M src/SmartSolar.Api/SmartSolar.Api.csproj
A src/SmartSolar.Application/Abstractions/Persistence/IExperienceRepository.cs
M src/SmartSolar.Application/Abstractions/Persistence/IReservationRepository.cs
M src/SmartSolar.Application/Abstractions/Persistence/IUserRepository.cs
A src/SmartSolar.Application/Abstractions/Security/IPasswordSecurityRepository.cs
A src/SmartSolar.Application/DTOs/Auth/PasswordRequests.cs
M src/SmartSolar.Application/DTOs/Users/CreateStaffRequest.cs
M src/SmartSolar.Application/DTOs/Users/RegisterProsumerRequest.cs
M src/SmartSolar.Application/DTOs/Users/UserResponse.cs
A src/SmartSolar.Application/Services/CsvWriter.cs
A src/SmartSolar.Application/Services/PasswordSecurityService.cs
M src/SmartSolar.Application/Services/ReservationService.cs
A src/SmartSolar.Application/Services/SessionVersion.cs
M src/SmartSolar.Application/Services/UserMappings.cs
M src/SmartSolar.Application/Services/UserService.cs
M src/SmartSolar.Domain/Entities/EnergyBookingSlot.cs
M src/SmartSolar.Domain/Entities/EnergyReservation.cs
A src/SmartSolar.Domain/Entities/ExperienceEvent.cs
M src/SmartSolar.Domain/Entities/SolarStation.cs
M src/SmartSolar.Domain/Entities/User.cs
A src/SmartSolar.Infrastructure/Persistence/AuditTrail.cs
M src/SmartSolar.Infrastructure/Persistence/MongoDbInitializer.cs
M src/SmartSolar.Infrastructure/Persistence/MongoMappings.cs
A src/SmartSolar.Infrastructure/Persistence/NotificationDispatcher.cs
A src/SmartSolar.Infrastructure/Persistence/Repositories/ExperienceRepository.cs
A src/SmartSolar.Infrastructure/Persistence/Repositories/PasswordSecurityRepository.cs
M src/SmartSolar.Infrastructure/Persistence/Repositories/ReservationRepository.cs
M src/SmartSolar.Infrastructure/Persistence/Repositories/StationCatalogRepository.cs
M src/SmartSolar.Infrastructure/Persistence/Repositories/UserRepository.cs
A tests/SmartSolar.IntegrationTests/EnterpriseApiTests.cs
A tests/SmartSolar.IntegrationTests/EnterpriseSecurityTests.cs
A tests/SmartSolar.UnitTests/EnterprisePolicyTests.cs
M web/smart-solar-web/src/api/apiClient.js
M web/smart-solar-web/src/App.jsx
M web/smart-solar-web/src/auth/AuthContext.jsx
A web/smart-solar-web/src/components/Experience.jsx
M web/smart-solar-web/src/components/Feedback.jsx
A web/smart-solar-web/src/enterprise.css
M web/smart-solar-web/src/main.jsx
M web/smart-solar-web/src/pages/HomePage.jsx
M web/smart-solar-web/src/pages/LoginPage.jsx
A web/smart-solar-web/src/pages/NotificationsPage.jsx
A web/smart-solar-web/src/pages/PasswordRecoveryPage.jsx
A web/smart-solar-web/src/pages/ProfilePage.jsx
M web/smart-solar-web/src/pages/reservations/BookingHistoryPage.jsx
M web/smart-solar-web/src/pages/reservations/CurrentBookingsPage.jsx
M web/smart-solar-web/src/pages/reservations/ReservationDetailsPage.jsx
M web/smart-solar-web/src/pages/reservations/ReservationListPage.jsx
M web/smart-solar-web/src/pages/reservations/SearchBookingsPage.jsx
M web/smart-solar-web/src/pages/StationsPage.jsx
M web/smart-solar-web/src/pages/UserManagementPage.jsx
A web/smart-solar-web/src/util/feedback.js
M web/smart-solar-web/tests/apiClient.test.js
A web/smart-solar-web/tests/enterprise.test.js
M web/smart-solar-web/tests/mergedNavigation.test.js
A web/smart-solar-web/tests/passwordRecovery.test.js
M web/smart-solar-web/tests/reservations.test.js
M web/smart-solar-web/tests/reservationScreens.test.js
```
