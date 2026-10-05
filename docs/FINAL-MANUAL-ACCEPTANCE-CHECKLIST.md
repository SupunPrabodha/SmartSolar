# Final manual acceptance checklist

**Manual execution required.** Build/JVM/unit/integration results do not certify browser, emulator, physical device, email delivery, camera, Maps, SQLite Inspector, IIS or hosted CI behavior. Record tester/date, revision, runtime/browser/device version, viewport, timezone, theme and font/zoom scale for each executed check.

Use disposable accounts and reservations. Never record passwords, JWTs, raw QR/reset/verification payloads, Maps keys or private profile photos. Capture unique redacted screenshots only after execution; see [screenshot checklist](FINAL-UI-SCREENSHOT-CHECKLIST.md).

## Setup and transport

- [ ] Follow [onboarding](TEAM-ONBOARDING.md): local Mongo healthy, one Development API process, Web running and debug APK installed.
- [ ] Connect/authorize the emulator or USB device and run scripts/setup-adb-reverse.cmd. Confirm host and device http://localhost:5000/health, then login without an HTTP redirect.
- [ ] Record browser/device timezone. Compare one local input and displayed date across a UTC midnight boundary with the actual API UTC value.
- [ ] Confirm the historically exposed Maps key has been revoked/rotated by its owner and the private replacement restricted before relying on Maps. A clean source scan does not certify this cloud action.

## Authentication and email lifecycle

- [ ] Web accepts Active Backoffice/GridOperator; Android accepts Active Prosumer/GridOperator. Direct unauthorized routes/actions are denied by the API.
- [ ] Register a Prosumer using a valid unique NIC and email. PendingActivation prevents login; success returns to Login without automatic login/password prefill or a repeated consumed message after rotation.
- [ ] Backoffice approves/sends email. Account remains pending until explicit confirmation on /verify-email. Opening the link alone must not activate; confirmation succeeds once and returns no JWT.
- [ ] Test resend after one minute, old-link rejection, 24-hour expiry, deactivation before confirmation, malformed link and SMTP failure/retry. Automated captured-email tests are not real delivery evidence.
- [ ] Change the Prosumer email: old approval/link/session becomes invalid and new approval/verification is required. Name/phone-only changes preserve status; edits do not reactivate Deactivated accounts.
- [ ] Backoffice reactivation requires the proper verification flow; GridOperator cannot approve users. Staff activation remains available without Prosumer verification.
- [ ] Wrong NIC/password and rejected accounts show generic sign-in feedback without raw response, trace or correlation text. Offline, 429 and 500/503 remain distinct: connection, throttling and service-unavailable messages.
- [ ] Password reveal/autofill, Enter/IME Next/Done, separate missing-field errors and duplicate-submit prevention work. Signing-in progress stays on the action.
- [ ] Restore/reload/background/foreground, expiry, matching 401, logout and account/role switching show no old profile or workspace. Network failure hides unverified content and offers Retry; SQLite alone never grants access.

## Password recovery and profile

- [ ] Forgotten NIC/email requests give the same generic response for existing and unknown accounts. Rate-limit feedback is readable. Active disposable account receives the reset email; unavailable SMTP does not disclose account existence.
- [ ] Reset at /reset-password using the fragment token; test invalid, expired (20 minutes), replaced and already-used token. Concurrent consumption succeeds once. Successful reset invalidates previous JWT sessions.
- [ ] Change password with current password, enforce 8–100 characters, reject incorrect current password and require fresh login after success. Never expose password/token values in logs.
- [ ] Profile completion prompt supports Skip without looping. Confirm displayed completion state comes from the server; save/photo changes behave consistently.
- [ ] Upload JPEG/PNG/still WebP under 2 MB; verify normalized own avatar, replacement and removal. Reject unsupported/oversized/over-dimension/animated files. Cancel/failure preserves usable UI.
- [ ] Test personal fields, avatar loading/error, name/initials fallback, security history and account switching without stale photos. NIC/role/status are not editable through profile controls.
- [ ] Backoffice profile edits versus concurrent deactivation produce a stale-write conflict instead of overwriting status.

## Backoffice Web

- [ ] Home, User Management, Pending Activations and Microgrid Stations appear; reservation workspace links/direct URLs are denied.
- [ ] Create Backoffice/GridOperator staff; register/manage a Prosumer; search/filter users and inspect history. Duplicate NIC/email and invalid input errors remain inside the form.
- [ ] Create/edit/deactivate stations with name, address, kWh capacity, total battery slots and seven-day recurring UTC operating schedule.
- [ ] Manual latitude/longitude, click/drag map picker, selected preview and full Google coordinate-link helper agree. Editing initializes stored coordinates; invalid input preserves the last valid pin. Short URLs give guidance rather than silently resolving.
- [ ] Persist GPS changes, reload and compare with Android markers. Map tile failure leaves manual entry usable; attribution stays visible.
- [ ] Pending/Approved reservations block station deactivation and protected slot mutations; Rejected/Cancelled/Completed do not. Other validation and exact timestamp conflicts still apply.
- [ ] Reducing station capacity below active allocated energy fails; the exact valid boundary succeeds. History and original identifiers remain intact.

## GridOperator Web and authoritative reservations

- [ ] Stations allow slot create/edit/availability/deactivation. Check local datetime -> UTC conversion, positive/bounded inventory, inactive parents, overlaps, adjacent endpoints and stale expectedUpdatedAtUtc.
- [ ] Manage Reservations, assisted create, detail/edit/cancel, Pending Queue, Current, History, Search and Dashboard use actual authorized records.
- [ ] Create within seven days; exactly seven days is accepted and beyond rejected. Update/cancel with exactly twelve hours notice works; below fails. Replacement time also satisfies the horizon/notice rules.
- [ ] Test unavailable inventory, capacity excess, same-owner overlap, terminal edits and simultaneous competing allocation. The API decides validity, not local control visibility.
- [ ] Approve eligible Pending, reject with a required remark, and verify updates return Pending for reapproval and invalidate old QR references.
- [ ] Current contains Approved with accepted end > now; Pending is status-only; History includes terminal records and ended Pending/Approved. Pending/history overlap does not change status.
- [ ] Dashboard Pending and strictly-future Approved counts match actual records. Search filters use accepted start dates, local inputs convert to UTC, paging is usable and no stale results survive a failed request.
- [ ] Missing legacy accepted snapshots show repair conflicts, never invented dates. Do not repair enterprise data merely to make a demonstration succeed.
- [ ] Compare REF-/STN-/SLOT- for the same real records on both clients. Copy/search/detail/edit/QR operations preserve actual internal IDs. Invalid and ambiguous references produce readable errors.

## Android Prosumer and workspace

- [ ] Exactly Home / Stations / Reservations / History / Account. Reservations contains My reservations / Current / Pending / Search; no sixth top-level item.
- [ ] Switch/reselect destinations repeatedly: no duplicated activities, unwanted reload loop or tab Back stack. Fields, search filters, scroll position, expanded cards and map camera remain useful.
- [ ] Back from non-Home returns Home; Back from Home backgrounds the task. Focused detail/create/review/edit/summary/QR/profile/notification screens have Back and no bottom bar.
- [ ] Create via available-slot picker, review, confirm and inspect the server summary. Modify/review/cancel eligible disposable records and verify authoritative outcomes.
- [ ] Current/Pending/History/Search, next/previous search paging and own-only scope work. No operator scanner/completion controls are exposed.
- [ ] Approved owner QR is available; other statuses cannot issue. Reissue invalidates the previous reference; bitmap has readable instructions and a clean white quiet zone.
- [ ] Account draft survives tab changes/rotation; Save replaces it with returned server values, failure retains draft. Deactivation requires confirmation and ends the session.
- [ ] Account sheet shows actual identity/role/status and My Profile, Account security, Refresh profile, Sign out. Security opens the section, refresh uses /users/me, cancel-sign-out preserves session and confirm clears it.
- [ ] Rotation/process recreation/restoration revalidates identity as required; no old token/QR is restored through UI saved state.

## Android GridOperator and QR completion

- [ ] Exactly Home / Stations / Scan / Bookings / Search; Bookings contains Current / Pending / History. No owner create/modify/cancel or QR issuance controls.
- [ ] Scan from Home and another destination; Back returns to the caller and retains the previous selected bottom destination.
- [ ] Grant/deny camera permission and recover through settings. Scan framing, progress, invalid format and Retry remain usable.
- [ ] Server verification displays actual owner/station/slot/energy/local accepted schedule, then requires explicit completion confirmation.
- [ ] Transfer requires accepted start <= server now < accepted end and Active correctly linked owner/station/slot. Test too-early, exact end, cancelled/rejected/completed, rotated/unknown/malformed and inactive-related records.
- [ ] Completion revalidates state, records operator/time and succeeds once. Replay, double tap and simultaneous requests cannot create a second transition. Completed inventory remains consumed and history remains intact.

## Stations, permissions and SQLite

- [ ] Both mobile roles see real active station cards and server-coordinate Maps markers. Marker/list selection opens the correct detail.
- [ ] Near me requests coarse location only on demand; results within 25 km are nearest first. Denial, disabled location, timeout, missing/invalid Maps key and offline tiles leave a useful all-stations fallback.
- [ ] Hide/show map, refresh while hidden, tab switching, rotation and pending callbacks cause no zero-size camera crash or stale markers. Google attribution/controls remain visible.
- [ ] Station detail shows real long name/address, distance/fallback, kWh capacity, battery-slot count, active slots and grouped dated device-local operating intervals. Check UTC date rollover and 24:00; missing schedule is unconfigured, not invented Closed days.
- [ ] Initial/partial loads, absent hours, no slots, zero availability, inactive record, network failure and Retry remain distinct.
- [ ] Follow [Database Inspector](../mobile/SmartSolarMobile/README.md#verify-sqlite): local_user contains one verified profile, no password/hash/JWT/QR/avatar/location columns. Logout/expiry/matching 401/role rejection/account switching clear stale rows.
- [ ] Verify Android backup exclusion and offline denial; Mongo/API remains enterprise truth.

## Notifications, audit, search and exports

- [ ] Web and Android bell badges reflect real unread counts, including zero/one/many/99+ display. Filtering retains correct total count; read-one/read-all and Open preserve ownership and destinations.
- [ ] Registration/approval, lifecycle actions, completion and password changes produce the expected scoped notifications/history. High/Medium/Low and unread have text plus color; empty/error/retry is distinct.
- [ ] History is bounded (100 retained entries), not an immutable archive. A worker restart must not be described as guaranteed durable delivery.
- [ ] Web Ctrl/Cmd+K global search has usable keyboard selection, role scope, empty/error states and bounded results. Cross-role private records remain hidden.
- [ ] CSV respects current supported filters/role, UTC dates, safe columns and formula escaping. More than 1,000 results is rejected with guidance; no secret fields appear.

## Responsive, loading and accessibility matrix

- [ ] Web widths 1920 / 1440 / 1024 / 768 / 390 and 200% zoom: no page overflow, usable internal table scroll, reachable forms, one active navigation link, browser Back/Forward/direct reload.
- [ ] Keyboard-only, skip link, focus rings, labelled actions, error announcements and screen reader. Account disclosure Escape restores focus. Dialog Tab/Shift+Tab containment, safe dismissal, busy lock and trigger-focus restoration work.
- [ ] Reduced motion makes boot/action/skeleton motion static without hiding progress. Loading labels remain meaningful; placeholders end on success, empty and failure without fake metrics.
- [ ] Save/Search/Change password/Export/review actions keep local progress, prevent duplicate submission and recover on failure. Toast dismiss/read time is usable; no repeated announcements.
- [ ] Android API 26 and current physical device, small/normal screens, portrait/landscape, light/dark, large fonts/display scale, TalkBack, keyboard, gesture/three-button navigation.
- [ ] Login overlay form stays inside the solar image surface with compact spacing; scroll reaches every field/action/footer on small screens and with keyboard open.
- [ ] Account sheet scroll/dismissal, normal/pressed/disabled action contrast, destructive sign-out separation, floating navigation labels, notification chips and profile camera overlay remain readable.
- [ ] Content/actions stay above system/navigation bars; bar behavior while typing and after hiding keyboard is correct. Icons have meaningful action names, decorative art is ignored, Android targets are at least 48dp.
- [ ] Verify actual render contrast, photo/initials, launcher/themed icon, status text and scanner scrims; computed resource checks alone are insufficient.

## Deployment and submission evidence

- [ ] Execute [IIS acceptance](../deployment/iis/README.md) with trusted HTTPS, Production, one worker, no overlapping process and external Mongo/JWT/SMTP configuration.
- [ ] Verify deployed SPA deep routes, CORS, /health, email verification/recovery, Android HTTPS/Maps/camera and production seed/Swagger behavior.
- [ ] Confirm hosted CI for the submission revision, assessor repository access and accessible real video <=5 minutes.
- [ ] Documentation owner prepares unique screenshots, final report, architecture/use-case/DFD/database figures, source excerpts, references and actual challenges/contributions.
- [ ] Review staged files/ignore rules, private key rotation evidence, release signing if required and the eventual source-package contents before creating the final ZIP.

Unchecked items are pending, not failed or claimed successes. Do not manufacture evidence from automated build output.
