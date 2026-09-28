# Premium UI/UX manual acceptance

Use the final Web build and Android debug APK from this pass. Record browser/device version, viewport, font scale, theme, timezone and build revision. Use disposable real accounts and reservations. Keep passwords, JWTs, QR payloads and Maps keys out of screenshots and logs.

Automated evidence is limited to the [refresh report](FINAL-PREMIUM-UI-UX-REFRESH.md). All boxes below require manual execution. Browser fixture tests are not live API acceptance.

## Setup and shared identity

- [ ] Start MongoDB and the Development API using the root README, start the Web client, and install the debug APK.
- [ ] Compare the connected-sun logo, forest/emerald/solar colors, status treatments and terminology on both platforms.
- [ ] Confirm app launcher icon, Android login/header and Web login/sidebar/favicon show the new identity.
- [ ] Confirm screenshots contain actual authorized data; keep empty states when no data exists.

## Web login and shell

- [ ] Open Login at 1920, 1440, 1024, 768 and 390 CSS pixels. Check logo, hero, form, validation, keyboard order and the Show/Hide password control. Confirm no page-level horizontal scroll.
- [ ] Sign in as active Backoffice, then separately as GridOperator. Verify exactly one active navigation item, correct role links, grouped navigation, current account and sign-out confirmation.
- [ ] At narrow widths, open/close Menu and select a route. Verify controls remain reachable.
- [ ] Set browser zoom to 200%. Check sidebar/menu, page headings, tables, forms, dialog content and toast dismissal. Tables may scroll within their own region.
- [ ] Enable OS reduced motion. Confirm entrance/spinner animation stops without hiding loading feedback.
- [ ] Test keyboard navigation, visible focus, skip link and screen-reader labels. Web uses its deliberate light theme; no Web dark-mode toggle is claimed.

## Backoffice

- [ ] Home: compare all displayed counts with actual account/station records. Test zero values, loading and API failure. No invented counts should appear.
- [ ] User Management: open Create staff user. Verify focus starts inside, Tab/Shift+Tab wrap within the dialog, background cannot be clicked, Escape closes, and focus returns to Create staff user.
- [ ] Submit invalid data and an API validation failure; errors remain inside. During saving, inputs and dismissal are disabled. Successful creation produces one notification and refreshes actual records.
- [ ] Register Prosumer in its dialog. Confirm PendingActivation behavior is unchanged. Cancel and reopen; sensitive creation fields must be cleared.
- [ ] Edit a Prosumer. NIC/role/status remain fixed; Save uses existing API behavior and failure leaves fields available for correction.
- [ ] Open Pending Activations, activate a disposable account and inspect real status changes.
- [ ] Deactivate: open confirmation, choose Go back and verify no mutation. Repeat and confirm, then verify server state and a single success notification.
- [ ] Stations: search/filter, show inactive, open details. Create/edit in the dialog; validate all fields and the unchanged seven-day recurring UTC schedule.
- [ ] Check long station names/addresses, empty search results, network error and explicit retry. Confirm protected deactivation still reports the authoritative server error inside its confirmation.

## GridOperator Web

- [ ] Home/dashboard: verify real pending/approved-future counts, loading, zero and failure states.
- [ ] Open Stations and a station detail. Create/edit slots in dialogs. Confirm browser-local inputs still convert to the same UTC instant, with concurrency errors shown inside the dialog.
- [ ] Change availability and deactivate a disposable slot. Confirm active reservation protection and historical records remain authoritative.
- [ ] Inspect Manage Reservations, Pending Queue, Current, Dashboard, History and Search. Verify table alignment, status chips, compact references, local schedule labels, paging, filters, empty/error/loading states and retry.
- [ ] Open reservation details; verify full references remain available when needed, restriction text remains accurate, and existing approve/reject/cancel dialogs fit the viewport.
- [ ] Approve, reject with remarks, and cancel eligible disposable reservations. Confirm one success notification, authoritative details and unchanged cutoff rules.
- [ ] Visit disallowed routes as Backoffice/Prosumer. Verify existing role rejection and a usable return action.

## Android Prosumer

- [ ] Login and registration: inspect logo, password visibility, keyboard, input errors, loading and PendingActivation guidance. Confirm real active-account login works.
- [ ] Home: verify greeting, real counts and role actions; load errors must remain readable.
- [ ] Home → Stations → Reservations → History → Account → Home. The same workspace and one floating bar remain; active selection is correct.
- [ ] Tap the current destination repeatedly. No duplicate fragment or navigation-triggered data reload.
- [ ] Stations: inspect real cards, Nearby/All controls, coarse-location allow/deny, distance and station detail entry.
- [ ] With Maps configured, pan/zoom, hide/show the map, switch tabs and rotate. Camera/list state must remain useful; markers use stored coordinates. Refresh while hidden, then show; no zero-size camera crash or missing current markers.
- [ ] Without a Maps key or network, verify the station list and helpful fallback messages remain usable.
- [ ] Reservations: switch My reservations / Current / Pending / Search, expand cards, scroll and return. Selected subsection, filters and results remain.
- [ ] Create/review/confirm a reservation; inspect local schedule and authoritative summary. Modify/cancel an eligible record using existing controls and confirmation.
- [ ] History and Search: inspect empty, populated, error and retry states, local times and status contrast.
- [ ] Account: edit fields without saving, switch tabs and rotate; preserve the draft. Save; show authoritative returned values and one Snackbar. Failed Save retains the draft.
- [ ] Deactivate a disposable account with confirmation. Logout, expiry and matching 401 must clear workspace/profile access as before.
- [ ] Approved QR: inspect summary and clean white QR presentation in both themes; no raw payload appears. Ineligible statuses still cannot issue QR.

## Android GridOperator

- [ ] Verify Home / Stations / Scan / Bookings / Search only, real counts and the scanner CTA.
- [ ] Bookings Current / Pending / History and Search retain state across tab changes.
- [ ] Scan from two different destinations and press Back; return to the same caller without duplicating the workspace.
- [ ] Grant/deny camera permission and recover through settings. Check scanner instructions, progress and error/retry overlays.
- [ ] Verify an eligible real QR. Inspect local time, station, reservation and energy details; complete only after explicit confirmation.
- [ ] Test invalid, too-early, expired, cancelled, rejected and completed references. Error/success states stay clear; replay and concurrent completion rules remain server-controlled.

## Android device and accessibility matrix

- [ ] Test API 26+ and a current supported device, portrait/landscape, light/dark, large font and display scaling.
- [ ] Rotate during loads, background/foreground, and restore after process recreation. Session verification gates restored content; no old account content crosses a role/account switch.
- [ ] Test gesture and three-button navigation. The capsule stays above system navigation, and the final row/action remains reachable.
- [ ] Open keyboards on Account/Search and deep forms. Verify layout resize, visible inputs, bar visibility and restored selection.
- [ ] TalkBack: inspect headings, labels, image descriptions, focus, error announcements and 48dp controls.
- [ ] Disable system animations. Material feedback remains usable without requiring motion.
- [ ] Inspect SQLite: only the current local profile is cached; passwords, password hashes, JWTs and QR secrets are absent. Logout/401 clearing is unchanged.
- [ ] Review launcher icon in normal and themed-icon modes. Review station cards, QR quiet zone and camera overlays on actual hardware.

Continue the existing [functional checklist](FINAL-MANUAL-ACCEPTANCE-CHECKLIST.md) and [workspace checklist](ANDROID-WORKSPACE-NAVIGATION-ACCEPTANCE.md). No Android device, camera, Maps runtime, SQLite Inspector, hosted CI or deployment acceptance is claimed here.
