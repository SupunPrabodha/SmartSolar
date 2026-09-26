# Member 1 + Member 2 post-merge integration audit

**Classification: READY AFTER MANUAL FIX.**

Audited on 2026-09-26. The corrected working tree builds and passes the automated gates below. It contains both members' implementations. The intended develop handoff and historical Maps-key remediation need team action; this is not certification that develop already contains this tree. Browser/emulator runtime checks were not executed.

## 1. Checkout and audit boundary

- Actual repository: `F:\Y4-S1\EAD\EAD-Assignment\Project\smart-solar-microgrid`.
- Active branch: `Merge`.
- HEAD: `d6a9fb05e0840a799cfd783ff64b0c45f604b49f` (Member 2 pull-request merge).
- Local `develop` and `origin/develop`: `b16aa2785a369357186a7aeff251fe7ae4de6a91`, the Phase-0 foundation. Their trees differ from the audited HEAD.
- Working tree was clean before this audit. The fixes and tests listed below remain uncommitted.
- No fetch, checkout, branch creation, merge, commit, push, deployment or cloud-account change was performed. Remote-tracking names describe local knowledge, not a fresh remote check.

Read README, CONTRIBUTING, TEAM-ONBOARDING, ARCHITECTURE, API-CONTRACT, DATABASE, BUSINESS-RULES, PHASE-0-ACCEPTANCE, PHASE-0-FINAL-REPORT and MEMBER-1-IMPLEMENTATION-REPORT. No separate Member 2 implementation/handover report was present in docs. Also inspected the available team handover guide v1.2 and official SE4040 assignment PDF. These were reference material; the explicit integration-only request controlled the work.

## 2. Merge sanity and safe corrections

No unresolved conflict markers were found in tracked text. The initial builds exposed two concrete merge failures:

- Web: duplicate `Link` import in HomePage prevented production compilation.
- Android: HomeActivity referenced removed `R.string.operations` for GridOperator, preventing Java compilation.

Removed the duplicate import and restored Find Stations as the first Android module for both supported roles. Preserved Member 2's Prosumer My Account button.

Also removed a duplicated React wildcard route, removed the duplicate disabled station navigation entry, enabled the User Management home card, and placed User Management inside the existing shared shell so staff retain navigation and sign out. Reservations, Transactions and Operations remain disabled/planned.

No backend production code, DTO, domain model, Mongo mapping, status, dependency version or shared contract changed. Existing tests were retained.

## 3. Member 1 preservation

Source inspection and the existing catalog service/API/Mongo tests confirm:

- Station metadata, capacity, GPS, seven-day UTC schedule and soft deactivation remain implemented.
- GridOperator slot creation/editing, availability, validation and soft deactivation remain implemented.
- Exact timestamp-based optimistic concurrency remains; stale writes reject with 409.
- Pending and Approved references block station deactivation and protected slot mutation. Rejected, Cancelled and Completed do not.
- Five separate real-Mongo status tests cover those cases, target scoping, unchanged reservation documents/IDs/references, and retained concurrency checks.
- Nearby discovery reads actual stored coordinates, excludes inactive stations and returns server-calculated great-circle distances.
- Station and slot IDs and their parent references remain unchanged.

Web StationsPage and its API helpers retain staff role distinctions, search/filter, loading/empty/error handling and slot inventory controls. Android retains station list/detail, API nearby search, Maps markers and approximate foreground location handling. These source/build checks do not establish browser, Maps or device runtime success.

## 4. Member 2 preservation

The integrated account services/controllers retain staff creation, Prosumer registration, pending activation, profile reads/edits, deactivation and Backoffice-only activation/reactivation. NIC remains immutable; contact edits do not change role, state or credentials.

The new real-API/Mongo integration test exercises staff creation/login, pending Prosumer login rejection, Backoffice activation, self-profile editing, Backoffice-managed contact editing, immutable fields, GridOperator denial at user-management boundaries, Prosumer self-deactivation and Backoffice reactivation. It also creates Member 1 station/slot data and verifies that deactivation invalidates profile and catalog access through the same JWT. Reactivation with a fresh login restores access without changing station/slot identifiers.

Web user/prosumer forms, pending queue and status actions remain reachable at /users. Android AccountActivity retains API profile editing, successful-save SQLite caching, confirmation before self-deactivation and session/cache clearing after successful deactivation.

AccountActivity still has owner follow-ups visible in source: it does not perform its own resume-time profile verification/expiry navigation, and a 401 clears the underlying session through the interceptor but displays the generic failure instead of immediately navigating to Login. It also needs rotation/pending-request and system-inset device checks. These pre-existing Member 2 behaviors were not redesigned as merge fixes; do not infer that the common HomeActivity lifecycle guarantees cover this screen.

## 5. Web routes and navigation

| Role | Home | /users | /stations |
| --- | --- | --- | --- |
| Backoffice | Yes | User Management | Station management |
| GridOperator | Yes | Denied | Station views and slot management |
| Prosumer | Denied staff workspace | Denied | Denied web workspace; Android/API discovery remains available |

One sidebar link exists per enabled module. Completed modules have working home-card links; future modules remain visibly disabled. Both member pages use the shared shell.

Four added tests render the actual JSX with a test session and inspect actual route guards: Backoffice navigation/cards, GridOperator restrictions, User Management's shared shell/forms, and unique routes with anonymous/wrong-role denial. They are Node/server-rendered regression checks, not interactive browser tests. No dependency was added; the tests use the installed Vite/esbuild toolchain.

Existing HTTP/session tests still cover matching versus delayed 401 responses, malformed error responses, anonymous login, outages and validation messages.

## 6. Backend integration

The four layers remain Domain, Application, Infrastructure and Api. Project references have no cycle; Domain has no Mongo dependency. API is the enterprise service boundary.

Program.cs registers each required service interface once: user repository, catalog repository, reservation-reference reader, auth/user services, password/JWT services, station/slot services, catalog write gate and Mongo initialization. The catalog repository and reservation reader are distinct interfaces backed by the same repository type, not duplicate registrations for one interface.

Controllers retain /api/v1 routes, role attributes, asynchronous calls/cancellation tokens and common ProblemDetails handling. The actual host initializes in integration tests with both members' services resolved. Auth and catalog DTO/service namespaces do not collide. JWT validation still checks current persisted account state and role.

## 7. Android integration and transport

Native Java/XML in `com.smartsolar.mobile` remains intact. The only application change here is the shared HomeActivity station-title correction. Prosumer My Account and both roles' Find Stations remain connected; API/service/session code was preserved.

- App permissions: INTERNET and ACCESS_COARSE_LOCATION. Merged dependencies also declare normal ACCESS_NETWORK_STATE and an app-specific signature receiver permission. No camera, fine or background location permission.
- Debug network configuration denies general cleartext and allows only exact host 10.0.2.2.
- Processed release manifest: usesCleartextTraffic=false and no networkSecurityConfig. Its transport configuration has no debug HTTP exception.
- Login is the exported launcher; internal home, account and station activities remain internal.
- Existing Retrofit/JWT/401 handling and local profile cache remain; enterprise data is accessed through REST, not direct MongoDB.
- SQLite schema remains version 1 with profile fields only. JWT/expiry use private preferences; no plaintext password storage was introduced.
- A locally configured Maps key can be embedded in build artifacts. No APK or merged-manifest contents containing that key were printed or committed.

No emulator/device, Maps tile, location, UI or SQLite runtime success is claimed.

## 8. Database and Member 3 contracts

Exact collections remain UsersDetail, SolarStationInfo, EnergyBookingSlots and EnergyReservation. NIC maps to the user document identifier; station/slot/reservation GUID strings map to their existing identifiers. Roles/account/reservation enums retain string serialization.

Available station fields include StationId, IsActive, CapacityKwh, TotalBatterySlots and OperatingSchedule. Slots retain SlotId, StationId, StartAtUtc, EndAtUtc, TotalSlots, AvailableSlots and IsActive. Roles remain Backoffice/GridOperator/Prosumer; account states remain PendingActivation/Active/Deactivated.

The frozen protection query is unchanged: matching station/slot reference AND status in Pending/Approved. No reservation lifecycle or scheduling semantics were inferred from it.

Member 3 must address the already documented cross-record coordination dependency before integrating booking/allocation writes: the catalog gate is in-process and per-document timestamps do not make reservation creation and station/slot mutations one atomic operation. This audit neither defines that policy nor adds fields, transactions, locks, reservation APIs, 7-day/12-hour rules, QR or completion.

## 9. Secret/configuration audit

| Local file | Tracked | Ignored |
| --- | --- | --- |
| mobile/SmartSolarMobile/secrets.properties | No | Yes |
| mobile/SmartSolarMobile/local.properties | No | Yes |
| web/smart-solar-web/.env.local | No | Yes |

The current tracked-source pattern scan found no matching Google key, GitHub credential, JWT token, private-key block or credential-bearing URL. Reviewed signing/seed/configuration assignments were blank placeholders or explicit test values. No tracked private keystore/signing-key files were found. This scoped inspection is not proof that arbitrary secrets cannot exist.

**Historical exposure:** a Google Maps API key is present in reachable commit `f7f879b6963a` in secrets.properties.example. Commit `bbb3b87` removed it from the example, but did not erase the historical exposure. No value is reproduced here. Revocation/rotation has been requested for confirmation and remains unverified at report time. The key owner must revoke the exposed key or confirm it was already revoked, then configure a restricted replacement locally if needed. History rewriting and cloud changes were outside this audit.

## 10. Exact files changed

Modified:

- README.md — merged baseline, routes and integration-report link.
- docs/ARCHITECTURE.md — replace stale unimplemented station/slot description.
- docs/TEAM-ONBOARDING.md — enabled modules and current validation instructions.
- web/smart-solar-web/src/App.jsx — single wildcard route.
- web/smart-solar-web/src/pages/HomePage.jsx — duplicate import/navigation, enabled member cards and stale placeholder text.
- web/smart-solar-web/src/pages/UserManagementPage.jsx — reuse common shell.
- mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/home/HomeActivity.java — valid Find Stations title for both mobile roles.

Added:

- tests/SmartSolar.IntegrationTests/MergedAccountCatalogTests.cs — combined account/catalog API and Mongo regression.
- web/smart-solar-web/tests/mergedNavigation.test.js — four merged routing/navigation regressions.
- docs/M1-M2-INTEGRATION-REPORT.md — this report.

API-CONTRACT, DATABASE and BUSINESS-RULES already describe the merged contracts and frozen status rule consistently; they were not rewritten. Historical Phase-0 and Member 1 reports remain historical evidence.

## 11. Commands and exact results

From repository root, each command checked for failure before continuing:

```powershell
docker compose up -d --wait
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
try {
    dotnet restore SmartSolarMicrogrid.sln
    dotnet build SmartSolarMicrogrid.sln --configuration Release
    dotnet test SmartSolarMicrogrid.sln --configuration Release --no-build
} finally {
    Remove-Item Env:SMARTSOLAR_TEST_MONGO -ErrorAction SilentlyContinue
}
```

The first baseline pass also ran the requested test command without --no-build. Baseline: 38 unit + 14 integration passed. Final pass after the added test: Mongo service healthy; restore success; Release build success with zero warnings/errors in 8.04 seconds; **38 unit + 15 integration = 53 passed, zero failed/skipped**. Mongo integration was enabled, not skipped. The temporary environment variable was removed. The final --no-build test invocation used the immediately preceding successful Release build.

From web/smart-solar-web:

```powershell
npm.cmd test
npm.cmd run build
```

Both exit 0: **12 passed, zero failed/skipped**; Vite production build succeeded in 5.29 seconds. The Node test runner emitted a non-failing experimental CommonJS-to-ES-module warning from the new JSX test harness. No package or lockfile changed.

From mobile/SmartSolarMobile:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest
```

Exit 0: **BUILD SUCCESSFUL in 39s**, 50 actionable tasks, 17 executed and 33 up-to-date. Debug APK assembled; JVM tests executed and their XML reports contain **12 passed, zero failures/errors/skips**. Lint: **zero errors, 27 warnings** (3 AndroidGradlePluginVersion, 21 GradleDependency, 3 Autofill). Release-manifest processing was up-to-date; its existing processed output was inspected without exposing the Maps metadata value.

`git diff --check` passes. Automated results cover the corrected working tree, not hosted CI or an eventual develop integration.

## 12. Remaining manual checks

1. **Handoff branch:** reconcile the team's intended develop with the audited Merge tree and these uncommitted fixes through its reviewed Git process. Recheck the resulting HEAD and rerun CI before Member 3 takes that baseline. This audit performed none of those Git mutations.
2. **Historical key:** confirm revocation of the exposed Maps key; never send its value in chat/reports. Apply any replacement only in ignored local configuration and its appropriate Google restrictions.
3. **Web Backoffice:** sign in, visit Home/User Management/Stations; confirm one link per module, pending registration/activation, contact edit, deactivate/reactivate and station schedule/edit/deactivation. Reload direct URLs and verify sign out, expiry/401 and network errors.
4. **Web GridOperator:** verify Stations/slots work, /users is denied, and pending reservation/transaction/operations modules stay disabled. Check narrow layout, keyboard navigation and 200% zoom.
5. **Android Prosumer:** verify Home exposes both Find Stations and My Account. Save contact details, return/refresh and inspect the SQLite profile; confirm successful self-deactivation clears session/cache and requires Backoffice reactivation. Verify failed requests do not report success.
6. **Android both supported roles:** run the existing README Maps/list/detail/nearby permission grant/denial/recovery checks. Test API outage, rotation/background, large fonts and theme changes; ensure no reservation action. GridOperator has Find Stations and no Prosumer account-deactivation entry.
7. **Account screen owner follow-up:** exercise expiry, external deactivation/401, resume, rotation and in-flight requests on AccountActivity specifically. The source limitations in section 4 need Member 2 review; Home's tested session behavior is not a substitute.
8. **SQLite/session:** verify one profile row after login/refresh/profile edit and zero after successful self-deactivation/logout/expiry/matching 401, no credential columns, and no old-session cache after lifecycle changes. These are device checks, not JVM evidence.
9. Confirm hosted CI on the eventual reviewed develop baseline. No hosted result was observed here.

## 13. Member 3 handoff

No shared-schema or API-contract incompatibility was found between Member 1 and Member 2 in the audited tree. The concrete start-from-develop obstacle is that locally known develop still points to Phase 0. Resolve that and historical key remediation before treating this audit as the team handoff.

Once that baseline is established, Member 3 can consume the existing station/slot/account contracts. Related-record atomicity and reservation lifecycle rules remain Member 3/shared-owner work to agree before integrating its writers, not implemented guarantees of this audit.
