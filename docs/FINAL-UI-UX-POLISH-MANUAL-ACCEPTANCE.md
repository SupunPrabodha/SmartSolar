# Final UI/UX polish manual acceptance

**Status: manual execution pending.** Automated build/test evidence is in [the current polish report](FINAL-UI-UX-POLISH-REPORT.md). These checks certify presentation and interaction; existing API/business behavior must remain the same.

Use disposable accounts/data. Do not capture personal profile photos, passwords, JWTs, SMTP credentials, Maps keys or raw QR/reset payloads in screenshots.

## Setup and evidence

- [ ] Start the existing backend/Mongo setup and Web development server using the project README; install the newly built debug APK.
- [ ] Use active Backoffice and GridOperator Web accounts, and active GridOperator and Prosumer Android accounts.
- [ ] Record tester/date, browser/version, viewport, zoom, Android device/API, font scale and theme. Capture redacted evidence only after each check is actually run.
- [ ] Compare the same real records before/after UI actions. No new endpoint, filter meaning, role permission, notification rule or business workflow should appear.

## Web toolbar and Home

- [ ] Toolbar shows the page context, Search/Ctrl+K, a bell and compact account disclosure. The bell has a readable unread badge for zero/one/many/99+ cases, plus an accessible name including the actual unread count.
- [ ] Click the bell: existing Notifications route opens. No duplicated Notifications text button remains.
- [ ] Open the avatar/account disclosure with mouse, Enter and Space. Tab through My Profile, Account security and Sign out. Escape closes the disclosure and returns focus to its trigger.
- [ ] My Profile opens the existing profile route; Account security targets its security card. Sign out still opens the existing confirmation dialog; Cancel preserves the session.
- [ ] Real avatar/initials render clearly; long names do not collide with toolbar controls. Role remains readable inside the account trigger.
- [ ] Ctrl+K and Cmd+K still open search. Arrow keys, Enter, Escape, accessible labels and focus restoration work.
- [ ] Home metrics/recent activity remain real. Quick-action SVG arrows and refresh icons remain decorative while action labels stay readable.

## Web Profile, Users and Notifications

- [ ] Profile has a separate hero/photo area, Personal information, Change password and Audit history surfaces. Desktop uses aligned personal/security columns; narrower widths stack them.
- [ ] Hero shows actual name/NIC, role/status and server completion state. NIC/role/status remain read-only; completion behavior is unchanged.
- [ ] Upload/remove photo, Save profile and Change password use existing validation/handlers. Camera SVG does not replace the upload label. Password visibility/confirmation and logout behavior work.
- [ ] User Management History/Edit/Deactivate actions have consistent icons plus labels; disabled state, confirmation dialogs and action placement remain usable.
- [ ] Export CSV still uses the current filters. Download icon, progress, success/failure and Retry behave as before.
- [ ] Notifications filters are grouped; priority/unread treatment has text as well as color. Mark all/read-one/Open preserve existing behavior and ownership.
- [ ] Check empty notification/audit states, populated histories, long messages and unavailable-service states.
- [ ] Toast and dialog dismiss controls use SVG icons with accessible names. Feedback timing/deduplication/queue behavior remains unchanged.

## Android shared header and Home

- [ ] GridOperator retains **Home / Stations / Scan / Bookings / Search**. Prosumer retains its existing role-specific destinations. No extra bottom tab or Activity-per-tab history appears.
- [ ] All Workspace destinations show the existing shared toolbar with bell action and profile icon. There is **no literal NOTIFICATIONS toolbar action** in place of the bell.
- [ ] Bell badge handles 0/1/many/99+ and TalkBack announces the actual unread count. Bell and account targets are at least 48dp.
- [ ] Home hero and real metrics remain intact. Recent activity and quick actions are grouped in cards. Refresh profile and Sign out are grouped in a quieter Account card.
- [ ] Scan/new reservation and other primary actions keep the same role visibility and navigation behavior.
- [ ] The floating capsule stays outside content and respects gesture/keyboard insets. Labels/icons and selected state remain clear on a small phone and with large text.

## Android Notifications

- [ ] Back/title/unread subtitle, Refresh and Mark all read are in the focused-screen toolbar. Long titles/large fonts do not obscure actions.
- [ ] All/Unread and All/High/Medium/Low are compact, wrapping selection chips with a visible checked state and 48dp touch targets.
- [ ] There are **no four giant identical green filter/action buttons**.
- [ ] Each message appears in a distinct surface with category icon, priority text, message, timestamp and contextual Open/Mark read actions.
- [ ] Unread cards use a subtle tint plus an explicit Unread label. High priority uses a restrained warm chip, not a full red card.
- [ ] Empty filters show the bell/caught-up state. Error/Retry does not conceal unread filters or produce duplicate feedback.
- [ ] Read-one, read-all, priority/unread filtering and deep actions still target the same real records.
- [ ] Check chip focus and announcement after selecting a filter, rotation, background/foreground and Back.

## Android Search and Bookings

- [ ] Search fields form one filter card: Reservation ID, role-appropriate Prosumer NIC, Station and labelled Status. Leading vector icons and outlined fields are aligned.
- [ ] Status spinner opens and displays every existing option; selections preserve the existing filter values. Verify on API 26 as well as a current target-compatible device.
- [ ] Search is primary and Clear secondary. Both remain reachable with keyboard open and 200% font size.
- [ ] Results have a section heading/count and structured empty state. Existing result data, paging limits and filter requests remain unchanged.
- [ ] Current/Pending/History (and existing Prosumer sections) have a subtle segmented surface; selected text is readable against the tinted indicator. No solid green empty selection block.
- [ ] Booking refresh is a compact heading action. Error/retry, empty states and pagination remain usable.
- [ ] Reservation energy/status/schedule hierarchy is readable, references secondary, and vector chevrons reflect expanded/collapsed state. TalkBack still announces expansion.
- [ ] Expand/collapse, QR visibility and modify/cancel controls preserve existing role/status rules.

## Android My Profile

- [ ] Profile hero has a circular photo/initials, name, NIC/role, status and completion chip. Camera overlay is visible, labelled and at least 48dp.
- [ ] Camera opens the existing Photo Picker. Cancel, upload failure, successful upload, removal and refreshed photo work without a new permission.
- [ ] Personal information, Account security and Security history occupy separate cards. **No long, unstructured flat Profile form remains.**
- [ ] Identity guidance is a compact info row; Prosumer email reapproval guidance appears only for Prosumer.
- [ ] Inputs have consistent spacing, persistent hints, leading icons and password visibility controls.
- [ ] Save is visually primary; removal/history/security utilities have quieter labelled controls. Password changes still require current password and end prior sessions.
- [ ] History and empty history are presented inside meaningful surfaces. Long references/messages wrap without clipping.
- [ ] Account switching/rotation/background behavior shows no old profile/image. SQLite behavior is unchanged.

## Responsive and accessibility matrix

Record results for each combination; do not infer runtime success from a build.

| Client | Required presentations |
| --- | --- |
| Web | 1920, 1440, 1024, 768 and 390 CSS-pixel widths; 200% browser zoom |
| Web interaction | Keyboard-only, screen reader, visible focus, reduced motion, long names/errors |
| Android size | Normal phone, small phone if available, portrait and landscape |
| Android accessibility | Default and large fonts, TalkBack, light and dark themes, keyboard/system bars |
| Android platform | API 26 and a current device; physical device acceptance |

- [ ] No page-wide horizontal overflow, overlapping cards, clipped buttons or hidden primary actions. Tables may scroll within their existing bounded containers.
- [ ] Icons have accessible action names; decorative icons are ignored. State/priority is never color-only.
- [ ] Touch targets are at least 48dp on Android. Text remains readable with the retained forest/emerald/solar palette.
- [ ] Android cards use theme surfaces in both modes; no new hardcoded white fields/cards in dark mode.
- [ ] Web transitions are restrained and disabled with reduced motion.
- [ ] No major Profile/Notifications/Search content floats ungrouped on a plain background.

## Functional smoke checks after visual acceptance

- [ ] Login/logout, expiry/matching 401, Forgot/Reset/Change Password and profile completion/Skip.
- [ ] Profile upload/save/remove, notifications/read/filter, account histories, search and exports.
- [ ] Existing station/slot actions, Maps, reservation lifecycle and QR scan/verification/completion.
- [ ] Existing role boundaries, Workspace Back behavior, offline denial and local profile cache clearing.

No browser/device screenshot, TalkBack, Maps/camera or SQLite Inspector execution was performed in this polish pass. No hosted CI, commit, push, merge or deployment is claimed.
