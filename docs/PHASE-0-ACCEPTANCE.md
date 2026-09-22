# Phase-0 acceptance

Checked items below are supported by source/configuration inspection, local command results or explicitly identified team-leader evidence. Unchecked items need fresh manual execution. See [final report](PHASE-0-FINAL-REPORT.md) for the command record.

## Architecture and MongoDB

- [x] Four backend layers retained with no circular dependency; Domain has no MongoDB dependency.
- [x] API is the authoritative FAT SERVICE; web/mobile do not access MongoDB.
- [x] Exact collections: UsersDetail, SolarStationInfo, EnergyBookingSlots, EnergyReservation.
- [x] NIC business identifier, string roles and account states preserved.
- [x] Mongo initialization, ID mappings, uniqueness and account persistence covered by passing tests.

## Common auth and backend

- [x] Secure salted password hashing, expiring JWT, server role/account-state checks.
- [x] Development-only Backoffice seed and local User Secrets preserved.
- [x] Common auth/profile/account endpoints only; asynchronous services and ProblemDetails retained.
- [x] All 41 handwritten C# files retain their project header; existing purpose comments retained.
- [x] HTTP health 200, malformed emulator-host login request 400 with no redirect, trusted HTTPS Swagger 200.
- [x] Team leader previously verified real registration, pending activation, Backoffice activation, active login, Swagger and JWT.
- [ ] Recheck login/deactivation behavior through the newly polished client screens using disposable accounts.

## Web

- [x] Responsive React/Bootstrap login and authenticated common layout.
- [x] Full name, role, account state, environment and last successful session verification use real session data.
- [x] Protected Backoffice/GridOperator workspace; Prosumer directed to Android.
- [x] Logout, expiry and /users/me restoration retained; delayed/malformed 401 handling hardened.
- [x] Four HTTP/session regression tests and production build pass.
- [x] Module cards/navigation are disabled and explicitly unimplemented; no business statistics.
- [ ] Manually check login, refresh, reload, sign out, 401, narrow layout, keyboard navigation and 200% zoom.

## Android

- [x] One real Gradle project/source tree; obsolete templates and IDE-only duplicate removed.
- [x] Native Java/XML, package com.smartsolar.mobile, JDK 17 / SDK 35 compatibility retained.
- [x] LoginActivity and internal HomeActivity support active Prosumer/GridOperator; Backoffice mobile sessions rejected.
- [x] Real name, role, account state, verification time, expiry, refresh and sign out.
- [x] Role-specific future module cards disabled; no feature workflows.
- [x] JWT expiry, /users/me, 401 clearing and profile cache retained.
- [x] INTERNET only as an application-requested network permission; no dangerous feature permissions.
- [x] Emulator HTTP limited to DEBUG/10.0.2.2; release manifest disallows cleartext and contains no debug network config.
- [x] Clean/debug build, nine JVM unit tests and lint pass.
- [ ] Run the exact [manual emulator checks](../mobile/SmartSolarMobile/README.md#manual-emulator-checks), including role rejection, lifecycle, keyboard, rotation, light/dark theme and large fonts.

## SQLite

- [x] App-private SQLiteOpenHelper database, version 1, single-profile transactional replacement.
- [x] No password, password-hash or JWT columns; API/Mongo remain authoritative.
- [x] Session preferences/cache are cleared on logout, expiry and matching 401; backups excluded.
- [x] Worker-thread access and try-with-resources helper closure retained.
- [ ] Database Inspector: verify one local_user row after login/refresh, zero after logout/expiry/401/role rejection, and no credential columns.
- [ ] No SQLite device runtime test was executed in this pass; inspect the actual emulator database.

## Docker, tests and CI

- [x] MongoDB 7 Compose health check, localhost-only port and persistent named volume.
- [x] Environment helper, Compose config/up/ps, Release bootstrap/restore/build/test passed.
- [x] 12 backend tests passed with Mongo integration enabled; zero skipped.
- [x] Web npm ci/build and four session tests passed; npm reported zero vulnerabilities.
- [x] Android clean/assembleDebug, nine JVM tests, lint and release-manifest processing passed.
- [x] CI has .NET 8/Mongo, Node 22/web and Ubuntu/JDK 17/Gradle Android jobs; no signing secrets.
- [ ] Hosted CI execution must be confirmed after the first manual push. Local success is not a hosted result.

## Secrets, Git and documentation

- [x] Source/config/documentation secret-pattern scan found only explicit placeholders/test values; no suspected live secret.
- [x] Local .env.local and local.properties remain excluded; .env.example remains includable.
- [x] Build/dependency/IDE outputs, signing files and local database exports ignored.
- [x] No Git directory existed; none was initialized. No branches, commits, push, account changes or deployment.
- [x] Root README, onboarding, contribution guide, Android guide and final report updated.
- [ ] Team leader manually reviews the eventual staged file list and ignore results before committing.
- [ ] Four developers agree module ownership and review shared interfaces.

## Explicitly deferred

Phase 0 does **not** contain complete station CRUD, booking-slot management, reservation lifecycle, authoritative 7-day booking or 12-hour update/cancel feature workflows, Google Maps, QR creation/scanning, operator transaction completion, energy-transfer workflows, real business dashboards, offline synchronization, production signing or IIS deployment.

The common account lifecycle endpoints already present remain part of the foundation. Placeholder cards and domain persistence contracts do not mean a feature is implemented.

**Classification: READY FOR TEAM DEVELOPMENT.** Automated foundation gates pass. Fresh UI/device smoke checks and first hosted CI run remain explicit handoff actions, not claimed runtime successes.
