# Final UI screenshot and acceptance checklist

Capture only after running the final app against disposable API data. The fixture browser captures described in [the polish report](FINAL-UI-UX-POLISH-REPORT.md) are layout evidence, not these live acceptance screenshots. Every item below remains unchecked until executed.

Record the date, branch/HEAD, client build, role, browser/device, viewport or screen size, timezone, theme and font/zoom setting with each capture. Use fictitious account details. Do not include passwords, JWTs, Maps keys, developer secrets or raw QR payloads. Keep an Approved QR capture private; redact the scannable code before sharing publicly. Do not commit screenshots containing personal data or valid credentials.

## Web demo captures

| Done | Screen | Required evidence |
| --- | --- | --- |
| [ ] | Login | Product branding, labeled fields, readable failure, keyboard focus |
| [ ] | Backoffice Home | Actual account/activation/station metrics and compact actions |
| [ ] | User Management / Pending Activation | Pending account, activation control, account form; one matching sidebar selection |
| [ ] | Stations | Real station names, addresses, capacities and active/inactive status |
| [ ] | Station Detail / Slots | Recurring schedule explicitly UTC; dated slot inputs/displays local; slot state and controls |
| [ ] | GridOperator Home | Actual pending/approved-future counts and role-specific actions |
| [ ] | Manage Reservations | Short references, statuses, local schedule and readable actions |
| [ ] | Pending Queue | Pending filter and only Pending Queue selected |
| [ ] | Current Bookings | Current data, local schedule, readable table |
| [ ] | Operations Dashboard | Actual counts, updated time and useful navigation |
| [ ] | Booking History | Completed/cancelled/rejected entries as allowed by API; local-time guidance |
| [ ] | Search | Filters, local datetime inputs, result table and empty state |
| [ ] | Reservation Detail | Local schedule/cutoff, status, full reference disclosure and permitted actions |

For live Web acceptance:

- [ ] Repeat Home at 1920, 1440, 1024, 768 and 390 CSS pixels.
- [ ] Inspect tables/forms at 390 pixels and real browser 200% zoom; no page-level horizontal overflow, usable internal table scrolling.
- [ ] Verify only one active sidebar link on every route and query change, including direct navigation and browser Back/Forward.
- [ ] Check keyboard-only operation, skip link, focus visibility, labels and error announcements.
- [ ] Check loading, no-results, API outage/retry, session expiry and role-protected direct routes.
- [ ] Compare one local slot/search input with its UTC API value and its local rendered output. Include a date rollover; do not expose authorization headers.
- [ ] Open and use the station detail/slot and user creation/edit controls, not just their collapsed panels.

## Android Prosumer demo captures

| Done | Screen | Required evidence |
| --- | --- | --- |
| [ ] | Login | Shared brand, persistent field labels, readable failure |
| [ ] | Registration | Toolbar Back, no bottom bar; pending-activation confirmation |
| [ ] | Home with floating nav | Live counts; Home / Stations / Reservations / History / Account |
| [ ] | Find Stations + Map | Real list/markers, map attribution, nearby controls, Stations selected |
| [ ] | Station Detail | Local dated operating intervals and actual slots; toolbar Back, no bottom bar |
| [ ] | Account | Verified profile, Save separated from deactivation, Account selected |
| [ ] | My Reservations | Compact energy/status/local schedule, full details expandable, Reservations selected |
| [ ] | Create | Slot picker, energy field and focused form; no bottom bar |
| [ ] | Review | Confirmation summary before mutation; no bottom bar |
| [ ] | Summary | Server-confirmed outcome, compact status and local schedule; no duplicate heading or bottom bar |
| [ ] | Edit / Review changes | Current details, editable request, notice guidance and confirmation |
| [ ] | Current | Live current data, Reservations selected |
| [ ] | Pending | Live pending data, Reservations selected |
| [ ] | History | Actual concluded bookings, History selected |
| [ ] | Search | Filters and real results, Prosumer NIC filter hidden, Reservations selected |
| [ ] | Approved Transaction QR | Local schedule/issued time, readable instructions; no raw payload or bottom bar |

## Android GridOperator demo captures

| Done | Screen | Required evidence |
| --- | --- | --- |
| [ ] | Home with floating nav | Home / Stations / Scan / Bookings / Search, actual counts |
| [ ] | Booking views | Current/Pending/History data, Bookings selected, no owner create/modify/cancel/QR-issuance actions |
| [ ] | Search | Operator scope/filter controls, actual results, Search selected |
| [ ] | QR Scanner | Camera permission state, framing and toolbar Back; no bottom bar |
| [ ] | Verified Reservation | Authoritative details, local schedule/issued time, explicit completion confirmation |
| [ ] | Completed Transfer | Server completion result/operator/local time; no bottom bar or repeat-completion success |

## Floating-navigation device acceptance

Use a small supported emulator and a current target-compatible emulator/device. Source and JVM tests do not certify these checks.

- [ ] Confirm exactly five concise icon/label items for each role.
- [ ] Prosumer never sees Scan; GridOperator never sees Account, Reservations ownership controls or QR issuance.
- [ ] Tap the already-selected destination repeatedly: no Activity copy, reload loop or unexpected Back step.
- [ ] Switch through all destinations repeatedly, then use Back: no uncontrolled history of duplicate Activities. Home remains the normal anchor.
- [ ] From Current/Pending/Search as Prosumer, tap selected Reservations to return to the parent destination; from GridOperator History, tap Bookings.
- [ ] Open Scan from Home and another top-level screen; Back returns to its caller.
- [ ] Confirm no bottom bar on registration, station detail, create/review/edit/summary, QR display, scanner, verification or completion.
- [ ] Scroll to the last row/action: it remains above the bar. Repeat with gesture and three-button system navigation.
- [ ] Open/dismiss the keyboard on Account/Search and deep forms: controls remain reachable, the bar hides during typing and returns correctly.
- [ ] Rotate, background/foreground and restore while verification/API calls are pending. No duplicate launch, wrong role selection or stale content.
- [ ] Sign out, expire/deactivate the session and switch accounts/roles; no old profile or navigation survives.
- [ ] Stop/restart the API: readable retry behavior; no unverified role inferred from stale cache.

## Visual, time and device acceptance

- [ ] Android portrait/landscape, light/dark theme, large font/display scaling and TalkBack; confirm all five nav labels remain usable.
- [ ] Buttons have primary/secondary/danger hierarchy, labels remain legible, and status is not communicated by color alone.
- [ ] Compare reservation start/end/cutoff, Home update, QR issued and completion against device-local time. Include a UTC-to-local midnight rollover and device timezone change.
- [ ] Verify Web local-time display/search conversion without changing the UTC recurring station schedule contract.
- [ ] Camera/location permission grant, denial and recovery; Maps markers and station list remain coherent.
- [ ] QR completed/cancelled/rejected/inactive-reference errors are understandable. Early/expired scans report the shared outside-window condition without guessing a cause.
- [ ] Verify actual completion once, including replay/concurrent rejection, using disposable eligible records.
- [ ] Inspect SQLite: only the current cached profile; no password/hash/JWT/QR secret. Logout/expiry/matching 401/account switching clear stale data.

Use [the functional acceptance checklist](FINAL-MANUAL-ACCEPTANCE-CHECKLIST.md) for the complete business flows. IIS runtime and hosted CI remain separate execution requirements; neither follows from UI screenshots or local builds.
