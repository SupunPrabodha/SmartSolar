# Final Android workspace refactor

## Scope and checkout

Branch: `Merge-M1-M4`. Starting HEAD: `8d77ac9fd3ab2b52ef3a4d3b00fd3be0ea62edab`. The original migration began with a clean worktree. This continuation began with the migration already applied: 14 modified tracked files, 16 deleted tracked files and 16 untracked status entries (including the workspace source directory). These edits were preserved and inspected; the migration was not restarted. The existing visual redesign was preserved.

This is an Android presentation/navigation refactor. Backend, web, API contracts, MongoDB, SQLite schema, repositories, JWT/interceptor rules, reservation rules and QR cryptography/authorization were not changed. No commit, push, merge or deployment.

## Continuation review and correction

Source review confirmed the single workspace, retained destinations and child sections, deep return paths, session gates, Maps lifecycle guards and draft restoration were already implemented. One Account Save defect remained: successful responses updated the profile heading but left draft form values in place. AccountFragment now clears the saved draft/view snapshot and populates fields from the authoritative returned profile after success. Fields are disabled while saving to prevent new edits being overwritten by the response. Failed saves preserve the draft; tab switches and rotation retain unsaved edits. No other production change was needed during this continuation.

## Problem and final architecture

Previously, WorkspaceChrome inflated a similar shell around each Activity, while tab selection launched another Activity and finished the outgoing screen. Home/session and catalog lifecycle callbacks then refetched data. Similar-looking navigation therefore hid repeated screen reconstruction and state loss.

WorkspaceActivity now owns **one toolbar, one FragmentContainerView and exactly one floating BottomNavigationView**. Top-level content and booking subsections use retained FragmentManager instances with stable tags, add/show/hide, and max lifecycle controls. Only the selected branch is RESUMED; inactive destinations remain STARTED. No tab selection creates Activity history.

| Fragment | Responsibility |
| --- | --- |
| HomeFragment | Shared role-aware greeting, actual counts, contextual actions |
| StationsFragment | Shared list/nearby/Maps workspace |
| BookingWorkspaceFragment | Prosumer Reservations or operator Bookings tabs |
| MyReservationsFragment | Existing owner reservation cards/actions |
| BookingListFragment | Current, Pending or History with independent page/expansion state |
| SearchBookingsFragment | Existing authorized search and retained inputs/results |
| AccountFragment | Prosumer profile form and deactivation |

Prosumer navigation: **Home / Stations / Reservations / History / Account**.
GridOperator navigation: **Home / Stations / Scan / Bookings / Search**.

Prosumer Reservations contains My reservations, Current, Pending and Search. Operator Bookings contains Current, Pending and History. Child tabs never start top-level Activities. Scan opens the existing deep scanner and leaves the caller selected.

## State, refresh and Back

Ordinary tab switches preserve views, scroll, expanded cards, search filters/results, unsaved Account fields and map/list state. WorkspaceState uses the existing ViewModel dependency for in-memory result snapshots and view-hierarchy state across configuration recreation. Saved-state stores selected destination/section, owner identity and Account draft fields. FragmentManager restores existing fragments rather than adding duplicates.

Process death deliberately discards in-memory API results. The server profile is verified before revealing restored content; only visible destinations load as needed. Owner/role mismatch discards the prior workspace. Tokens and QR payloads are not stored in these presentation snapshots.

Data loads on first usable entry, explicit refresh/search/retry, or a relevant invalidation. Create, update, cancel and completion success increment a data-free in-process booking revision. Visible affected data refreshes once; hidden affected views refresh when selected. Tab switches alone do not request profile/count/list data. Failed requests wait for explicit retry; interrupted view-lifetime requests can retry after recreation.

Foreground/deep-flow return still verifies /users/me for security; this is separate from tab switching and does not unconditionally reload data. Rotation reuses an unexpired, verified in-memory session. Time-sensitive owner cutoff controls are re-evaluated locally without rebuilding cards or changing server rules.

Back from non-Home returns to Home; Back from Home backgrounds the task. Deep Back/Done returns to the existing caller. Reservation Summary Done uses CLEAR_TOP/SINGLE_TOP to reuse WorkspaceActivity and its parent destination. Home's New Reservation action selects Reservations before entering the deep flow.

## Session, Maps and deep screens

Login success clears the authentication entry stack and opens WorkspaceActivity. Navigation is built only from a verified profile. Unavailable verification shows a retry gate. Expiry/logout uses the existing session/cache clearing path. Token preference changes, including matching 401 invalidation by the unchanged interceptor, clear the authenticated task. Background verification callbacks cannot grant a stale foreground session.

Stations now hosts a **child SupportMapFragment**, with view lifecycle enabled and getMapAsync. Maps manages its own lifecycle. View-generation checks discard old map callbacks; location work is cancelled on pause and API calls on view destruction. Ordinary tab switches retain the map view, rows and camera. Coordinate, marker, nearby-radius, permission and station-detail business behavior remain unchanged.

Required deep Activities retained:
- LoginActivity and RegisterActivity.
- StationDetailActivity (with existing CatalogActivity base).
- CreateReservationActivity including Review.
- ModifyReservationActivity including Review.
- ReservationSummaryActivity.
- ReservationQrActivity.
- QrScannerActivity.
- QrVerificationResultActivity including Completion.

DeepScreenChrome supplies only toolbar/insets. It has no bottom navigation or separate authentication/profile loader.

## Cleanup and manifest

Removed seven obsolete top-level Activities and WorkspaceChrome. Removed the old shared bottom wrapper. Migrated six Activity layouts to fragment_* layouts and removed the unused Current Bookings Activity layout. Removed six now-unused Activity-specific string labels. ReservationAdapter retains expansion IDs across RecyclerView rebinds/recreation.

The manifest replaces the seven top-level Activity declarations with one non-exported WorkspaceActivity using adjustResize. Login remains the exported launcher; all internal/deep screens remain non-exported. Permissions and network security are unchanged.

No dependency or framework was added. The existing graph resolves Fragment **1.5.4** and lifecycle-viewmodel **2.6.2**. No Navigation Component or Compose.

## Exact files changed

Modified:
- `mobile/SmartSolarMobile/app/src/main/AndroidManifest.xml`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/auth/LoginActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/auth/RegisterActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/CreateReservationActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/ModifyReservationActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/ReservationSummaryActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/QrScannerActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/QrVerificationResultActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/ReservationAdapter.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/ReservationQrActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/stations/StationDetailActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/util/MobileNavigation.java`
- `mobile/SmartSolarMobile/app/src/main/res/values/strings.xml`
- `mobile/SmartSolarMobile/app/src/test/java/com/smartsolar/mobile/MobileNavigationTest.java`

Added (including renamed fragment layouts):
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/common/DeepScreenChrome.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/AccountFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/BookingListFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/BookingWorkspaceFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/HomeFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/MyReservationsFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/SearchBookingsFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/StationsFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/WorkspaceActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/WorkspaceChanges.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/WorkspaceFragment.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/workspace/WorkspaceState.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/util/WorkspaceRefreshPolicy.java`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_workspace.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_account.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_booking_list.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_booking_workspace.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_home.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_my_reservations.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_search_bookings.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/fragment_stations.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/view_deep_screen.xml`
- `mobile/SmartSolarMobile/app/src/test/java/com/smartsolar/mobile/WorkspaceRefreshPolicyTest.java`

Removed (including former names of migrated layouts):
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/account/AccountActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/common/WorkspaceChrome.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/home/HomeActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservation/ReservationDetailsActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/BookingHistoryActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/CurrentBookingsActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/reservations/SearchBookingsActivity.java`
- `mobile/SmartSolarMobile/app/src/main/java/com/smartsolar/mobile/ui/stations/StationDiscoveryActivity.java`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_account.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_booking_history.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_current_bookings.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_home.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_reservation_details.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_search_bookings.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/activity_station_discovery.xml`
- `mobile/SmartSolarMobile/app/src/main/res/layout/view_workspace_shell.xml`

Documentation added:
- `docs/ANDROID-WORKSPACE-ARCHITECTURE.md`
- `docs/ANDROID-WORKSPACE-NAVIGATION-ACCEPTANCE.md`
- `docs/FINAL-ANDROID-WORKSPACE-REFACTOR.md`

## Tests and final validation

Modified `MobileNavigationTest.java`: seven tests for exact role menus, selected/parent mapping, current/pending/search/history subsections, reselection, special Scan behavior, Back and role isolation.

Added `WorkspaceRefreshPolicyTest.java`: three tests for first-load versus repeated selection, mutation invalidation once, and explicit retry/interrupted load.

The prior navigation test class contained three tests; these changes increase the total from 59 to **66 JVM tests**. These are policy/unit tests, not simulated device or Fragment-runtime evidence.

From `mobile/SmartSolarMobile`:

```powershell
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:processReleaseMainManifest
```

Final clean-build exit code **0**, **BUILD SUCCESSFUL in 1 minute 20 seconds**. **51 actionable tasks: 51 executed**. The unit-test and release-main-manifest processing tasks executed successfully after clean. Fresh JUnit XML reports contain **66 tests passed; zero failures/errors/skips**. Fresh lint XML reports contain **zero errors, 78 warnings**. A compilation note remains for the API-compatible legacy Parcelable retrieval used for camera state.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

`git diff --check` passed. Git prints existing line-ending normalization notices for unrelated files; these are not whitespace failures. All 24 layout XML files parse and have no duplicate view IDs within a layout. Source scans confirm only one bottom-navigation XML view and no old WorkspaceChrome/top-level Activity references. Conflict-marker, duplicate-import and obvious current-source secret scans passed without printing values; local secret/config files remain ignored. The scanner was corrected to skip intentionally deleted tracked files before the successful scan.

Backend and web production/test files have no diff. As authorized, the immediately preceding backend baseline is referenced rather than rerun: **202 unit + 77 MongoDB integration tests passed, zero failures**. Web was not redesigned or rerun.

## Device evidence and limitations

No emulator/device navigation, authenticated runtime, rotation, camera, location, Maps tiles or SQLite Inspector check was executed in this pass. Builds and JVM tests do not prove absence of a visible flash or clipping on actual hardware.

Run [the focused acceptance checklist](ANDROID-WORKSPACE-NAVIGATION-ACCEPTANCE.md). It covers both role sequences, stable Activity identity, request counts, subsection/tab state, transaction return/invalidation, logout/401/account switching, rotation/process restoration, keyboard, both system navigation modes, Maps, dark mode and large fonts.

In-memory data snapshots intentionally do not survive process death. Interrupted mutations are not automatically replayed. External changes made by other users become visible through explicit Refresh or existing relevant invalidation; no polling, offline sync or new stale-data timer was invented. The existing Search screen still shows its first result page; search pagination was outside this navigation pass. Five-item fit at extreme font scales and real Fragment/Maps restoration need device acceptance. The earlier historical Maps-key rotation question remains outside this refactor and is not claimed resolved.

See [the concise architecture guide](ANDROID-WORKSPACE-ARCHITECTURE.md) for responsibilities and refresh policy. The older UI polish report describes the prior Activity-per-tab stage; this report supersedes that navigation design.

READY FOR ANDROID WORKSPACE MANUAL ACCEPTANCE
