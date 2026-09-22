# Contributing

This is the SE4040 four-member Smart Solar team foundation. Agree feature ownership before starting implementation and coordinate changes to shared authentication, DTOs, Mongo mappings, configuration and CI.

## Planned Git workflow

| Branch | Purpose |
| --- | --- |
| `main` | Release/submission-ready work |
| `develop` | Reviewed integration work |
| `feature/*` | Member-owned features |
| `bugfix/*` | Focused corrections |
| `docs/*` | Documentation changes |

After the team leader manually establishes the repository and branches, work from current `develop`, create a member branch, make focused commits, push that branch and open a PR to `develop`. CI and peer review must pass before merge. The team leader controls releases into `main`. Do not push directly to shared branches or rewrite another member's work.

Planned remote: `https://github.com/SupunPrabodha/SmartSolar.git`. Repository initialization, branch creation, remote setup and all commits/pushes are manual team actions.

## Engineering boundaries

- Keep Domain independent; use Application services for authoritative rules, Infrastructure for persistence, and thin API controllers.
- Clients call the REST API only. Android remains Java/XML Views; SQLite is local persistence only.
- Preserve collection names, NIC identifiers, roles, account states and API contracts. Coordinate migrations and shared DTO changes.
- Preserve the required project header in every handwritten C# file. Begin methods with meaningful purpose comments where the assignment requires them.
- Keep passwords, signing keys and tokens out of source, screenshots and logs. Never weaken release TLS or add trust-all certificate code.
- Implement only your agreed feature scope. Phase-0 module cards are deliberately disabled and carry no business data.
- Add focused tests for behavior and meaningful regressions, not tests that merely mirror code.

## Before a pull request

1. Review changed files and local ignore rules. Exclude secrets, local environment files, build outputs and database exports.
2. Run the relevant builds/tests from [onboarding](docs/TEAM-ONBOARDING.md). For shared changes, validate backend, web and Android.
3. Describe the concrete problem, resulting behavior and validation. State any manual tests still needed.
4. Include API/documentation updates when contracts or setup change. Attach UI evidence without personal data or credentials.
5. Request a teammate's review; resolve feedback and merge only after green CI.

The common foundation owns session behavior and shared shells. Station management, slots/reservations, Maps/QR, operator workflows and business dashboards belong to the feature phase and need explicit team ownership.
