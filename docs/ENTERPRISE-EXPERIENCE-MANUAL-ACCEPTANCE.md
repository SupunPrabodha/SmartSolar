# Enterprise experience manual acceptance

Status: **NOT EXECUTED**. Automated results are recorded in [the hardening report](FINAL-ENTERPRISE-EXPERIENCE-HARDENING-REPORT.md). Record date, tester, client/device version and result beside each scenario when actually performed. Use disposable accounts/data. Do not capture passwords, JWTs, reset/QR bearer tokens, SMTP credentials or private images in evidence.

## Setup and email

1. From the repository root, run `docker compose up -d --wait`; start the Development API with `dotnet run --project .\src\SmartSolar.Api\SmartSolar.Api.csproj --launch-profile https`. Confirm host health and the configured Android host URL.
2. In `web/smart-solar-web`, run `npm.cmd run dev`. Install the debug Android APK using Android Studio or `.\gradlew.bat :app:installDebug` from the Android directory.
3. Keep existing working private `VerificationEmail:Host`, `Port`, `Username`, `Password`, `From` and `VerificationPageUrl` settings. The existing sender uses authenticated STARTTLS, normally port 587; implicit TLS port 465 is not supported. Do not paste Markdown links into configuration values.
4. Optionally set the reset page explicitly, from the repository root:
   ```powershell
   dotnet user-secrets set "VerificationEmail:ResetPageUrl" "http://localhost:5173/reset-password" --project .\src\SmartSolar.Api
   ```
   Without this setting, the sender uses the origin of VerificationPageUrl plus `/reset-password`. Restart the API after configuration changes. A link to localhost is usable in the host browser only. For remote recipients/physical phones, use a reachable HTTPS Web origin with SPA route fallback; do not weaken transport validation.
5. Use accessible disposable mailboxes and Active accounts for all three roles. Approval/email verification must already be complete for a Prosumer. Record browser/device timezone. No real email delivery was executed by the automated validation.

## Forgot and reset password

- [ ] Web Login and Android Login offer Forgot Password. Submit known NIC, known email, unknown identity and an inactive account. Validly shaped requests show the same generic message; no account lookup result is exposed.
- [ ] Verify actual reset email arrival, sender, readable content and reachable Web link. It contains a fragment token; the Web page removes the fragment from the address bar after reading it. Passwords are never emailed.
- [ ] Confirm a valid link changes the password. Old password fails, new password works, and all prior JWTs fail an authenticated API request. Reset does not automatically sign in.
- [ ] Verify invalid, expired (20 minutes), used and replaced links fail safely. Wait at least one minute before requesting a replacement; the replacement invalidates the previous link.
- [ ] Submit the same valid token concurrently twice. Exactly one request succeeds; no second password change occurs. Use private test tooling without logging request bodies.
- [ ] Test password length 7/8/100/101, blank input, mismatched confirmation, show/hide controls and double submission.
- [ ] Confirm a High security inbox item, PasswordReset audit and acknowledgement email. Simulated acknowledgement delivery failure must not undo a committed password change.
- [ ] Simulate reset-mail failure using a disposable test configuration: generic acknowledgement remains, no password changes, and retry after fixing SMTP/one-minute cooldown succeeds.
- [ ] Verify authentication rate limiting produces a readable retry response. The in-memory recovery queue is not durable across an API restart; retry a lost request after restart.

## Authenticated change password

- [ ] Web My Profile and Android My Profile expose current/new/confirmation fields. Wrong current password and invalid new password cause no mutation.
- [ ] Successfully change a password as each permitted client role. Current client signs out; old password and an old JWT fail; new password works.
- [ ] Confirm reset state is consumed, PasswordChanged audit, High notification and acknowledgement email.
- [ ] Force a competing profile/security change: a stale conditional write must not silently overwrite it.
- [ ] On Android, inspect SQLite after logout: no cached profile, password, token, avatar or reset data remains.

## Profile, avatar and completion

- [ ] NIC, role and account state are read-only. Edit allowed full name/email/phone fields and check existing duplicate/validation/email-change rules still apply.
- [ ] Upload JPEG, PNG and still WebP; verify immediate avatar display, refresh/relogin persistence and initials fallback after removal.
- [ ] Reject empty, corrupt, animated, oversized (>2,000,000 bytes), excessive dimension (>4096 per side) and excessive pixel-count (>12 million) uploads. A renamed non-image must not bypass decoding.
- [ ] Confirm stored output is normalized JPEG at most 512 pixels per side, without source metadata. Normal profile JSON contains metadata only; authenticated image endpoint serves the image.
- [ ] Request another user's image/profile through unsupported paths or an expired session; no cross-user access.
- [ ] Incomplete account receives a dashboard completion prompt. Complete Profile opens the right screen; Skip hides it for this account/session. Relogin prompts again if still incomplete.
- [ ] Valid profile plus avatar followed by Save marks completion on the server; later login no longer prompts. Remove avatar and recheck incomplete state.
- [ ] Switch accounts: no previous avatar, prompt dismissal, profile, notification or feedback leaks.
- [ ] As GridOperator, change profile/avatar on Web and refresh Android; reverse the direction. Check server data matches on both clients.
- [ ] Exercise Android Photo Picker cancellation, activity recreation, background/foreground and denied/unavailable provider. No new media permission should be requested.

## Persistent notifications and activity

- [ ] Register a Prosumer: Backoffice receives an approval-related item.
- [ ] Activate/reactivate a Prosumer: owner receives the account item.
- [ ] Create/modify a reservation: owner and appropriate GridOperators receive items. Approve/reject/cancel: owner receives the result. Complete: owner and operators receive the completion item.
- [ ] Password changed/reset appears as High security feedback. Check High/Medium/Low filtering, category, timestamp and unread badge.
- [ ] Mark one read and then all read. Concurrent arrival of another notification is preserved. Reload and cross-platform refresh reflect server state.
- [ ] Verify ownership by trying another user's notification ID: no other inbox changes or data leak.
- [ ] Restart the API worker with retained pending events; delivery retries without duplicates within the retained event/notification window.
- [ ] Create more than 100 disposable events: inbox/history keep the newest 100. This is bounded retention, not an indefinite archive.
- [ ] Routine station edits do not flood inboxes. Dashboard shows the latest real 3-5 relevant items or an honest empty/error state.
- [ ] Notification actions navigate only to authorized current data. Deleted/unavailable records and lost authorization show a useful state.

## Feedback and unavailable service

- [ ] Web Success/Info/Warning/Error styling, icons, dismiss controls and loading-to-result update work. Repeated identical feedback is deduplicated; at most three items display.
- [ ] Navigation does not create a second feedback host. Persistent inbox items remain separate from transient messages.
- [ ] Android uses the shared Snackbar policy; QR completion/profile actions do not stack Toast + Snackbar + dialog for one result.
- [ ] Stop the API while each client is open. Expect connection/service-unavailable guidance and Retry, without endless polling or treating an outage as invalid credentials.
- [ ] Restart the API and Retry. Separately test validation 400, forbidden 403, stale/expired-session 401 and conflict 409.
- [ ] Unexpected server failures expose only safe text and a short reference. Correlate X-Correlation-ID with ProblemDetails correlationId, traceId and scoped server logs without recording secrets.
- [ ] A cached Android profile alone does not allow offline authenticated access. Background/foreground verifies the API session.

## Audit and authorization

- [ ] Inspect user registration/approval/activation/reactivation/deactivation/profile completion/avatar/password histories.
- [ ] Inspect station create/edit/activation/deactivation and slot create/edit/availability/deactivation histories.
- [ ] Inspect reservation create/modify/approve/reject/cancel, QR issuance/verification and completion histories.
- [ ] Check actor, entity context, UTC timestamp, event and correlation reference; no credentials, raw tokens or image bytes appear.
- [ ] Exercise history filtering by action/actor. History is bounded to 100 events.
- [ ] Backoffice cannot read operational reservation histories; Prosumer cannot read another owner's history; role/account-state checks remain authoritative.

## Search and CSV

- [ ] Ctrl+K and Cmd+K open Web search. Two-character minimum, debounce, arrow navigation, Enter and Escape work with focus restoration.
- [ ] Search by identifier/name prefix (case-sensitive). Results are capped at five per allowed kind. Backoffice sees users/stations; GridOperator stations/reservations; Prosumer active stations/own reservations only.
- [ ] Selecting a result opens the matching filtered list/detail. A stale result never bypasses target endpoint authorization.
- [ ] Export Users, Stations and relevant reservation lists using active filters. Compare row set, UTC values and explicit headers against API data; narrow filters for >1,000 rows.
- [ ] Verify commas, quotes, line breaks, Unicode and spreadsheet-formula prefixes (including leading whitespace) are safely represented. No passwords, hashes, JWT/reset/QR data, image bytes or audit internals are exported.
- [ ] Unauthorized export fails. Backoffice cannot export operational reservations; Prosumer data scope remains own only.
- [ ] Test loading/failure/retry/export success feedback without duplicate messages.

## Accessibility and regression checks

- [ ] Web desktop/narrow/mobile widths and 200% zoom; keyboard-only flow, focus trap/restoration, screen-reader announcements, contrast and reduced motion.
- [ ] Android portrait/landscape, rotation during requests, light/dark themes, large fonts, TalkBack, system bars and physical device.
- [ ] Verify original five Workspace tabs per role; profile and notification screens stay focused deep screens, with correct Back behavior.
- [ ] Existing approval/verification, station/slot protection, reservation cutoffs, QR verification/completion and optimistic concurrency remain unchanged.
- [ ] Verify SQLite Inspector shows only the current local profile and no avatar/password/JWT/reset/QR secrets. Logout, expiry and matching 401 clear it.
- [ ] Recheck release cleartext disabled, debug host exception limited, camera/location recovery and Maps configuration locally.

Hosted CI, IIS deployment, real browser/device interaction, Maps/camera execution, SQLite Inspector and SMTP delivery are not certified by unit/integration/build success.
