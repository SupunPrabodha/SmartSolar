# Final all-member integration audit

Automated compilation and the existing/added regression suites pass after the focused fixes below. This is not final acceptance: unresolved shared coordination, QR eligibility/authorization and secret-remediation issues remain. Browser, emulator, camera, Maps and SQLite runtime acceptance were not executed.

## 1. Audit date

2026-09-26. Evidence is from this checkout and local commands, not hosted CI or a deployed environment.

## 2. Current branch

`Merge-M1-M4`. No checkout, branch creation, merge, fetch, commit, push or deployment was performed.

## 3. Current HEAD

`687fbbccceb9bb9eaf919f8a9f71747e442561e1` (Merge pull request #6 from SupunPrabodha/IT23163522). The audited fixes remain uncommitted on that HEAD.

## 4. Working-tree state before audit

Clean: `git status --short` returned no entries before edits. Actual root: `F:\Y4-S1\EAD\EAD-Assignment\Project\smart-solar-microgrid`. Ignored local configuration and generated outputs already existed. No local development database was deleted; Mongo tests use isolated temporary databases.

## 5. Merge-marker result

Tracked-text scanning found no unresolved conflict markers or duplicate single-line imports. Source XML parses successfully. The web/Java/C# builds also check syntax and duplicate definitions.

The initial backend build did fail: Member 4's MemoryReservations test fake did not implement Member 3's GetActiveBySlotAsync and GetActiveSlotsAsync. These methods now filter the fake's actual records rather than returning invented results. No test was disabled.

Web initially lacked the installed react-test-renderer dependency already declared in package.json/lockfile; older navigation expectations also still assumed Operations was disabled. Dependency restoration and updated positive/negative route assertions resolved these issues. Current `git diff --check` passes.

## 6. Files inspected and references

Documentation: README, CONTRIBUTING, TEAM-ONBOARDING, ARCHITECTURE, API-CONTRACT, DATABASE, BUSINESS-RULES, PHASE-0-ACCEPTANCE, PHASE-0-FINAL-REPORT, MEMBER-1-IMPLEMENTATION-REPORT, M1-M2-INTEGRATION-REPORT, MEMBER-3-IMPLEMENTATION-REPORT, Member 3 API/business/database notes, and MEMBER-4-RESERVATION-CONTRACT. No separate current Member 2 or final Member 4 implementation report was present; the Member 4 handshake contains checkpoint and later implementation notes.

Available reference PDFs inspected:

- EAD_Team_Development_Handover_Guide_v1.2.pdf, Downloads, 13 pages.
- EAD_SE4040_Assignment_2026.pdf, project Docs/DOCS EDITED, 8 pages.

Their rubric/scope requirements informed the audit; the user's integration-only boundary governed changes.

Principal code inspected:

- All four project references; Program.cs, JWT/auth middleware and controller route/role declarations.
- Domain entities/enums/constants; MongoMappings and MongoDbInitializer.
- StationService, SlotService, UserService, AuthService, ReservationService, ReservationRules, ReservationQueryService and their repository interfaces/implementations.
- Reservation DTOs, QR security service and API/read/QR/catalog/account integration tests.
- Web App, HomePage, ProtectedRoute, AuthContext/API client, StationsPage, UserManagementPage, reservation layout/list/form/detail/dashboard/history/search, API helpers and tests.
- Android main/debug manifests, network config, Gradle/package settings, ApiService/DTOs, auth/reservation repositories, SessionManager, SQLite helper, Home/Account/station/reservation/booking/QR activities, resources and JVM tests.

The scan covered tracked text and private-file patterns. This does not claim exhaustive dynamic testing of every line or malicious-input scenario.

## 7. Member 1 preservation

Station create/read/update/soft deactivation, UTC operating schedule, finite GPS/capacity validation, nearby API, slot create/update/availability/deactivation, overlap checks and optimistic catalog timestamps remain implemented. No catalog production code changed in this audit.

Web station/slot management retains Backoffice versus GridOperator write boundaries, search/filter, inactive inspection and loading/empty/error states. Android station discovery/detail, Maps markers using stored coordinates and foreground approximate location are present.

The existing five real-Mongo status tests still pass. The added all-member test also proves the protection rule against reservations created and transitioned by the integrated services, rather than direct status fixtures. Sequential protection is correct; concurrent catalog/allocation safety is not established (sections 11 and 27).

## 8. Member 2 preservation

Staff creation, Prosumer registration/pending activation, contact editing, deactivation and Backoffice-only activation/reactivation remain. NIC, roles and account states are unchanged. Existing account/catalog tests pass. UserRepository updates account fields without erasing Member 3's persistent ReservationWriteLock.

Web /users remains Backoffice-only and retains its pending queue and lifecycle actions. Android AccountActivity remains present, internal and reachable only from the Prosumer Home entry. Successful profile saves cache the API result; successful self-deactivation clears preferences/cache and returns to Login.

Known AccountActivity issues remain visible in source, unchanged from the prior audit:

- No independent onStart/onResume profile verification or automatic expiry navigation.
- A matching 401 clears session/cache through the interceptor, but AccountActivity displays generic failure rather than immediately returning to Login.
- Worker shutdown on destruction does not itself guarantee that an already-posted callback cannot update the old Activity.
- Late save responses cache against the currently valid session, without comparing the originating token, so account/session switching needs owner review and a device regression.

These are not claimed fixed or device-tested. SQLite has no plaintext password or JWT column; JWT/expiry remain in private preferences.

## 9. Member 3 preservation

The integrated service is registered and reachable. It resolves StationId from the persisted SlotId, checks active station/slot and capacity bounds, enforces same-Prosumer overlap, and uses server-derived accepted ScheduledStartAtUtc/ScheduledEndAtUtc snapshots.

Time rules remain:

- Start strictly after now and no more than seven elapsed days ahead.
- Update/cancel at least twelve hours before the accepted start.
- Replacement starts also meet the twelve-hour and seven-day limits.
- Pending or Approved update returns Pending and clears QR fields.
- Pending can be approved only before its accepted start, or rejected with a required remark.
- Terminal Rejected/Cancelled/Completed cannot be updated/cancelled.

Web GridOperator assisted creation/list/details/edit/approve/reject/cancel and Android Prosumer create/details/modify/summary exist. Available-slot listing currently filters slot fields/time/count only; it can advertise slots under inactive stations, although create subsequently rejects them. Approval rechecks status/time but not active related records or the target Prosumer's state. These are residual eligibility inconsistencies, not reasons to invent new models.

## 10. Member 4 preservation

Current/pending/history/search APIs and live Mongo counts are present and scoped to authenticated identity:

- Current: Pending/Approved with accepted end > server now.
- Pending: exact Pending, including elapsed Pending.
- History: terminal states, or Pending/Approved with accepted end <= now.
- Approved-future count: exact Approved with accepted start > server now.
- Search: controlled exact identifiers/status and inclusive accepted-start bounds.

Missing/invalid snapshots fail with 409 before time filtering/pagination. Query projection omits QR credentials. This audit fixes the merged booking-query DTO mapping to retain Member 3's rejectionRemark; the new real-Mongo test verifies it.

Web dashboard/history/search and lifecycle list/status filters are present. Android current/history, dashboard counts, QR display/scanner/result/completion are present. Dedicated native pending/search activities and dedicated web current/pending pages were removed in earlier member work; they were not silently restored. Web lifecycle filters provide a pending queue, while Android currently exposes current/history plus counts. Confirm that this reduced UI coverage satisfies the team's final rubric evidence.

## 11. Cross-member station/slot/reservation integration

The added AllMemberIntegrationTests test creates real Member 1 stations/slots, creates Pending reservations via Member 3, applies actual approval/rejection/cancellation or Member 4 QR completion, then invokes Member 1 guards.

It verifies:

| Actual lifecycle status | Station deactivation | Protected slot availability/deactivation |
| --- | --- | --- |
| Pending | Blocked | Blocked |
| Approved | Blocked | Blocked |
| Rejected | Allowed | Allowed |
| Cancelled | Allowed | Allowed |
| Completed | Allowed | Allowed |

It also verifies persisted string status, immutable ReservationId/ProsumerNic/StationId/SlotId, accepted snapshots, terminal history, summary counts and unchanged reservation documents after catalog operations.

**Unresolved race:** StationService/SlotService use only CatalogWriteGate. ReservationService uses a different, persistent per-Prosumer lock; it never enters the catalog gate. Station deactivation can check “no active reference” before reservation insertion while a reservation writer has already read an active station. Slot allocation changes AvailableSlots without advancing UpdatedAtUtc; a catalog write that passed its reference check can still match the old catalog timestamp and overwrite availability. This can occur within one API instance, not just across multiple instances.

Fixing this requires a reviewed cross-member coordination mechanism. No new transaction, lock collection or schedule/ID contract was introduced.

## 12. Reservation state/capacity consistency

| Operation | Observed implementation |
| --- | --- |
| Create | Conditionally decrement one slot place, then insert Pending with accepted snapshots |
| Same-slot update | Replace reservation; no count change; recalculate kWh check |
| Move | Acquire replacement place, conditionally replace reservation, release old place |
| Approve | Pending -> Approved; retain allocation |
| Reject | Pending -> Rejected with remark; separate bounded capacity release |
| Cancel | Pending/Approved -> Cancelled; clear QR; separate bounded capacity release |
| Complete | Approved -> Completed; record operator/time; **no slot-count release** |

Existing Mongo tests cover final-place contention, same-Prosumer overlap, moving, duplicate cancellation, lock preservation and stale lifecycle writes against completion. These do not prove aggregate kWh or catalog cross-record atomicity.

EnsureStationEnergyCapacityAsync sums active reservations before writing. Two different Prosumers can both read the same energy total and pass; with two available count places, both decrements can succeed, exceeding station kWh capacity. Same-slot energy increases have no shared slot lock either. Lowering station CapacityKwh also does not check already allocated reservation energy.

Ambiguous lifecycle writes retain a non-expiring Prosumer lock and conservative capacity for manual reconciliation. This prevents blind retries but is not an automatic recovery workflow. Completion's retained count versus terminal/non-active energy semantics needs an agreed release/consumption policy before acceptance.

## 13. QR/completion/idempotency verification

Confirmed by source and passing tests:

- Cryptographically random opaque SMG1 reference; SHA-256 hash stored, raw reference not stored.
- Approved status required; owning Prosumer isolation applies.
- Reissue rotates the hash; old/unknown/malformed references are rejected.
- GridOperator-only verify and completion; Backoffice/Prosumer completion denied.
- Completion rechecks status/hash and uses one conditional Mongo update requiring Approved and the matching hash.
- Added simultaneous HTTP completion test: exactly one 200 and one 409; one persisted Completed transition with operator/time.
- Existing sequential replay and stale lifecycle-write tests still pass.
- Completed appears in history and stops blocking Member 1 protection.

Limits requiring shared-owner decisions:

- Issue/verify/complete validate snapshot shape, not a permissible time window. Expired or arbitrarily early Approved reservations can still pass.
- They do not revalidate existence/active state/linkage of station/slot or the current Prosumer account; eligibleForCompletion is true after status/hash/snapshot checks only.
- The API documentation describes QR issuance as owner-Prosumer-only, but the actual controller and EnsureOwner helper also permit GridOperator issuance/rotation. No silent authorization change was made.
- Single-document exactly-once status transition is not proof of exactly-once capacity release or a distributed physical transfer.

## 14. Backend architecture/DI

Four layers and dependency direction remain intact; Domain has no MongoDB dependency. Clients use the REST API; the API remains the authoritative business service. No duplicate parallel station, slot, user or reservation model was introduced.

Program registers catalog/account services plus TimeProvider, ReservationRules, IReservationRepository, IReservationReadRepository, IReservationService, IReservationQueryService and IQrSecurityService once per service contract. Catalog and reservation-reference interfaces intentionally share an implementation. All controllers resolve in the real API tests.

Routes use /api/v1; authorization and ProblemDetails remain. Controllers propagate cancellation. Once uncertain multi-write lifecycle mutations begin, CancellationToken.None is intentionally used to finish compensation/recovery bookkeeping; read/validation cancellation remains supported. This does not provide cross-record transactional safety.

## 15. Web routing/navigation

All required routes exist:

- /, /login, /unauthorized, /users, /stations.
- /operator/reservations and its dashboard, history, search, new, :reservationId and :reservationId/edit children.

Wildcard route is unique. The GridOperator parent guard protects every operational child. /users is Backoffice-only, /stations serves both staff roles, and Prosumer cannot enter the staff web workspace.

Safe fixes:

- ReservationLayout now reuses HomePage's integrated shell instead of displaying disabled Stations/Operations and stale Phase-0 text.
- GridOperator sidebar exposes one link each to reservations, dashboard, history and search, with active-route highlighting.
- GridOperator's implemented history card is enabled, rather than labeled as an unimplemented transaction module.
- Search datetime-local inputs are converted to explicit UTC using the existing catalog helper.
- Route tests retain all prior role denials and add all operational child routes/shared-shell checks; the UI search test verifies exact UTC query values.

apiFetch still replaces caller authorization with the current JWT, sends none for login, ignores old-session 401, expires matching sessions before parsing, handles 204, preserves validation/traceId and handles malformed responses safely. Reservation clients use this same base convention.

## 16. Android manifest/activity/resource integration

Java/XML, com.smartsolar.mobile, minSdk 26 and compile/target SDK 35 remain. Retrofit/OkHttp/Gson and SQLiteOpenHelper remain; no direct Mongo client exists in Android.

All required activities are registered exactly once: Login, Home, Account, StationDiscovery, StationDetail, CreateReservation, ReservationDetails, ModifyReservation, ReservationSummary, CurrentBookings, BookingHistory, ReservationQr, QrScanner and QrVerificationResult. Only Login is exported.

App requests INTERNET, ACCESS_COARSE_LOCATION and CAMERA. Merged libraries add normal ACCESS_NETWORK_STATE and the app-specific signature receiver permission. Camera and ancillary camera features are optional. No fine/background location permission was added. Maps metadata retains the MAPS_API_KEY placeholder; no real value is reproduced.

HomeActivity's referenced IDs all exist in activity_home.xml, including station/account/current/history/module/scanner cards and dashboard counters. Prosumer receives account and reservation-management entries, no scanner. GridOperator receives current/history and scanner, with the Prosumer account/mutation card hidden. Home calls live dashboard API counts; non-401 metric failures currently leave old/loading metrics without a useful failure message and need UI acceptance review.

Source XML parses; resource compilation succeeds. Device role visibility, insets, permission flows and lifecycle are still manual.

## 17. Retrofit/API consistency

ApiService contains one definition for each requested operation:

- Shared login/current user.
- Stations/list/nearby/detail/slots.
- Profile update/self-deactivation.
- Reservation create/my/available slots/detail/update/cancel.
- Dashboard/current/history/QR issue/verify/complete.

Annotations, verbs and paths match the integrated controllers. DTOs use shared camelCase/string-status/snapshot fields. No duplicate UserResponse import/class or parallel slot identity was introduced. Current/history use paged responses, while Member 3's my/list use arrays.

The two Android packages ui.reservation (Member 3) and ui.reservations (Member 4) contain different activities, not duplicate registrations; they remain unchanged. Build/JVM results do not prove all runtime Activity/session paths.

## 18. Role/authorization matrix

| Capability | Backoffice | GridOperator | Prosumer |
| --- | --- | --- | --- |
| Staff web workspace | Yes | Yes | No |
| User management/reactivation | Yes | No | No |
| Station management writes | Yes | No | No |
| Slot management writes | No | Yes | No |
| Station discovery API | Yes | Yes | Yes |
| Mobile login | Rejected by mobile policy | Yes | Yes |
| Android account self-deactivation entry | No | No | Yes |
| Reservation operations | No | Assisted/global, approve/reject | Own create/read/update/cancel |
| Booking queries/counts | No | Operational scope | Own scope |
| QR issuance in actual API | No | Allowed; documentation mismatch | Own Approved |
| QR verification/completion | No | Yes | No |

API roles and current persisted account state enforce authorization independently of hidden UI controls. The QR-issuance mismatch needs explicit reconciliation, not an inferred policy change.

## 19. Database/shared contract integrity

Collections remain exactly UsersDetail, SolarStationInfo, EnergyBookingSlots and EnergyReservation. NIC remains UsersDetail._id and ProsumerNic business reference. StationId/SlotId/ReservationId remain compatible string IDs. Roles, account states and reservation enum values/string BSON mappings are unchanged.

Accepted nullable UTC snapshots are the single reservation scheduling source for both lifecycle and Member 4 reads. Legacy missing/invalid snapshots return 409 and are not reconstructed from mutable slots. QR hash/completion fields and reservation indexes already existed before the audit; no schema/index migration or extra collection was added.

Member 1 and Member 3/4 use the same EnergyReservation documents and Pending/Approved definition. No audit edit changes identity, scheduling or status semantics.

## 20. Security/secret audit

Current tracked-text scan found no matching Maps key, GitHub credential, JWT token, private-key block or credential-bearing URL. Config/seed/signing assignments inspected are placeholders/blank values or test data. No tracked private keystore/signing file was found. This pattern-based check is not an exhaustive secret-discovery guarantee.

The following local files exist, are ignored and are not tracked:

- mobile/SmartSolarMobile/secrets.properties
- mobile/SmartSolarMobile/local.properties
- web/smart-solar-web/.env.local

The Maps key previously exposed in reachable commit f7f879b6963a remains in Git history. Removing it in bbb3b87 did not revoke it. During this audit the user answered **“Not yet / unsure”** about revocation/rotation. Treat it as potentially active until the owner confirms revocation; configure a restricted replacement privately if needed. No key value was printed, no Git history rewritten and no cloud settings changed.

Processed release manifest has usesCleartextTraffic=false and no networkSecurityConfig. Debug currently permits exact hosts 10.0.2.2, localhost and 127.0.0.1; this loopback addition predates the audit. It does not weaken release transport. No trust-all certificate implementation was introduced.

## 21. Documentation consistency

Updated only the current README, ARCHITECTURE, TEAM-ONBOARDING, API-CONTRACT, BUSINESS-RULES and DATABASE descriptions needed to reconcile implemented reservations/operations, explicit UTC search, current transport and unresolved acceptance limits.

API-CONTRACT's obsolete proposed-only Member 3 section now lists the implemented routes and accepted snapshots. QR issuance policy mismatch is flagged, not silently redefined. Current business/database notes distinguish atomic single-document updates from multi-document lifecycle sequences.

Historical Phase-0, Member 1/3 and M1-M2 reports were not rewritten. Their earlier counts/readiness claims are not current runtime evidence. The Member 4 handshake contains historical UI claims (including removed screens); sections 10/26 here give current coverage and required follow-up.

## 22. Exact files changed by this audit

Modified (14):

1. README.md
2. docs/ARCHITECTURE.md
3. docs/TEAM-ONBOARDING.md
4. docs/API-CONTRACT.md
5. docs/BUSINESS-RULES.md
6. docs/DATABASE.md
7. src/SmartSolar.Application/Services/ReservationQueryService.cs
8. tests/SmartSolar.UnitTests/ReservationQrServiceTests.cs
9. tests/SmartSolar.IntegrationTests/ReservationQrApiTests.cs
10. web/smart-solar-web/src/pages/HomePage.jsx
11. web/smart-solar-web/src/pages/reservations/ReservationComponents.jsx
12. web/smart-solar-web/src/pages/reservations/SearchBookingsPage.jsx
13. web/smart-solar-web/tests/mergedNavigation.test.js
14. web/smart-solar-web/tests/member4Operations.test.js

Added (2):

15. tests/SmartSolar.IntegrationTests/AllMemberIntegrationTests.cs
16. docs/FINAL-INTEGRATION-AUDIT.md

No tracked files removed. No Android source/manifest, package/lockfile, production lifecycle repository or shared contract changed. Installed/generated outputs are ignored and excluded from this list.

## 23. Exact backend test/build results

From repository root:

```powershell
docker compose up -d --wait
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
try {
    dotnet restore SmartSolarMicrogrid.sln
    dotnet build SmartSolarMicrogrid.sln --configuration Release
    dotnet test SmartSolarMicrogrid.sln --configuration Release
} finally {
    Remove-Item Env:SMARTSOLAR_TEST_MONGO -ErrorAction SilentlyContinue
}
```

Mongo healthy; restore successful. Initial build: two CS0535 errors for missing fake repository members; corrected without weakening tests. Final build after all source/test changes: **success, 0 warnings, 0 errors, 2.65 seconds**.

Final executed tests: **181 unit + 76 integration = 257 total; 257 passed, 0 failed, 0 skipped**. Mongo integration enabled. Unit duration 818 ms; integration duration 10 seconds. Temporary environment variable removed. Tests used isolated databases and removed those fixtures.

The two added integration tests verify actual lifecycle/catalog interaction and simultaneous completion. Existing concurrency tests remain. Aggregate energy/cross-record race risks described above are not covered by passing guarantees.

## 24. Exact web test/build results

From web/smart-solar-web:

```powershell
npm.cmd test
npm.cmd run build
```

Final results: **71 passed, 0 failed, 0 skipped**, exit 0; Vite production build exit 0 in **1.94 seconds**.

Initial local node_modules lacked the already-declared react-test-renderer. npm ci encountered a Windows-locked Rollup binary. `npm.cmd install --no-save --package-lock=false` restored dependencies in place; exit 0, zero reported vulnerabilities, with cleanup warnings for locked old binaries. No package/lockfile edits or user process termination occurred. The JSX harness still emits a non-failing Node experimental CommonJS/ES-module warning. These are local results, not proof of a hosted clean-install run.

## 25. Exact Android test/build/lint results

From mobile/SmartSolarMobile:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest
```

Exit 0: **BUILD SUCCESSFUL in 1m 58s; 50 actionable tasks, all 50 executed**. Debug APK assembled.

JVM XML reports: **53 tests passed, 0 failures, 0 errors, 0 skipped** across nine suites. Lint: **0 errors, 122 warnings**:

| Warning | Count |
| --- | --- |
| AndroidGradlePluginVersion | 3 |
| GradleDependency | 21 |
| NotifyDataSetChanged | 1 |
| UseCompoundDrawables | 1 |
| MergeRootFrame | 1 |
| DisableBaselineAlignment | 2 |
| Overdraw | 3 |
| UnusedResources | 28 |
| UselessParent | 2 |
| Autofill | 3 |
| SetTextI18n | 46 |
| HardcodedText | 11 |

Processed release manifest: cleartext false, no debug network configuration. No configured manifest or APK secret value was printed. No Android code changed after this successful run.

## 26. Remaining manual acceptance checklist

Use disposable accounts/data and capture evidence without credentials, tokens or raw QR payload logs. Resolve the blockers below before treating this checklist as final acceptance.

1. Backoffice creates/registers and activates a Prosumer; pending login must fail.
2. Backoffice creates a station with actual GPS, capacity and complete operating hours.
3. GridOperator creates a valid slot; overlap/invalid inventory must fail.
4. Prosumer Android finds that station in list, detail and Maps.
5. Prosumer creates a reservation and sees authoritative accepted schedule/summary.
6. Invalid station/slot, full slot, excess energy and overlapping owner booking are rejected.
7. A start beyond seven days is rejected; exact boundary is covered by server-clock tests.
8. Modify/cancel at twelve hours succeeds; below twelve hours fails, including attempts to move later.
9. GridOperator sees the Pending reservation in the operational list/filter.
10. Approve and reject separate fixtures; required rejection remark remains visible in lifecycle/history responses.
11. Pending count matches exact Pending rows, including elapsed Pending.
12. Approved-future count matches only Approved accepted starts strictly after server now.
13. Owning Prosumer can issue/display an eligible Approved QR; issuance role policy must first be reconciled.
14. GridOperator grants camera permission, scans the QR and sees server-returned details.
15. Malformed/unknown/rotated/cancelled/rejected/completed references fail; agreed expiry/related-record eligibility cases must also fail after repair.
16. Valid eligible Approved transfer completes with real operator/time metadata.
17. Repeat/simultaneous completion gets one successful transition and conflict on the other.
18. Completed appears in history and leaves current/approved-future views.
19. Completed no longer blocks station/slot protection; verify the agreed capacity policy too.
20. Pending/Approved still block protected operations, including concurrent create/catalog attempts after coordination repair.
21. Profile edit, self-deactivation and Backoffice reactivation work; NIC/role/state remain immutable through contact edits.
22. SQLite has one current profile, no credential columns, and clears after logout/expiry/matching 401/self-deactivation; test late callbacks/account switching.
23. Maps/location and camera grant/deny/settings recovery work; camera-less device remains installable. Test rotation/background, pending requests, large fonts and light/dark themes.
24. Web role navigation, direct URL denial, active links, keyboard/narrow/zoom behavior, sign-out/expiry and API outage recovery remain correct.

Also run hosted CI on the eventual reviewed commit. IIS hosting, signing and submission screenshots were not performed by this audit.

## 27. Known risks

**B1 — Catalog/allocation race (acceptance blocker).** Independent catalog and reservation coordination permits deactivation or protected mutation around a not-yet-inserted reservation. Slot count changes do not invalidate the catalog timestamp. Review StationService.DeactivateAsync, SlotService mutation methods, ReservationService.CreateCoreAsync/UpdateAsync and ReservationRepository.TryAcquireCapacityAsync/ReleaseCapacityAsync together.

**B2 — Energy allocation race and capacity reduction (acceptance blocker).** Read/sum/check kWh is not atomic across different Prosumers; count decrements alone do not enforce the energy limit. Two 40 kWh requests can each pass against an empty 50 kWh slot with two free places. Existing station edits can lower CapacityKwh below already allocated energy. Agree a durable invariant and coordination mechanism before changing production writes.

**B3 — Completion eligibility/capacity contract (acceptance blocker).** Approved/hash/snapshot validation allows early/expired or invalid-related-record completion. Completion retains the slot count while excluding Completed from active energy totals. Owners must define the permitted window, related account/station/slot eligibility, and release/consumption policy, then implement/test them together. The audit does not choose those semantics.

**B4 — QR issuance authorization mismatch (acceptance blocker).** Code permits GridOperator issuance; documentation says owner Prosumer only. Resolve which contract is authoritative and test both permitted and forbidden paths.

**B5 — Historical secret exposure (release/handoff blocker).** Revocation of the exposed Maps key is unconfirmed; user answered “Not yet / unsure”. Owner must revoke/confirm revocation and privately configure any replacement.

Additional risks: persistent-lock recovery is manual; available-slot listing can advertise inactive parents; approval does not revalidate related eligibility; AccountActivity and other new screens require lifecycle/session checks; Android metrics can remain stale on non-401 errors; native pending/search coverage and mobile registration rubric evidence need owner review; no device/hosted CI/IIS result exists.

The concurrency and eligibility findings above are source-supported reachable interleavings/missing checks, not falsely reported executed stress/device tests. Passing sequential protection and single-document completion tests do not resolve them.

## 28. Blockers before final acceptance

Resolve B1–B5 through the shared owners, retain the established collection/ID/status contracts, add the corresponding race/eligibility regressions, and rerun the automated/manual gates. The user explicitly required stopping contract-changing fixes; therefore this audit reports the required decisions instead of silently changing concurrency architecture, schedule semantics, authorization policy or terminal capacity behavior.

Safe merge fixes are complete and reviewable. The current branch cannot yet be certified as the final acceptance/deployment baseline.

BLOCKED
