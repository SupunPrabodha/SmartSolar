# Final station location, display-reference and rubric audit

Date: 2026-10-05. **READY FOR STATION LOCATION + DISPLAY REFERENCE + RUBRIC MANUAL ACCEPTANCE.**
This is readiness for manual acceptance, not certification of final submission, deployment, Maps-key revocation or device behavior.

## Checkout and scope

- Branch: IT23187450. HEAD before/after: 2677c8c (Merge pull request #36 from SupunPrabodha/Merge-M1-M4).
- Worktree was clean before this pass. No commit, push, merge, fetch, branch switch or deployment.
- Reviewed the official EAD_SE4040_Assignment_2026.pdf and relevant architecture/ownership portions of EAD_Team_Development_Handover_Guide_v1.2.pdf, repository README/CONTRIBUTING/onboarding/contracts, and final integration, blocker, UI, Android workspace and visual-system reports.
- Earlier final-integration findings B1–B4 were addressed by the later blocker-remediation implementation. Current source retains the shared CatalogWriteGate, accepted schedule eligibility, owner-only QR issuance and conditional completion. Historical reports are not current failing test results.
- Backend production/tests, API contracts, Mongo schema/collections/IDs, JWT/roles/account states, QR payloads, navigation policy and existing Android Maps permissions/configuration are unchanged.
- Historical exposed Maps-key revocation is still **unconfirmed**. No cloud account or key was changed.

## Every updated UI surface

| Platform / surface | Visible change |
| --- | --- |
| Web Microgrid Stations | Consistent decorative station glyph tile, actual station name and secondary STN reference |
| Web Add Station | Live identity preview; retained manual latitude/longitude; map click/drag and safe Google Maps paste helper share those same fields |
| Web Edit Station | Same location tools initialized from existing coordinates; current selection updates immediately |
| Web Station Details | Identity header, address/coordinates, read-only pin preview, Open in Google Maps; slot rows and audit selector show SLOT references |
| Web Manage Reservations | REF labels, station names with secondary STN, SLOT labels; station filter uses readable searchable options |
| Web Active/Current Bookings | Shared table uses the same readable reservation/station/slot presentation |
| Web Pending Queue | Shared readable table and station filter; approval actions preserved |
| Web Booking History | Shared readable table; raw IDs removed from titles/tooltips |
| Web Search Bookings | Searchable REF/STN option labels preserve selected real IDs in server filters, pagination and export query |
| Web Reservation Details | REF heading, readable summary and approval/rejection/cancellation confirmations; no raw-ID disclosure panel |
| Web assisted Create/Edit, Review and action summaries | SLOT choices and typed references resolve to loaded real slot IDs; actual station names where available; readable review and returned summaries |
| Web workspace command search | Reservation hit labels show REF; existing navigation targets retain real IDs; guidance directs reference searches to Search Bookings |
| Android Stations | Existing decorative station tile retained; address now includes a secondary STN reference |
| Android Station Details | Matching decorative soft-square glyph tile, STN hero reference and SLOT availability references |
| Android My Reservations | REF/SLOT references on cards, actual station name + STN, readable cancel confirmation |
| Android Current | Shared card references and station name/STN |
| Android Pending | Shared card references and station name/STN |
| Android History | Shared card references and station name/STN |
| Android Search | Shared card references; REF/STN input resolves through authorized API records; Previous/Next controls expose later pages; failed/new reads clear stale rows |
| Android reservation details (expanded booking cards) | Copyable REF/SLOT block and wrapping station name/STN; existing actions unchanged |
| Android Create Reservation / slot picker / review | Station name/STN, SLOT options, typed reference suggestions and readable review |
| Android Modify Reservation / review | Existing REF/SLOT, replacement-slot suggestions using authorized available slots, readable review |
| Android Reservation Summary | Selectable REF/STN/SLOT values after existing operations |
| Android Transaction QR | Selectable REF/STN/SLOT text; opaque QR payload itself untouched |
| Android GridOperator booking lists | Same Current/Pending/History/Search card formatting; no new navigation destination |
| Android QR Verification Result / completion confirmation | Selectable REF/STN/SLOT and readable confirmation reference; actual verification/completion request IDs unchanged |
| Android Account booking history | REF replaces raw reservation text |
| Android Summary analytics fallback | Unresolved station names fall back to STN through the existing resolver |

Web Operations Dashboard metric UI contains no reservation identifiers and was not changed. Its real server counts and role gate remain intact. Login UI was not changed.

## Location implementation and dependency

Leaflet **1.9.4** is the only added package; package-lock is updated. It is loaded only when the map mounts. The map adapter isolates click/drag, pin synchronization, resize and cleanup for network-free tests.

- Manual coordinates retain their original fields and stationPayload conversion. Map and paste changes use that same form state.
- Empty/invalid fields produce a neutral world overview without a fabricated marker.
- Full Google Maps URLs support @latitude,longitude, q, query and !3d..!4d..; place coordinates take priority over camera center. Raw coordinate pairs also work.
- Parsing is local. No shortened-link fetch, backend URL fetcher, geocoder, additional data field or saved Google Maps URL.
- Invalid paste retains the previous selection. Short links show the requested full-URL/map-selection guidance.
- Coordinates must be finite, latitude within ±90 and longitude within ±180. Already valid longitude values are not needlessly recomputed during selection.
- Maps stay in a bounded responsive region. Keyboard users can use the labelled coordinate fields. Tiles/library failure leaves manual entry usable.
- Uses standard HTTPS OpenStreetMap tiles with attribution and ordinary browser caching; no offline bulk download. References: [Leaflet API](https://leafletjs.com/reference.html), [OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/).
- Station tiles are decorative existing glyphs, with no photo uploads, fields, permission or Android SDK changes.

## Display-reference convention

Canonicalize a valid GUID string by trimming, lowercasing and removing hyphens. Hash its 32 ASCII hexadecimal characters using FNV-1a with unsigned 64-bit overflow, reduce modulo 36^10, then encode ten uppercase base-36 characters with zero padding.

Prefixes: REF for reservations, STN for stations, SLOT for inventory slots. Ten characters give substantially more display space than six decimal digits. References are deterministic presentation aids, not globally guaranteed unique keys, authorization tokens or replacements for stored identifiers. Invalid/empty/all-zero IDs display “Unavailable”.

Shared non-secret fixtures:

| Input | Reservation | Station | Slot |
| --- | --- | --- | --- |
| 11111111-1111-1111-1111-111111111111 | REF-SB2J5ZFT6T | STN-SB2J5ZFT6T | SLOT-SB2J5ZFT6T |
| 22222222222222222222222222222222 | REF-8FHMZDSSO5 | STN-8FHMZDSSO5 | SLOT-8FHMZDSSO5 |
| 3ef796dd-1234-5678-9abc-12345678d770 | REF-FUOFG2HQ7M | STN-FUOFG2HQ7M | SLOT-FUOFG2HQ7M |

Web and Android fixtures match exactly, including uppercase/unhyphenated forms. No row index, ordering or pagination input is involved.

Real IDs remain in state, option values, API bodies/routes, intents and QR operations. Web station names use existing staff-authorized station reads and fall back to STN if unavailable. Web reference filters load authorized options when focused. Typed slot references are resolved against available/current slots, rejecting missing/ambiguous matches; they are never sent as fake GUIDs.

Android reference search reads the existing server-scoped search pages, resolves an unambiguous real ID and repeats the requested filtered page with that ID. It preserves 401/errors, rejects malformed references, returns empty when no match exists, and stops after 200 pages of 100 records with guidance to narrow the search. There is no cross-session reference cache or persisted mapping. This bounded scan is a small-dataset UI aid; a future large-scale indexed reference-search contract would require a separate backend review.

## Rubric findings

See [detailed traceability](requirements/RUBRIC-TRACEABILITY.md).

**Implemented, supported by source and automated tests:** role-aware Web/Android login; staff and Prosumer account management; pending approval/email verification; station CRUD/GPS/schedules; slots; assisted/owner reservations; server 7-day/12-hour boundaries; active protection for Pending/Approved only; current/pending/history/search; server pending and approved-future counts; native Maps station selection; owner Approved QR and GridOperator scan/verification/completion.

**Architecture retained:** Domain/Application/Infrastructure/API layering; REST-only enterprise client access; four exact Mongo collections; stable reference IDs; app-private version-1 SQLite profile cache without password/hash/JWT columns; release TLS; supported single-process IIS design. Current-source client scan found no direct Mongo connection/driver indicators.

**Gaps fixed here:** easier station location entry, consistent identity/reference presentation, Android Search first-page-only limitation, README repository/allocation/submission pointers, substantive rubric traceability, actionable IIS hosting instructions.

**Submission gaps remain:** no files in docs/report; no submitted screenshot set, use-case/DFD/database figure set, source-as-text final report, actual individual attribution, challenges section or demo video link. The architecture document has a text overview, which is only source material for the final report.

**C# comments:** 98 production + 19 test files have leading header blocks. Beginning-of-method comments are not uniformly complete; verified examples are ExperienceRepository.SetAvatarAsync/GetAvatarAsync and ReservationRepository.RecordQrVerificationAsync. Meaningful owner review/annotation is an explicit submission action, not falsely marked complete. No broad automated filler-comment rewrite was performed.

**Security manual action:** existing secrets.properties/local.properties/.env.local are ignored; targeted current-source key/private-key scan found no candidates and printed no values. This is not proof that historical secrets were revoked. Owner confirmation remains required for the historically exposed Maps key.

## Actual validation

| Gate | Result |
| --- | --- |
| Local Mongo | Docker engine was initially stopped; started Docker Desktop, then docker compose up -d --wait reported Mongo healthy |
| Backend Release build | dotnet build SmartSolarMicrogrid.sln --configuration Release — exit 0, 0 warnings/errors, 12.40s |
| Backend tests | dotnet test SmartSolarMicrogrid.sln --configuration Release with SMARTSOLAR_TEST_MONGO=localhost — **219 unit + 89 integration passed**, 0 failed/skipped; temporary variable removed |
| Web | npm.cmd test — **117 passed**, 0 failed/skipped; npm.cmd run build — exit 0, 86 modules, 1.85s |
| Android | clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest — **BUILD SUCCESSFUL in 1m 14s**, 51 tasks executed |
| Android JVM XML | **84 tests**, 0 failures/errors/skips |
| Android lint XML | **0 errors, 139 warnings**; no lint checks suppressed |
| Release manifest | package com.smartsolar.mobile, cleartext false, no debug network-security configuration; existing permissions retained |
| Repository | git diff --check passed; tracked + new text-file conflict-marker scan found no matches; branch/HEAD unchanged |

Focused new tests cover all requested paste formats, invalid/range/short-link handling, neutral/existing/manual map preview, click/drag field updates, locked/read-only interaction, coordinate controls, real-ID selection, cross-platform fixtures, slot-reference lookup, multi-page reference resolution, retained server filters/page, no-match/invalid input and 401.

Existing tests were retained. UI assertions now expect readable references while still asserting real raw-ID routes, payloads, mutations, authorization and time filters. Map tests use a fake Leaflet adapter and controlled UI state; no tile/network or device runtime success is inferred.

Logs are local ignored files under TestResults/rubric-*.log. APK: mobile/SmartSolarMobile/app/build/outputs/apk/debug/app-debug.apk. Do not publish configured APKs containing the private restricted Maps key.

## Manual acceptance / MANUAL SUBMISSION ACTION REQUIRED

- [ ] Web Backoffice: add/edit a disposable station by manual fields, map click, marker drag and every paste format. Confirm all methods update the same fields and saved API GPS; invalid/short-link input preserves the selected location.
- [ ] Confirm an existing station initializes correctly, invalid/empty coords have no marker, map stays selected after modal resize, and tiles/library failure leaves keyboard/manual input usable.
- [ ] Station detail: verify actual name/address/STN, coordinates, marker and Open in Google Maps; compare Android marker/list/detail and station/slot references with the same stored records.
- [ ] Check 1920/1440/1024/768/390 widths and 200% zoom: no modal overflow, retained attribution, keyboard focus/labels and manual coordinate access.
- [ ] GridOperator Web: Manage, Current, Pending, History, Search, Details, create/edit/review and action summaries. Copy REF/STN/SLOT; choose references in filters; confirm outgoing routes/payloads still use actual IDs. Backoffice must still be denied operational routes.
- [ ] Android Prosumer/GridOperator: compare identical refs on all listed screens; exercise slot suggestions, create/modify/cancel summaries, QR display/scan/verification/completion without changing payload semantics.
- [ ] Android Search: seed more than 20 authorized records, check Next/Previous, new filters reset to page 1, retained state on workspace return, empty results, failure/retry, expiry and account switching. A reference lookup beyond the bounded scan must ask for narrower filters.
- [ ] Android portrait/landscape, large fonts, light/dark themes, TalkBack, selectable references, rotation and lifecycle. Verify tile artwork is decorative and labels/controls remain reachable.
- [ ] Repeat real-device Maps, coarse-location/camera grant/deny, SQLite Inspector, logout/expiry/401 and replay/concurrent QR completion using the existing acceptance checklist.
- [ ] Finish method comments, all screenshots, diagrams, database design, source-as-text, citations, individual contributions and challenges in the submitted report.
- [ ] Add the actual ≤5-minute demo video link and confirmed individual names/IDs/evidence to README; check assessor repository/video access.
- [ ] Follow the [IIS guide](../deployment/iis/README.md), perform real SMTP/trusted HTTPS/runtime checks, verify one worker, run hosted CI and capture redacted deployment evidence.
- [ ] Revoke/rotate the historically exposed Maps key and privately restrict the replacement to package/API/signing fingerprint. Confirm this cloud action with the owner.

No browser viewport, emulator, physical-device, real map tiles, QR camera, SQLite Inspector, hosted CI, SMTP or IIS execution is claimed.

## Exact files changed

- Modified: `deployment/iis/README.md`
- Added: `docs/FINAL-STATION-REFERENCE-RUBRIC-AUDIT.md`
- Modified: `docs/requirements/RUBRIC-TRACEABILITY.md`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/account/AccountExperienceActivity.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/CreateReservationActivity.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/ModifyReservationActivity.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/ReservationSummaryActivity.java`
- Added: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/SlotReferenceInput.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/SlotSpinnerAdapter.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/QrVerificationResultActivity.java`
- Added: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/ReferenceSearch.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/ReservationAdapter.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/ReservationQrActivity.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/stations/StationDetailActivity.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/MyReservationsFragment.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/SearchBookingsFragment.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/StationsFragment.java`
- Added: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/util/DisplayReference.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/util/ReservationUiUtils.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/util/StationNameResolver.java`
- Modified: `mobile/SmartSolarMobile/app/src/main/res/layout/activity_create_reservation.xml`
- Modified: `mobile/SmartSolarMobile/app/src/main/res/layout/activity_modify_reservation.xml`
- Modified: `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_search_bookings.xml`
- Modified: `mobile/SmartSolarMobile/app/src/main/res/layout/item_reservation_card.xml`
- Modified: `mobile/SmartSolarMobile/app/src/main/res/values/strings.xml`
- Added: `mobile/SmartSolarMobile/app/src/test/java/com/smartsolar/mobile/DisplayReferenceTest.java`
- Added: `mobile/SmartSolarMobile/app/src/test/java/com/smartsolar/mobile/ReferenceSearchTest.java`
- Modified: `README.md`
- Modified: `web/smart-solar-web/package-lock.json`
- Modified: `web/smart-solar-web/package.json`
- Modified: `web/smart-solar-web/src/components/Experience.jsx`
- Added: `web/smart-solar-web/src/components/ReferencePicker.jsx`
- Added: `web/smart-solar-web/src/components/StationCaption.jsx`
- Added: `web/smart-solar-web/src/components/StationIdentity.jsx`
- Added: `web/smart-solar-web/src/components/StationLocation.jsx`
- Added: `web/smart-solar-web/src/components/stationMap.js`
- Modified: `web/smart-solar-web/src/pages/reservations/ReservationComponents.jsx`
- Modified: `web/smart-solar-web/src/pages/reservations/ReservationDetailsPage.jsx`
- Modified: `web/smart-solar-web/src/pages/reservations/ReservationFormPage.jsx`
- Modified: `web/smart-solar-web/src/pages/reservations/ReservationListPage.jsx`
- Modified: `web/smart-solar-web/src/pages/reservations/reservationUi.js`
- Modified: `web/smart-solar-web/src/pages/reservations/SearchBookingsPage.jsx`
- Modified: `web/smart-solar-web/src/pages/StationsPage.jsx`
- Added: `web/smart-solar-web/src/util/displayReference.js`
- Added: `web/smart-solar-web/src/util/location.js`
- Modified: `web/smart-solar-web/src/visual-system.css`
- Added: `web/smart-solar-web/tests/displayReference.test.js`
- Added: `web/smart-solar-web/tests/location.test.js`
- Modified: `web/smart-solar-web/tests/member4Operations.test.js`
- Modified: `web/smart-solar-web/tests/reservationScreens.test.js`
- Added: `web/smart-solar-web/tests/stationLocationUi.test.js`
