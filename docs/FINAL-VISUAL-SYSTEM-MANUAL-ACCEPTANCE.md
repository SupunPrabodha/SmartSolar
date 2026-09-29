# Final Visual System Manual Acceptance

Status: **Manual execution required.** Automated results are recorded in [the refinement report](FINAL-VISUAL-SYSTEM-REFINEMENT-REPORT.md). These boxes are not runtime claims.

Use disposable accounts and real test records. Keep passwords, tokens, QR payloads and Maps keys out of screenshots and reports. Record browser/device version, viewport, theme, text size and result for each check.

## Android: shared workspace and account menu

- [ ] Install the debug APK and restore an existing valid session. Expect the branded restoration surface, followed by the existing workspace. Interrupt connectivity and verify the loading indicator ends and Retry appears.
- [ ] Check the warm canvas, mint grouped surfaces and restrained solar accents. In dark mode, verify the header, navigation, cards, fields, initials, chips and skeletons use readable dark-theme colors.
- [ ] Open the top Account options action as Prosumer and GridOperator. Verify initials, actual name, NIC, role and account status. Check TalkBack reading order and labelled actions.
- [ ] My Profile opens the existing profile screen. Account security opens that screen at its security section. Back returns to the same workspace destination.
- [ ] Refresh profile uses existing verification. Test success, network error, expiry and matching 401. No old account content should remain after switching accounts.
- [ ] Sign out opens the existing confirmation. Cancel stays signed in; confirm clears the session as before. Verify the logout icon and destructive text remain understandable without color.
- [ ] Home has no duplicate Refresh profile / Sign out account panel.
- [ ] Prosumer retains Account in bottom navigation. GridOperator retains its existing five destinations, with no sixth item.
- [ ] Check the floating tinted nav, visible selected label, compact header and bell unread badge. Verify bell opens Notifications and remains reachable at large text sizes.

## Android: screens and loading

- [ ] Home: forest hero, real metric values, labelled metric placeholders during loading, recent activity placeholders, mint activity/quick actions and existing primary operations.
- [ ] Stations: Near me / All stations share a segmented surface. Selection matches actual discovery mode, including permission denial/unavailable location. Verify existing location permission and fallback behavior.
- [ ] Stations: inspect map corners and Google attribution/controls; markers, detail links and location behavior must remain unchanged. Cards show actual name, address, capacity, battery slots and distance.
- [ ] Bookings / Reservations / History: selected tabs are clear, cards retain text status plus semantic accents, expand/collapse and actions work, and loading/empty/error states remain distinct.
- [ ] Search: tinted filter group, readable input surfaces, unchanged fields, in-place search label, result-area skeleton, useful count/icon and matching booking cards. Test empty results and error/retry.
- [ ] Notifications: initial skeleton ends on success/empty/failure; filters remain usable after failure/retry. Verify priority, unread text, mark-read/all-read and existing targets.
- [ ] Profile: mint hero, circular photo/initials, camera overlay, solar incomplete chip, personal/security/history sections. Photo and password rules must remain unchanged.
- [ ] Account: real summary/initials/NIC/status, grouped personal fields, outlined profile/security entry, separate warm danger zone. Save and deactivation retain their previous confirmation and session behavior.
- [ ] New Reservation: intro, details card, slot picker/manual selection, helper/rule information and strong Review action. Verify review/back/confirmation and existing validation without altered request data.
- [ ] Scan flow: verify the shared focused header; scan, verification, timing, QR and completion behavior remain unchanged.
- [ ] Exercise slow requests, success, zero results, known API failure, offline, recovery and Retry on every data screen. No persistent skeleton after a completed failure, repeated announcements, false metrics or full-screen mutation loader.
- [ ] During Save, Search, Change password, reservation confirmation and deactivation, verify progress stays local, existing duplicate-submit protection remains effective and controls recover on failure.

## Android: device and accessibility matrix

- [ ] API 26 emulator and a current physical Android device.
- [ ] Light and dark mode; default and large system fonts; portrait and landscape.
- [ ] Keyboard shown/hidden, gesture navigation, system insets, rotation, background/resume and Back.
- [ ] TalkBack names/order, meaningful loading status, decorative placeholders ignored, 48dp or larger actions.
- [ ] Bottom sheet scrolling and dismissal, especially landscape/large text. No actions hidden under navigation bars.
- [ ] Maps attribution, camera/location prompts and SQLite/session regressions from the existing acceptance checklist.

## Web

- [ ] Test Backoffice and GridOperator at widths 1920, 1440, 1024, 768 and 390, plus 200% browser zoom.
- [ ] Reload a protected URL and the login page with session restoration delayed. Expect branded Smart Solar boot; no raw “Loading session...” page. Check successful restoration, anonymous login and expired-session paths.
- [ ] Dashboard labels remain visible while numbers use skeletons; no fake dots/counts. Verify actual data, empty activity, error and Retry transitions.
- [ ] Station/reservation lists, notification rows, recent activity, search results and security history use bounded placeholders while loading. Previously loaded content must not become falsely current after an error.
- [ ] Confirm mint filters, metric/activity surfaces, neutral tables and existing forest sidebar. The overall 60/30/10 balance should remain calm rather than oversaturated.
- [ ] Verify toolbar search / Ctrl+K, bell badge, hover/focus, keyboard account disclosure, Escape focus restoration and profile/security/sign-out links.
- [ ] Profile forms and notifications in empty/populated/high/medium/low/unread states; no change to upload, password or read semantics.
- [ ] In-place Saving/Updating/Searching/Exporting progress, unchanged button disabling and recovery after failure.
- [ ] Keyboard-only access, labels, focus rings, status announcements, responsive tables and narrow-screen menus.
- [ ] Enable reduced motion: boot, action indicator and skeleton shimmer become static; information and progress remain understandable.

## Evidence

Record results and redacted screenshots only after execution. No browser, emulator, physical-device, camera, Maps, SQLite Inspector or hosted CI execution is claimed by this pass.
