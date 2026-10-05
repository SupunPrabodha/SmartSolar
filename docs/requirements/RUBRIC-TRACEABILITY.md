# Rubric traceability — current implementation and submission evidence

Audit date: 2026-10-05. Source/test evidence is **not** hosted or device acceptance.
Official brief reviewed: EAD_SE4040_Assignment_2026.pdf; team allocation checked against the v1.2 handover guide.
See [current audit](../FINAL-STATION-REFERENCE-RUBRIC-AUDIT.md) for changes and actual command results.

| Requirement | Source / UI evidence | Regression evidence | Acceptance still required |
| --- | --- | --- | --- |
| FAT REST service, four layers, IIS | src/SmartSolar.Api/Program.cs; Application services; [architecture](../ARCHITECTURE.md); [IIS guide](../../deployment/iis/README.md) | ApiContractTests, enterprise/catalog/reservation integration tests | Run hosted API on one IIS worker; capture deployment evidence |
| Four collections, stable IDs, Mongo relationships | Domain/Constants/CollectionNames.cs; Infrastructure/Persistence/MongoMappings.cs; [database contract](../DATABASE.md) | MongoFoundationTests, ReservationMongoTests | Database design diagram, redacted representative records |
| Web role-aware login and navigation | App.jsx, ProtectedRoute, HomePage; Backoffice excludes operational reservation routes | mergedNavigation, apiClient, loginExperience Web tests | Backoffice/GridOperator browser and direct-URL checks |
| Staff creation, Prosumer management, pending approval | UserManagementPage, UserService, UsersController; email verification flow | MergedAccountCatalogTests, EnterpriseApiTests; emailVerification Web tests | Disposable accounts, real email approval/verification, deactivation/reactivation |
| Station create/edit/deactivate, GPS/schedule | StationsPage, StationLocation; StationService, CatalogRules | CatalogServiceTests, CatalogApiTests; location and stationLocationUi Web tests | Manual fields, click/drag, paste, stored GPS, 7-day UTC schedule; tiles failure |
| Slot management and active protection | StationsPage slot editor; SlotService; reservation reference query | CatalogServiceTests, AllMemberIntegrationTests, ReservationMongoTests | Pending/Approved protection, stale timestamp conflict, terminal history retained |
| Assisted and owner create/update/cancel | Web ReservationForm/Details; Android Create/Modify/MyReservations/Summary; ReservationService | ReservationServiceTests, ReservationApiTests; reservationScreens; ReservationRepositoryTest | Create/review/summary, authoritative error and cancellation flows |
| Server 7-day and 12-hour rules | Application/Services/ReservationRules.cs | ReservationRulesTests exact/below/above boundaries | Demonstrate accepted and rejected cases against running API |
| Current, pending, history, search/filter | Web operational pages; Android workspace booking/search fragments; ReservationQueryService | ReservationQueryTests, member4Operations, ReferenceSearchTest | Owner scope, all statuses, page navigation, no stale results on failure |
| Pending and approved-future counts | ReservationQueryService.DashboardAsync; backend read repository; Web OperationsDashboard; Android Summary | ReservationQueryTests, member4Operations | Compare counts with disposable authoritative records |
| Native Java/XML and SQLite | Android Gradle project; AppDatabaseHelper version 1 profile cache | Android compile/lint/session/API/JVM tests | Database Inspector: one current profile, no password/hash/JWT; logout/401 clearing |
| Android registration/profile/deactivation | RegisterActivity; Account/Profile workspace screens; auth/user REST API | EnterpriseApiTests, AuthFoundationTests, Android enterprise/session tests | Registration, approval + email verification, contact edit, self-deactivation |
| Maps and station selection | StationsFragment uses API station.latitude/longitude; StationDetailActivity | StationApiTest; build/resource checks | Real-device Maps key, tiles, nearby ordering, marker details; coarse permission grant/deny |
| Approved owner QR; operator scan/verify/complete | ReservationsController role attributes; ReservationService QR methods; ReservationQrActivity/QrScannerActivity/QrVerificationResultActivity | ReservationQrServiceTests, ReservationQrApiTests, QrSecurityServiceTests, Android QR tests | Owner issuance, wrong roles/status, camera denial, timing window, replay/concurrent completion |
| Stable presentation references | Web displayReference.js; Android DisplayReference.java; raw API values retained | Identical cross-platform fixtures; existing route/payload regressions | Same real record on both clients; copy, search, modify, QR/deep link regression |
| Required C# comments | All 98 handwritten production and 19 test .cs files have a leading project block | Source inspection; no backend code changed | Beginning-of-method comments are incomplete (e.g. ExperienceRepository.SetAvatarAsync, GetAvatarAsync, ReservationRepository.RecordQrVerificationAsync). Owners must add meaningful explanations, not generic generated filler. |
| Report and demonstration | Existing architecture/database docs and acceptance checklists are source material | No submitted report/evidence found in docs/report | Complete the evidence items below |

## MANUAL SUBMISSION ACTION REQUIRED

- Capture every Web and Android UI with disposable data, role, viewport/device/theme, and caption; no secrets or raw QR payloads.
- Produce the final high-level architecture, use-case and DFD diagrams. Existing architecture text diagram is a starting point, not a complete submitted figure set.
- Add database design with all four collections, identifiers, references and relevant indexes.
- Include source code as readable text in the report, citations, actual individual contributions, challenges and resolutions.
- Review all required beginning-of-method comments with each code owner; include tests in the team's final interpretation of “each .cs file”.
- Supply named contributors/student IDs and evidence (commits/PRs/tasks). The README allocation table describes responsibility, not a verified individual contribution claim.
- Record a video **no longer than five minutes**, publish it under the team's control and put the real accessible link in README.
- Verify repository access for assessors and link the reviewed submitted revision.
- Execute and record IIS, browser, real-device Maps/QR, SQLite, email and hosted CI checks.
- Confirm revocation/rotation of the historically exposed Maps key and restrict the private replacement. This audit does not certify that cloud action.

No completed screenshots, final report, video, deployment or personal contributions were invented.
