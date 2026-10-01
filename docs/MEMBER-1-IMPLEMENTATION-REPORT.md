# Member 1 implementation and validation report

Date: 2026-09-22. Branch: `IT23187450`; HEAD: `b16aa27`.

**Member 1 station management, slot inventory, web screens and Android nearby/Maps discovery are implemented and automated gates pass. The frozen Pending/Approved protection rule is implemented. Cross-record coordination with future Member 3 writers remains a separate integration dependency. Browser/device acceptance is still manual.**

The user requested only Member 1. The official assignment and team handover guide were read along with the required repository documents and existing models. Document workflow instructions were treated as reference requirements; no commit, push, merge, fetch, branch change, account change or deployment was performed. Existing authentication, roles, account lifecycle, collection names and reservation fields remain unchanged.

## Focused protection-rule finalization

The team's frozen rule replaces the earlier all-reference guard: **Pending and Approved block; Rejected, Cancelled and Completed do not**. A single Mongo status filter is combined with the target StationId/SlotId. Reader names, service messages/comments and existing test doubles now describe active reservations accurately. No schema, API shape, UI or dependency change was made.

Five new real-Mongo status cases each exercise station deactivation plus slot update, availability and deactivation. They verify references to another station/slot do not affect the target; reservation BSON remains unchanged; station/slot/reservation IDs and references are preserved; and terminal history does not bypass stale timestamp rejection. Existing HTTP authorization/ProblemDetails regressions remain.

Finalization commands and results:

| Check | Result |
| --- | --- |
| docker compose up -d --wait | Existing MongoDB service healthy |
| dotnet build SmartSolarMicrogrid.sln --configuration Release | Success, zero warnings/errors, 16.59 seconds |
| dotnet test SmartSolarMicrogrid.sln --configuration Release --no-build with SMARTSOLAR_TEST_MONGO set | **37 unit + 14 integration = 51 passed**, zero failed/skipped; all five new real-Mongo status cases executed |
| npm.cmd test | **8 passed**, zero failed/skipped |
| npm.cmd run build | Success, 4.08 seconds |
| .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug | **BUILD SUCCESSFUL in 32s**, 47 tasks: 1 executed, 46 up-to-date |

Mongo tests used isolated SmartSolarTests databases and removed them afterward. The temporary SMARTSOLAR_TEST_MONGO environment variable was removed. Gradle reused the unchanged JVM-test and lint results in this pass: existing XML reports contain **12 passing JVM tests**, zero failures/errors/skips; lint contains **zero errors and 24 dependency/AGP notices**. This is a successful requested gate run, not a claim that cached tests or device checks executed afresh. Web and Android source/configuration were not changed by this finalization.

Exactly these **10 existing working-tree files** were modified during this focused pass (no files added or removed):

- `docs/API-CONTRACT.md`
- `docs/BUSINESS-RULES.md`
- `docs/DATABASE.md`
- `docs/MEMBER-1-IMPLEMENTATION-REPORT.md`
- `src/SmartSolar.Application/Abstractions/Persistence/IStationCatalogRepository.cs`
- `src/SmartSolar.Application/Services/SlotService.cs`
- `src/SmartSolar.Application/Services/StationService.cs`
- `src/SmartSolar.Infrastructure/Persistence/Repositories/StationCatalogRepository.cs`
- `tests/SmartSolar.IntegrationTests/CatalogApiTests.cs`
- `tests/SmartSolar.UnitTests/CatalogServiceTests.cs`

The cumulative original Member 1 inventory remains below. No commit, push or merge was performed.

## Result and architecture

The existing four backend layers now contain station/slot DTOs, application services, persistence abstractions, a Mongo repository and two API controllers. All 53 handwritten C# files retain opening headers; new methods include purpose comments. Program.cs adds five service registrations only.

Backoffice can list/search, create, inspect, edit and soft-deactivate stations in the existing web shell. GridOperator can select a station, list/create/edit slots, set availability and deactivate slots. Forms show API errors, stale-write conflicts and success states, with loading/empty states and responsive layout.

Android Home opens Find Stations for Active Prosumer/GridOperator accounts. Discovery shows API records in a list and, when a local key is configured, on Google Maps. Find near me requests foreground approximate location and sends coordinates to the REST nearby endpoint. Selecting a marker/list row reloads station details and published slot availability through Retrofit. Permission denial/location timeout/key absence have list fallbacks. Requests are cancelled when screens stop; profile restoration, expiry and 401 behavior reuse the foundation.

No reservation action, reservation lifecycle, QR, operator completion, user-management UI or business dashboard was added.

## Domain and schema

- Only new field: `SolarStation.OperatingSchedule`, an embedded seven-entry `OperatingDay` list in `SolarStationInfo`.
- Day 1 Monday through 7 Sunday; fixed UTC hours in strict HH:mm; closed days have null times. 24:00 is allowed only for closing. Overnight hours must be split. All-closed schedules are valid.
- Existing stations without the field remain readable with an empty/unconfigured schedule; editing requires all seven days. No database backfill was applied.
- Existing StationId/SlotId, Mongo _id mappings, reservation references, collections, status enums and slot fields are preserved.
- Nearby distance is calculated server-side and never persisted. SQLite remains version 1 with only the local profile cache; no location, JWT or password columns were added.
- Updates use the exact latest updatedAtUtc as an optimistic concurrency token and atomically match it in MongoDB. Related consistency checks share an in-process write gate.

See [database contract](DATABASE.md#member-1-station-and-slot-contract) and [business rules](BUSINESS-RULES.md#implemented-member-1-rules-and-remaining-dependency).

## Endpoints and permissions

All routes use `/api/v1`, valid JWT and current Active-account enforcement.

| Method | Endpoint | Allowed role |
| --- | --- | --- |
| GET | /stations | Backoffice, GridOperator, Prosumer |
| GET | /stations/nearby | Backoffice, GridOperator, Prosumer |
| GET | /stations/{id} | Backoffice, GridOperator, Prosumer |
| POST | /stations | Backoffice |
| PUT | /stations/{id} | Backoffice |
| PATCH | /stations/{id}/deactivate | Backoffice |
| GET | /stations/{stationId}/slots | Backoffice, GridOperator, Prosumer |
| GET | /slots/{id} | Backoffice, GridOperator, Prosumer |
| POST | /stations/{stationId}/slots | GridOperator |
| PUT | /slots/{id} | GridOperator |
| PATCH | /slots/{id}/availability | GridOperator |
| PATCH | /slots/{id}/deactivate | GridOperator |

Staff can explicitly include inactive records. Prosumer requesting includeInactive=true receives 403; inactive record/parent discovery returns 404. Nearby always returns active stations only. Backoffice does not implicitly gain GridOperator writes. There is no station-assignment field or invented assignment-based restriction.

Create returns 201 and Location; reads/updates return 200; soft deactivation returns 204. Validation/role/missing/conflict errors retain ProblemDetails and 400/401/403/404/409. Request/response fields, UTC handling and timestamp requirements are in [API-CONTRACT.md](API-CONTRACT.md#member-1-endpoints).

## Dependencies and manifest

Added dependencies:

| Component | Dependency | Version |
| --- | --- | --- |
| Backend tests only | Microsoft.AspNetCore.Mvc.Testing | 8.0.29 |
| Android | com.google.android.gms:play-services-maps | 19.2.0 |
| Android | com.google.android.gms:play-services-location | 21.3.0 |

No web or backend application dependency was added. Android versions use the existing Gradle version catalog. Native Java/XML, AGP 8.8.2, Gradle 8.10.2, JDK 17 and SDK 35 remain.

Manifest additions: ACCESS_COARSE_LOCATION, two non-exported station activities, and a Google Maps key placeholder. INTERNET and the existing launcher remain. The merged manifest also contains Maps' normal ACCESS_NETWORK_STATE and AndroidX's signature receiver permission. No fine/background location or camera permission is present.

DEBUG API URL remains `http://10.0.2.2:5000/api/v1/`. Debug-only network security permits emulator-host HTTP. Release manifest processing succeeds and inspection confirms cleartext remains false with no debug network-security configuration. No certificate/hostname-validation bypass was introduced.

## Tests added

- **33 Member 1 unit cases** in CatalogServiceTests: station create/update/deactivation, coordinate/capacity/count validation, missing records, seven-day schedule validation, slot creation/edit/availability/deactivation, inactive/missing parents, counts/times/capacity, overlapping versus touching windows, stale writes, reference protection, nearby coordinate/radius/active filtering, ordering and distance.
- **One real API/Mongo integration scenario** in CatalogApiTests: isolated real Mongo database, actual API host/JWT authorization and account lookup, allowed/denied roles, 400/401/403/404/409 ProblemDetails, 201/204 responses, persisted IDs/references/schedule, literal search, optimistic Mongo update rejection, legacy schedule reading and active reservation-reference guards. Fixtures are generated in a uniquely named test database and removed afterward. This does not exercise browser login or a Google Maps device.
- **Four new web tests**: UTC input round-trip with millisecond precision, rejection of ambiguous/invalid dates, station payload/concurrency token/closed-day normalization, and validation ProblemDetails field messages without session clearing. Existing four session regressions retained.
- **Three Android JVM tests**: nearby query coordinates/radius and returned server distance, slot IDs/counts/UTC fields, and separate list/detail read paths with schedule deserialization. Existing nine session/role tests retained.

The API regression fixture now uses Pending to verify protected-reference 409 responses. Five additional Mongo-backed status cases prove Pending/Approved block and Rejected/Cancelled/Completed permit otherwise-valid station/slot changes. They also verify stale-write rejection, target scoping, and unchanged reservation BSON, IDs and references. These tests exercise protection queries, not reservation lifecycle transitions.

## Initial implementation commands and results

Repository root:

```powershell
docker compose up -d --wait
dotnet restore SmartSolarMicrogrid.sln
dotnet build SmartSolarMicrogrid.sln --configuration Release --no-restore
$env:SMARTSOLAR_TEST_MONGO = 'mongodb://127.0.0.1:27017'
try {
    dotnet test SmartSolarMicrogrid.sln --configuration Release
} finally {
    Remove-Item Env:SMARTSOLAR_TEST_MONGO
}
```

Compose: MongoDB healthy. Restore: success. Final explicit Release build: **success, zero warnings/errors, 3.55 seconds**. Final tests: **37 unit + 9 integration = 46 passed, zero failed/skipped**. Mongo integration was enabled; the API test host used an isolated database and random test-only signing key. Temporary environment variable removed. No enterprise database was cleared.

From `web/smart-solar-web`:

```powershell
npm.cmd test
npm.cmd run build
```

**8 tests passed, zero failed/skipped. Production build succeeded** (2.04 seconds in the final run). These are local Node regression tests and compilation, not interactive browser acceptance.

From `mobile/SmartSolarMobile`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest
```

**BUILD SUCCESSFUL in 1m 4s; 50 actionable tasks, all 50 executed.** XML reports confirm **12 JVM tests passed**, zero failures/errors/skips. Lint: **zero errors, 24 dependency/AGP upgrade notices**, no remaining new feature/resource/API-compatibility warnings. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release manifest processed and inspected; no production-signed release or deployment was performed.

`git diff --check` found no whitespace errors. The local Maps property file is ignored and its blank example is not ignored. Source/config/docs scan found no matching Google API key, GitHub token or private-key patterns; this is a bounded pattern scan, not a comprehensive secret audit. No source was staged. Build outputs remain ignored.

## Required manual Maps configuration

Follow [Android Maps setup](../mobile/SmartSolarMobile/README.md#google-maps-local-configuration):

1. Enable Maps SDK for Android and billing in your own Google Cloud project.
2. Run `.\gradlew.bat :app:signingReport` in the Android directory.
3. Restrict an Android Maps key to `com.smartsolar.mobile`, the correct debug SHA-1, and Maps SDK for Android.
4. Copy `secrets.properties.example` to ignored `secrets.properties`, enter the key locally as MAPS_API_KEY, sync/rebuild/install.
5. Use an emulator image with Google APIs/Play services.

No real Maps key or Google Cloud configuration was supplied by this pass. Configured keys reside in the APK manifest, so Google application/API restrictions remain necessary. Never commit or share the key, keystore, configured APK or screenshots containing secrets.

## Exact manual web checks still required

1. Start Mongo/API from the repository root with `docker compose up -d --wait` and `dotnet run --project .\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https`. From `web/smart-solar-web`, run `npm.cmd run dev`; use its displayed local URL and the existing trusted HTTPS API configuration.
2. Sign in as Active Backoffice. Open Microgrid stations from Home/navigation. With no stations, verify the empty state. Add a disposable station with real test GPS coordinates, positive capacity/count and all seven UTC schedule entries. Verify success, detail values and persistence after reload.
3. Edit name/address/coordinates/capacity/schedule, including closed days and 00:00–24:00. Reopen and verify saved values. Verify schedule labels are UTC. Existing unscheduled data should say unconfigured and require a complete schedule on edit.
4. Test name/address search and Include inactive. Confirm literal special characters are treated as text. Test no matches and clear the filter.
5. Test required fields and invalid latitude/longitude/capacity/count/hours. Browser validation should be helpful; repeat invalid payloads in Swagger to confirm server 400 independently of UI. Duplicate/missing weekdays and open-after-close must fail.
6. Open the same station in two browser tabs. Save one edit, then submit the older edit. Expect 409 and no overwrite. Reload details, review current data and retry intentionally.
7. Sign in as Active GridOperator. Select the station; verify no station add/edit/deactivation actions. Create a slot with UTC start before end, total within station count and availability within total. Reload and confirm the actual persisted slot. Table times use browser local timezone; forms explicitly use UTC.
8. Edit the slot; change availability to zero and then a valid positive count. Test end <= start, negative/zero totals, negative availability, availability > total, total > station count and a conflicting active time window. Expect validation/conflict errors and unchanged stored data. Touching non-overlapping windows should work.
9. As Backoffice, try reducing station battery count below an active slot total; expect 409. As GridOperator, deactivate an unreferenced slot and verify inactive history remains visible. Test creation/availability at an inactive station via Swagger; expect conflict.
10. As Backoffice, deactivate a disposable station with no reservation references. Confirm it becomes inactive, is hidden by the default list, remains in Include inactive, and vanishes from Android discovery. No historical station/slot document should be deleted.
11. With disposable team-provided fixtures, verify Pending and Approved references cause station deactivation and slot mutations to return 409. Rejected, Cancelled and Completed must not trigger that guard; otherwise-valid changes should succeed while IDs/references remain stored. Use current updatedAtUtc values and confirm stale edits still return 409. The automated tests create isolated fixtures; do not mutate enterprise data to manufacture manual evidence.
12. Verify anonymous requests get 401; Prosumer is redirected to Android by the web foundation and cannot use staff writes via the API; Backoffice slot writes and GridOperator station writes return 403.
13. Test API outage and retry; session expiry/401; profile refresh and sign out; Back/reload; narrow mobile-width browser, keyboard-only operation, validation focus and 200% zoom. Confirm no booking, QR, completion or fabricated dashboard controls appeared.

## Exact manual Android checks still required

Execute the ten-step [Member 1 emulator checklist](../mobile/SmartSolarMobile/README.md#member-1-manual-emulator-checks), plus the retained foundation session/SQLite checks. It covers:

- No-key list fallback and correctly configured Maps rendering.
- Stored-coordinate markers, marker/list detail navigation and real slot availability.
- Emulator location near real test records, 25 km radius/nearest order, outside-radius and inactive exclusion.
- Approximate permission grant/denial/recovery and location-off timeout.
- All-stations reset, refresh, changed station metadata and deactivation.
- API outage, Maps/key failure, retry and zero availability.
- Back/rotation/background while requests are pending; API 26/current emulator; themes, large fonts, TalkBack and insets.
- Prosumer/GridOperator access, Backoffice rejection, expiry/401 and SQLite clearing.

**No manual browser session, device Google Maps/location/camera test, emulator UI test or device SQLite test was executed or claimed.** Hosted CI was not run.

## Member 3 dependency and deliberately deferred contracts

The team has frozen active reservation protection as Pending or Approved. Both station and slot queries consume that exact definition; Rejected, Cancelled and Completed no longer block. This resolves the earlier conservative-guard/status-definition blocker.

Member 3 still owns reservation lifecycle and authoritative scheduling/eligibility beyond this protection rule. Cross-record concurrency remains an integration concern: the current write gate protects only this API process, not another instance or a future reservation writer. Integrate reviewed coordination before concurrent Member 3 writes or multi-instance deployment. Existing optimistic timestamp checks are preserved.

Deliberately not added: reservation scheduled datetime, status enum values, lifecycle endpoints, 7-day/12-hour rules, approval workflows, capacity-allocation transactions, QR rules, completion transitions, operator assignment, GeoJSON/index schema or new collections. Station schedule remains metadata; no slot-within-operating-hours rule was introduced.

## Exact files added (30)

- `docs/MEMBER-1-CONTRACT-NOTES.md`
- `docs/MEMBER-1-IMPLEMENTATION-REPORT.md`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/dto/NearbyStationResponse.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/dto/SlotResponse.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/dto/StationResponse.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/stations/CatalogActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/stations/StationDetailActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/stations/StationDiscoveryActivity.java`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_station_detail.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_station_discovery.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/item_station.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/item_station_text.xml`
- `mobile/SmartSolarMobile/app/src/main/res/values/stations.xml`
- `mobile/SmartSolarMobile/app/src/test/java/com/smartsolar/mobile/StationApiTest.java`
- `mobile/SmartSolarMobile/secrets.properties.example`
- `src/SmartSolar.Api/Controllers/SlotsController.cs`
- `src/SmartSolar.Api/Controllers/StationsController.cs`
- `src/SmartSolar.Application/Abstractions/Persistence/IStationCatalogRepository.cs`
- `src/SmartSolar.Application/DTOs/Slots/SlotDtos.cs`
- `src/SmartSolar.Application/DTOs/Stations/StationDtos.cs`
- `src/SmartSolar.Application/Services/CatalogRules.cs`
- `src/SmartSolar.Application/Services/SlotService.cs`
- `src/SmartSolar.Application/Services/StationService.cs`
- `src/SmartSolar.Domain/Entities/OperatingDay.cs`
- `src/SmartSolar.Infrastructure/Persistence/Repositories/StationCatalogRepository.cs`
- `tests/SmartSolar.IntegrationTests/CatalogApiTests.cs`
- `tests/SmartSolar.UnitTests/CatalogServiceTests.cs`
- `web/smart-solar-web/src/pages/StationsPage.jsx`
- `web/smart-solar-web/src/util/catalog.js`
- `web/smart-solar-web/test/catalog.test.js`

## Exact files modified (21)

- `.gitignore`
- `README.md`
- `docs/API-CONTRACT.md`
- `docs/BUSINESS-RULES.md`
- `docs/DATABASE.md`
- `mobile/SmartSolarMobile/DEPENDENCIES.md`
- `mobile/SmartSolarMobile/README.md`
- `mobile/SmartSolarMobile/app/build.gradle.kts`
- `mobile/SmartSolarMobile/app/src/main/AndroidManifest.xml`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/data/remote/api/ApiService.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/home/HomeActivity.java`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_home.xml`
- `mobile/SmartSolarMobile/app/src/main/res/values/strings.xml`
- `mobile/SmartSolarMobile/gradle/libs.versions.toml`
- `src/SmartSolar.Api/Program.cs`
- `src/SmartSolar.Domain/Entities/SolarStation.cs`
- `tests/SmartSolar.IntegrationTests/SmartSolar.IntegrationTests.csproj`
- `web/smart-solar-web/src/App.jsx`
- `web/smart-solar-web/src/api/apiClient.js`
- `web/smart-solar-web/src/pages/HomePage.jsx`
- `web/smart-solar-web/tests/apiClient.test.js`

No tracked files were removed. Generated/ignored build outputs are not included in these source-file lists.
