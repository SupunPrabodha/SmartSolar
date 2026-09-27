# Android workspace navigation acceptance

All boxes are **manual and unexecuted in this refactor pass**. JVM policy tests do not prove actual Fragment, camera or device behavior.

Use disposable accounts/data and the newly built debug APK. Keep the configured API/Mongo running and use the existing transport setup. Record device/API level, app build, timezone, theme and font scale. Do not record credentials, tokens, QR payloads or Maps keys.

## Prosumer

- [ ] Sign in as Active Prosumer. Exactly Home / Stations / Reservations / History / Account appear.
- [ ] Home → Stations → Reservations → History → Account → Home: the same WorkspaceActivity remains, with no full layout teardown, white flash, toolbar disappearance or bottom-bar reconstruction.
- [ ] Repeatedly tap the selected destination. No duplicate navigation, API reload or scroll reset.
- [ ] Scroll Stations, pan/zoom the map and choose Nearby or All. Switch away/back; list position, results, mode and camera remain.
- [ ] Reservations: switch My reservations / Current / Pending / Search. All remain child content in the same workspace.
- [ ] Expand a reservation and scroll. Switch subsection/tab and return; expansion and position remain.
- [ ] Enter search filters and run Search. Switch away/back; fields and results remain.
- [ ] Enter unsaved Account edits. Switch away/back and rotate; edits remain. Save and confirm fields reflect the returned server values, including any normalization. Fields are disabled during Save and usable afterward. Switch away/back and rotate again; the old draft must not reappear. Repeat with a failed Save; the draft must remain available for correction/retry.
- [ ] Open Create from Reservations, then Back. Return to the same Reservations parent.
- [ ] Open Create from Home; the parent is Reservations. Complete a disposable booking and tap Summary Done; return to the existing workspace, refresh affected data once.
- [ ] Modify/cancel an eligible reservation. Verify the same return path and one invalidation refresh, without changing lifecycle/cutoff rules.
- [ ] Open Station Detail and Back. Stations does not become an empty loading map or reset its list.
- [ ] Open Approved Transaction QR and close it. Return to the same parent; no bottom bar on QR.
- [ ] No operator Scan item or completion controls appear.

## GridOperator

- [ ] Exactly Home / Stations / Scan / Bookings / Search appear.
- [ ] Home → Stations → Bookings → Search → Home reuses the workspace without tab reload flashes.
- [ ] Bookings Current / Pending / History stay within one parent and preserve their list/page/expansion state.
- [ ] Search fields/results survive tab switches.
- [ ] Scan from Home and from another destination. The scanner is a deep Activity; no bottom bar. Back returns to the exact caller and its selected item.
- [ ] Rapidly tap Scan; only one scanner opens.
- [ ] Verify and complete a disposable eligible transfer. Done returns to the existing workspace; affected counts/lists refresh once.
- [ ] Rejection/replay/window/invalid-reference handling remains authoritative.
- [ ] Account, owner create/modify/cancel and QR-issuance controls remain absent.

## Refresh/network observations

Use Android Studio Network Inspector or server request counts without logging request credentials.

- [ ] Initial visible destination loads once after /users/me succeeds.
- [ ] Warm Home → Stations → Reservations/Bookings → Home causes no additional /users/me or data request merely for switching.
- [ ] Secondary tab reselection does not fetch; first entry to an unvisited subsection may fetch.
- [ ] Explicit Refresh/Search/Retry issues the expected request.
- [ ] A successful mutation refreshes visible affected data once; hidden affected data waits until selected.
- [ ] Returning from Station Detail verifies the session but does not refetch the Stations list.
- [ ] API outage shows a useful message and explicit retry; failed data requests do not loop on tab switches.
- [ ] Returning from background performs security verification. Cached content stays gated when profile verification fails.

## Task and session

- [ ] Back from a non-Home destination returns to Home, not a history of previous tabs.
- [ ] Back from Home backgrounds the app.
- [ ] Deep toolbar/system Back returns to the same workspace destination.
- [ ] Logout clears session/profile and task; Back cannot reveal the workspace.
- [ ] Foreground and background token expiry require Login.
- [ ] Deactivate a disposable signed-in account elsewhere; matching 401/profile verification clears workspace access and cached profile.
- [ ] Self-deactivation clears workspace and Login becomes the only accessible session entry.
- [ ] Switch Prosumer accounts and then roles. No old form, search, map/list or navigation state crosses accounts.
- [ ] A delayed response from an older session never clears a newer session through the interceptor.

## Device/lifecycle

- [ ] Rotate on each destination, including non-default child sections. Correct selection, no duplicate fragments, retained completed data and no refresh burst.
- [ ] Rotate during a request; obsolete callbacks do not update a destroyed view. An interrupted load may retry once.
- [ ] Background then recreate the process through developer tooling. The selected destination/subsection restores, but content requires fresh authoritative session verification. In-memory results are allowed to reload.
- [ ] Rotate/recreate with an Account draft; verify saved field restoration and correct account identity.
- [ ] Maps survives rotation, tab switching, background/foreground and view recreation; no destroyed-map callback, marker duplication or crash.
- [ ] Allow/deny location and leave while locating. Cancellation does not leave a permanent loading indicator; retry works.
- [ ] Gesture and three-button navigation: capsule stays above system navigation; final scroll row/action remains accessible.
- [ ] Keyboard on Account/Search hides the bar where required; closing it restores the same selection.
- [ ] Light/dark mode, large fonts, display scaling, TalkBack and landscape: five labels remain usable, controls/focus are accessible, content is not clipped.
- [ ] Permission flows and physical camera scanning work on the intended device.
- [ ] SQLite Inspector confirms the unchanged local-profile-only schema and clearing behavior.

See [architecture](ANDROID-WORKSPACE-ARCHITECTURE.md). Continue to use [functional acceptance](FINAL-MANUAL-ACCEPTANCE-CHECKLIST.md) and [screenshot checklist](FINAL-UI-SCREENSHOT-CHECKLIST.md); this document replaces their older Activity-per-tab navigation expectations.
