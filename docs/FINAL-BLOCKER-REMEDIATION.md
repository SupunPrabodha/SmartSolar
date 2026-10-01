# Final Blocker Remediation

## 1. Branch and HEAD

- Branch: `Merge-M1-M4`
- HEAD: `687fbbccceb9bb9eaf919f8a9f71747e442561e1`
- No commit, push, merge, branch, deployment, reset, revert, checkout or stash was performed.

## 2. Pre-existing partial changes discovered

The worktree already contained the prior integration pass. It had the singleton `CatalogWriteGate`, reservation lock ordering helpers, QR accepted-snapshot eligibility code, completed reservations retaining `AvailableSlots`, single-IIS documentation fragments, QR tests, web navigation changes, and the added all-member integration test. The partial state also contained stale audit/documentation claims that described those blockers as unresolved. The remaining production defect found during this pass was station capacity protection using the largest active slot allocation instead of the sum of all active Pending/Approved reservation energy.

## 3. Exact production files changed

- `src/SmartSolar.Application/Abstractions/Persistence/IStationCatalogRepository.cs`
- `src/SmartSolar.Application/Services/StationService.cs`
- `src/SmartSolar.Infrastructure/Persistence/Repositories/StationCatalogRepository.cs`

The other production files listed by `git status` were pre-existing partial changes retained and verified, including `ReservationsController.cs`, `ReservationService.cs`, `ReservationQueryService.cs` and `ReservationRepository.cs`.

## 4. Exact tests added or changed

- `tests/SmartSolar.UnitTests/ReservationServiceTests.cs`: deterministic different-Prosumer concurrent energy allocation test.
- `tests/SmartSolar.IntegrationTests/CatalogApiTests.cs`: real Mongo aggregate active-energy capacity reduction and exact-boundary test.
- Pre-existing partial test changes retained and verified in `ReservationQrServiceTests.cs`, `ReservationQrApiTests.cs`, `ReservationMongoTests.cs`, `CatalogServiceTests.cs` and `AllMemberIntegrationTests.cs`.

## 5. B1 resolution

Catalog mutations and reservation allocation/capacity mutations use the same singleton `CatalogWriteGate`. Reservation operations acquire it before the durable per-Prosumer recovery lock. Slot allocation and release advance the slot `UpdatedAtUtc` token through atomic Mongo pipeline updates, preventing stale catalog writes from overwriting newer allocation state. Pending and Approved remain the only protected statuses. The supported deployment assumption is one ASP.NET Core API instance hosted by IIS; this is not a distributed lock or transaction.

## 6. B2 resolution

The station energy read/check/allocation sequence runs inside the shared gate. Active energy is summed across the station for Pending and Approved reservations. Atomic slot count acquisition remains in place. Station capacity reduction is rejected below the total active allocated energy and allowed at the exact boundary. The concurrent 40 kWh versus 40 kWh on a 50 kWh station regression allows at most one request.

## 7. B3 resolution

QR verification and completion require Approved status, matching QR hash, valid accepted schedule snapshots, `ScheduledStartAtUtc <= now < ScheduledEndAtUtc`, an existing active Prosumer, an active station, an active slot, matching station/slot linkage, and the accepted reservation references. Mutable slot times are not used. Completion remains a conditional Approved-plus-QR update, so simultaneous completion succeeds exactly once. Completed does not release `AvailableSlots`, remains non-active for catalog protection and active energy calculations, and appears in history.

## 8. B4 resolution

QR issuance is owner-Prosumer-only in both controller authorization and service ownership enforcement. GridOperator may verify and complete but may not issue or rotate QR. Backoffice may not issue, verify or complete. Tests cover owner success, other Prosumer denial, GridOperator denial and Backoffice denial.

## 9. B5 status

No Maps key was changed in code. The historical key exposure remains a manual owner action: revoke/rotate the old key and configure a restricted replacement privately.

## 10. Lock acquisition order

1. `CatalogWriteGate`
2. Durable per-Prosumer reservation/recovery lock, when required

Locks are released in reverse order.

## 11. Single-IIS-instance assumption

The coursework deployment assumes one ASP.NET Core API instance hosted by IIS. Multiple processes or API instances require a reviewed distributed coordination mechanism before deployment.

## 12. Capacity invariants

- `AvailableSlots` never falls below zero or exceeds `TotalSlots`.
- Slot acquisition and release use conditional Mongo updates.
- Active energy is the sum of Pending and Approved reservation energy for the station.
- Station `CapacityKwh` cannot be reduced below active allocated energy.
- Rejected and Cancelled release one booking place; Completed retains its consumed booking place.

## 13. Exact backend results

`docker compose up -d --wait` passed and MongoDB was healthy.

- `dotnet restore SmartSolarMicrogrid.sln`: passed.
- `dotnet build SmartSolarMicrogrid.sln --configuration Release`: passed, 0 warnings, 0 errors.
- `dotnet test SmartSolarMicrogrid.sln --configuration Release`: 202 unit tests passed and 77 integration tests passed; 0 failed, 0 skipped.
- Focused concurrent energy regression: 1 passed.
- Focused real Mongo capacity regression: 1 passed.

The temporary `SMARTSOLAR_TEST_MONGO` environment variable was removed after testing.

## 14. Exact Web results

From `web/smart-solar-web`:

- `npm.cmd test`: 71 passed, 0 failed, 0 skipped.
- `npm.cmd run build`: passed with Vite production build.

The existing non-failing Node experimental module warning remains.

## 15. Exact Android results

From `mobile/SmartSolarMobile`:

- `:app:assembleDebug`: passed.
- `:app:testDebugUnitTest`: passed.
- `:app:lintDebug`: passed with no build failure.
- `:app:processReleaseMainManifest`: passed.
- Gradle result: `BUILD SUCCESSFUL`, 50 actionable tasks, 16 executed and 34 up-to-date.

## 16. Diff and conflict-marker result

- `git diff --check`: passed.
- Tracked-source conflict-marker scan for `<<<<<<<`, `=======` and `>>>>>>>`: no matches.
- Common tracked-secret scan: no matches.
- Ignored local secret files confirmed: `mobile/SmartSolarMobile/secrets.properties` and `web/smart-solar-web/.env.local`.

## 17. Remaining manual tests

- Owner must revoke/rotate the historical Maps key and confirm the replacement is restricted.
- Browser role navigation and live API acceptance remain manual.
- Android emulator account/session, SQLite cache, Maps, location permission, camera QR scan, rotation/background, large-font and theme checks remain manual.
- IIS hosting and single-instance configuration must be checked in the target environment.
- Hosted CI, signing and submission evidence remain manual.

## 18. Remaining blockers

No B1-B4 code blocker remains from the requested remediation pass. B5 remains pending manual Maps-key revocation/rotation.

READY AFTER MAPS KEY REVOCATION