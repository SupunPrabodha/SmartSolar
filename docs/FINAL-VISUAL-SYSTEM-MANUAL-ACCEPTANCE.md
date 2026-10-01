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

## Enterprise contrast refinement: priority Android checks

These checks apply to the follow-up Android contrast pass and remain unexecuted.

- [ ] **Account sheet:** all four action labels and icons are immediately readable. Normal rows have light/tonal backgrounds; Sign out has a warm danger background, red icon/text and separating divider. Check enabled, pressed and disabled appearance in both themes.
- [ ] Confirm My Profile, Account security, Refresh profile and Sign out call the same actions as before. Verify cancel/confirm sign-out behavior, Back and dismissal.
- [ ] Verify rounded sheet corners, distinct modal surface, spacing and landscape scrolling. Increase system text size; keep all labels and actions reachable above system navigation.
- [ ] Compare canvas, standard cards, grouped panels, strong surfaces and navigation side-by-side. They should be visibly distinct without heavy shadows.
- [ ] Home: forest hero, solar/emerald metric accents, stronger Recent activity and lighter quick-action rows inside a mint container.
- [ ] Bookings: separate canvas/card/header/inner-tile tones, unchanged semantic status strip and vector chevron rotation.
- [ ] Stations: distinct segments/map wrapper/station cards/metrics. Verify the existing marker, permission and location behavior.
- [ ] Search: darker filter group, lighter fields, stronger result summary, readable Search/Clear Filters states.
- [ ] Notifications: visible priority/unread text, distinct normal/unread cards, filters and empty state. Read behavior is unchanged.
- [ ] Profile/Account: strong summary hero, normal personal card, grouped security section, separate danger zone and readable incomplete-profile chip.
- [ ] New Reservation: intro, form and rule-information surfaces remain distinct; Review/Confirm actions are readable in every state.
- [ ] Refresh icons: Bookings, History, My Reservations, Summary and Stations use the existing refresh vector, at least 48dp targets and meaningful spoken descriptions. Refresh profile stays an icon plus text row.
- [ ] Floating navigation: labels/icons remain readable on selected and unselected surfaces, including dark pressed states; existing destinations remain unchanged.
- [ ] TalkBack announces action names, not just “button”. Verify icon descriptions for profile, bell, refresh, Back, camera and QR actions.
- [ ] Repeat on a physical device in light/dark themes, large fonts, portrait/landscape, with keyboard and gesture navigation. Compare pressed/disabled states and contrast against the calculated resource pairs in the report.

## Enterprise authentication experience

Focused follow-up status: **automated checks passed; manual acceptance required.** The approved minimal AuthRepository classification is now implemented: 401/403 remain generic, 429 is rate limiting, 5xx is service unavailable, and the existing IO failure maps to offline. No session/auth rules changed. See the latest premium-authentication report section.

### Web Login

- [ ] Sign in as active Backoffice and GridOperator; confirm existing workspace redirects. Check rejected mobile-only roles retain existing access policy.
- [ ] Try wrong NIC/password and rejected accounts. Expect one toast titled “Sign-in failed” with “Check your NIC and password and try again.” No Reference, trace, correlation ID or raw response is visible.
- [ ] Confirm the toast is announced once; dismiss it and retry. Keyboard focus remains usable after rejection.
- [ ] Toggle Show/Hide password, use autofill and submit with Enter. Check visible labels, focus rings and the quiet Forgot password link.
- [ ] Delay login: the button stays in place, displays Signing in, disables controls and prevents duplicate submission.
- [ ] Test network outage, HTTP 503 and 429: operational feedback stays distinct from credential rejection. Test expired-session restoration and retry.
- [ ] Inspect 1920 / 1440 / 1024 / 768 / 390 widths, 200% zoom, keyboard only and reduced motion. Compact branding remains visible without horizontal scrolling.

### Android Login and registration

- [ ] Sign in using active Prosumer and GridOperator accounts. Confirm unchanged destinations/session behavior and generic feedback for rejected credentials.
- [ ] Test offline login; expect “No connection. Check your network and try again.”
- [ ] Test HTTP 503 and 429: expect “Smart Solar is temporarily unavailable. Try again shortly.” and “Too many sign-in attempts. Wait a moment and try again.” respectively. Neither should imply wrong credentials or display raw response text.
- [ ] Check separate missing-NIC/password field messages, password reveal/autofill, IME Next/Done, duplicate-submit prevention and the stationary button/progress.
- [ ] Verify Forgot password opens the unchanged recovery flow and outlined Create Prosumer account opens registration.
- [ ] Successfully self-register a disposable Prosumer. Expect Login with “Account created. Your account is pending activation. You can sign in after Backoffice approval.”
- [ ] Confirm no automatic login, no password prefill and Back does not reopen the completed registration form. Reopening/rotating Login must not repeat consumed success feedback.
- [ ] Fail registration with invalid/duplicate data or network outage; remain on the registration form with existing validation.
- [ ] Inspect default/large text, portrait/landscape, keyboard/insets, light/dark themes, TalkBack labels/order and 48dp targets.

## Station details final refinement

- [ ] Open a real station from the existing discovery flow. Check actual name, long address, distance or existing distance fallback, capacity in kWh and battery-slot count.
- [ ] Verify Back and labelled Refresh station toolbar actions, including native ripple, target size and unchanged reload behavior.
- [ ] Compare grouped next-seven-day hours with authoritative schedule data using device-local time, including timezone/date-boundary and 24:00 cases. Missing records must not invent Closed days.
- [ ] Compare each active slot's labelled start/end and available/total counts with API data. No inferred “busy” categories or new booking actions.
- [ ] Delay initial station and slot responses: placeholders end on success/failure; loaded station content remains above slot loading.
- [ ] Test absent hours, no active slots, zero availability, inactive station, network failure, retry and session expiry/401.
- [ ] Inspect long names/addresses, wrapped dates/metrics, large fonts, landscape, dark/light modes, TalkBack order and system/gesture insets.

No browser, emulator, physical-device or accessibility runtime execution is claimed for this follow-up.

## Premium authentication follow-up

Automated Web and Android gates pass. These additional visual/device checks remain manual. Station Details was not modified by this follow-up.

- [ ] Web: original solar-network SVG is subtle, non-interactive and ignored by assistive technology. Forest branding, tonal right panel and bounded form remain composed at 1920 / 1440 / 1024 / 768 / 390 and 200% zoom.
- [ ] Web: check field hover/focus/disabled states, password reveal keyboard focus, sole primary Sign in CTA and quiet recovery link. Repeat generic errors, rate limit and offline/service recovery.
- [ ] Android, keyboard closed: on a tall/normal phone with enough room, hero + form + secondary footer form one vertically centered composition with balanced space above/below.
- [ ] Android, short phone/landscape/large fonts: scroll from hero through footer without clipping; no fixed spacer creates unreachable content.
- [ ] Android, keyboard open: focus NIC then Password, use Next/Done, scroll to Sign in and confirm all actions remain reachable. Hide the keyboard and confirm centering returns when there is room.
- [ ] Android: check the original vector motif, bordered mint form, focused field stroke and reveal icon in light/dark modes. TalkBack must skip decorative artwork and announce fields/actions.
- [ ] Android: test 401/403, 429, 500/503 and network failure through a controlled test API/proxy; compare generic rejection, throttling, unavailable and offline messages. Do not change production security settings to induce failures.
- [ ] Recheck successful registration -> Login -> pending-activation message, empty password, no auto-login and sane Back behavior. Failed registration stays on the form.

No browser viewport, emulator, physical-device, keyboard, TalkBack or rendered contrast result is inferred from automated tests.
