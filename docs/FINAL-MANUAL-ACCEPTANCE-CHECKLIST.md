# Final Manual Acceptance Checklist

Use disposable accounts and reservations. Do not record passwords, JWTs, raw QR payloads or Maps keys.

## 1. Backend and setup

1. Start MongoDB with `docker compose up -d --wait`.
2. Start one Development API instance and confirm `/health` is healthy.
3. Start the Web client and install the debug Android APK.
4. Confirm the target browser timezone and Android device timezone before recording time evidence.

## 2. Backoffice Web

1. Sign in as active Backoffice.
2. Confirm Home, User Management and Microgrid Stations are visible.
3. Confirm reservation workspace links are absent.
4. Register or inspect a Pending Prosumer and activate it.
5. Create/edit/deactivate a station and inspect its UTC recurring operating schedule.
6. Confirm active reservations protect station and slot mutations.

## 3. GridOperator Web

1. Sign in as active GridOperator.
2. Open Stations and create/edit a slot.
3. Enter a browser-local slot time and verify the API/Mongo value is the corresponding UTC instant.
4. Confirm slot list displays local date/time and a timezone label.
5. Open Manage Reservations and Pending Queue.
6. Approve and reject disposable reservations; verify rejection remarks.
7. Open Current Bookings, Dashboard, History and Search.
8. Search with local datetime inputs and verify UTC query values at the API boundary.
9. Open details and verify local schedule, cutoff, status and action states.
10. Confirm Backoffice and Prosumer direct reservation URLs are denied.

## 4. Android Prosumer

1. From Login, open Create Prosumer account.
2. Submit valid registration and confirm PendingActivation guidance.
3. Confirm duplicate/invalid/network errors are readable.
4. Activate the account from Backoffice, then sign in on Android.
5. Confirm Find Stations, My Account, My Reservations, Current, Pending, Search and History are visible.
6. Create a reservation using the available slot picker, review, confirm and inspect the server summary.
7. Confirm available slot and reservation times display in device-local time.
8. Modify and cancel disposable reservations where allowed; inspect server summaries.
9. Approve a reservation, open My Reservations and display its Approved QR.
10. Confirm Pending, Rejected, Cancelled and Completed reservations do not show QR issuance.
11. Test logout, expiry, matching 401 and account switching; confirm no old profile remains.

## 5. Android GridOperator

1. Sign in as active GridOperator.
2. Confirm Find Stations, Current, Pending, Search, History, dashboard counts and Scan Transaction QR are visible.
3. Confirm My Account, Prosumer registration controls after login, Prosumer create/modify/cancel controls and QR issuance are absent.
4. Scan an owner QR during the accepted transfer window.
5. Confirm server verification details use device-local display time.
6. Complete an eligible transfer and verify completion metadata.
7. Verify too-early, expired, cancelled, rejected, completed, invalid and inactive-related-record errors are readable.
8. Confirm simultaneous/replayed completion leaves only one successful transition.

## 6. Maps, permissions and persistence

1. Allow and deny coarse location; verify useful fallback messages.
2. Confirm station list and Maps markers use stored station coordinates.
3. Select a marker and open station details.
4. Allow and deny camera permission; verify QR scanner recovery/settings guidance.
5. Inspect SQLite and confirm only the current profile is cached, with no password, password hash, JWT or QR secret.
6. Confirm logout, deactivation, matching 401 and account switching clear stale profile data.

## 7. Responsive/accessibility checks

1. Check Web desktop, narrow desktop, mobile-width and 200% zoom.
2. Check keyboard focus, labels, error announcements and table scrolling.
3. Check Android portrait, rotation, large text, light/dark theme and system insets.

Browser, emulator, camera, Maps, SQLite Inspector, IIS runtime, hosted CI and screenshot evidence remain manual until explicitly executed.
