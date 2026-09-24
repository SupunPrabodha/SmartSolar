# Member 3 implementation report — Checkpoint 1

## Scope and starting state

Checkpoint 1 only: contracts and test foundation, on IT23164130 as explicitly
authorized by the user. Phase 0 contained reservation/station/slot entities and
Mongo mappings/indexes, authentication, and client shells, but no reservation
service, endpoints, business rule tests or injectable reservation clock.

## Completed

- Minimal CreateReservationRequest and UpdateReservationRequest: nonempty GUID
  SlotId and positive decimal EnergyAmountKwh only.
- ReservationResponse: server-derived identifiers, energy, UTC schedule, existing
  string status and timestamps, excluding QR credentials.
- ReservationRules: isolated UTC timing, permitted status transitions and overlap
  predicate. This is not a complete ReservationService.
- TimeProvider.System and ReservationRules registration in the API composition root.
- 31 deterministic rule cases and 11 DTO validation/wire-contract cases.
- API, database and business-rule documentation, including deferred requirements.

No dependencies were added. Every new handwritten C# file has the project header
and method-purpose comments. Domain and Infrastructure remain unchanged.

## Contract decisions and team review

Existing IDs, collections, entities, mappings, indexes and all five reservation
statuses are preserved. Update returns Pending for both Pending and Approved
records; cancellation returns Cancelled; terminal/unknown states reject.
Later orchestration must clear stale QR data on successful updates/cancellation.

SHARED CONTRACT CHANGE REQUIRING TEAM REVIEW — PROPOSED ONLY:
accepted schedule snapshots (nullable ScheduledStartAtUtc/ScheduledEndAtUtc) are
needed if referenced slot schedules can change. Retained immutable slot schedules
are the alternative. No extension is applied at this checkpoint. DATABASE.md
records why this decision and legacy-data handling must be resolved before lifecycle
implementation. Response schedule properties are DTO fields only.

New reservation DTOs are additive planned API contracts. The existing shared JSON
conventions and authentication contracts are unchanged. There are no live
reservation endpoints, DTO mappings or database mutations.

## Files added

- src/SmartSolar.Application/DTOs/Reservations/CreateReservationRequest.cs
- src/SmartSolar.Application/DTOs/Reservations/UpdateReservationRequest.cs
- src/SmartSolar.Application/DTOs/Reservations/ReservationResponse.cs
- src/SmartSolar.Application/Services/ReservationRules.cs
- tests/SmartSolar.UnitTests/ReservationRulesTests.cs
- tests/SmartSolar.UnitTests/ReservationDtoTests.cs
- docs/MEMBER-3-IMPLEMENTATION-REPORT.md

## Files modified

- src/SmartSolar.Api/Program.cs
- docs/API-CONTRACT.md
- docs/DATABASE.md
- docs/BUSINESS-RULES.md

## Tests and actual results

Tests were added before ReservationRules. The initial unit test command failed to
compile with CS0246 because ReservationRules did not exist yet. This is the observed
test-first failure, not a run of failing assertions.

Final Release solution build: **passed, zero warnings and zero errors**.

| Suite | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: |
| Unit (42 new + 4 existing) | 46 | 0 | 0 |
| Existing middleware/BSON contracts and Mongo foundation | 7 | 0 | 1 |
| Total | 53 | 0 | 1 |

SMARTSOLAR_TEST_MONGO was unset, so the existing live Mongo foundation test was
explicitly skipped. No live Mongo, capacity/concurrency, endpoint authorization,
browser/device or client build result is claimed.

Rule tests cover exact seven-day acceptance and plus-one-second rejection,
past/current starts, exact twelve-hour update/cancel acceptance and one-second-below
rejection, original and replacement update cutoffs, replacement horizon, active-state
overlap, adjacency, containment, terminal/unknown transitions, invalid intervals,
UTC kinds, reapproval and one clock read per operation.
DTO tests cover positive decimal precision, invalid/valid GUIDs, inability to bind
server-owned fields, camelCase JSON, string status and UTC response timestamps.
DTO tests do not prove future endpoint authorization or service validation.

## Exact validation commands executed

All commands ran from D:\SmartSolar. Full executable paths were used because Git
was not on the tool shell PATH. The resolved .NET SDK was 8.0.200.

Initial expected compile failure:
```powershell
& 'C:\Program Files\dotnet\dotnet.exe' test tests/SmartSolar.UnitTests/SmartSolar.UnitTests.csproj --configuration Release --no-restore
```

Final successful build and tests:
```powershell
& 'C:\Program Files\dotnet\dotnet.exe' build SmartSolarMicrogrid.sln --configuration Release
& 'C:\Program Files\dotnet\dotnet.exe' test SmartSolarMicrogrid.sln --configuration Release --no-build --no-restore
```

Read-only review commands:
```powershell
& 'C:\Program Files\Git\cmd\git.exe' branch --show-current
& 'C:\Program Files\Git\cmd\git.exe' status --short
& 'C:\Program Files\Git\cmd\git.exe' diff --check
& 'C:\Program Files\Git\cmd\git.exe' diff --stat
& 'C:\Program Files\Git\cmd\git.exe' diff -- src/SmartSolar.Api/Program.cs docs/API-CONTRACT.md docs/DATABASE.md docs/BUSINESS-RULES.md
```

## Manual Checkpoint 1 review

1. Confirm IT23164130 and review the four modified files plus seven untracked new
   files listed above. Git diff alone omits untracked file contents.
2. Inspect request DTOs for exactly slotId/energyAmountKwh and the response for no
   QR credential. Confirm all domain entities and Mongo mappings are unchanged.
3. Review ReservationRules and its fixed-clock tests. The test instant is
   2030-01-01T00:00:00Z, requiring no accounts, seeded slots or running API.
   Exactly 2030-01-08T00:00:00Z is accepted for creation; one second later rejects.
   Exactly 2030-01-01T12:00:00Z meets update/cancel notice; 11:59:59Z rejects.
4. Run the successful build/test commands above. Expect 46 unit passes and seven
   existing contract passes; live Mongo is skipped unless explicitly configured.
5. Review the proposed snapshot/immutable-slot decision with the station owner.
   Do not treat current response schedule fields as persisted snapshots.
6. Review and commit manually when satisfied. No Git staging, commit, push, merge,
   branch change or PR creation has been performed.

## Deferred

Complete ReservationService; ownership/account authorization for reservations;
station/slot lookup and availability checks; capacity mutations, compensation and
concurrency tests; endpoints; Web screens; Android screens and summaries.
Maps, QR creation/scanning, transaction/energy-transfer completion, Member 4
dashboards/history/search, deployment, signing and offline synchronization remain
out of scope. No UI/API lifecycle test can be executed yet.

## Classification

**CHECKPOINT 1 READY FOR MANUAL REVIEW AND COMMIT.**

This classification covers the tested contract/policy foundation only. It does not
certify the full Member 3 feature. The accepted-schedule persistence proposal requires
team review before lifecycle implementation. Work stops here; Checkpoint 2 has not
been implemented.
