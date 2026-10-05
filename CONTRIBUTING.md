# Contributing

Coordinate changes to shared authentication, DTOs, Mongo mappings, configuration and CI with the responsible team members. The verified allocation is in [README](README.md#submission-and-individual-contributions).

## Review workflow

Use focused branches and reviewed pull requests under the team's agreed workflow. Preserve other members' work; do not rewrite shared history or push directly to shared branches without team agreement. Release/submission selection remains a team decision.

## Engineering boundaries

- Domain stays independent. Application services own business validation, Infrastructure owns persistence and API controllers expose the REST boundary.
- Clients use REST; Android stays Java/XML Views with SQLite only for the local profile cache.
- Preserve collection names, NIC/record identifiers, roles, account states and shared contracts. Coordinate schema changes and migrations.
- Every handwritten C# file under src/ and tests/ needs a block with File, Project, Author(s) and Purpose. Attribute substantive work honestly; merging or formatting alone does not establish authorship. Preserve useful Notes.
- Begin each method, constructor and local-function body with a meaningful intent comment. Bodyless interface/abstract declarations are exempt.
- Keep credentials, signing material and tokens out of source, logs and evidence. Preserve release TLS validation.
- Add focused regression tests for behavior changes; do not weaken existing assertions.

## Before review

1. Inspect the diff and ignored files; exclude credentials, generated outputs and private exports.
2. Run the [validation commands](README.md#validation); shared changes require backend, Web and Android checks.
3. Explain the problem, resulting behavior and actual validation. Identify unexecuted manual tests.
4. Update canonical setup/contracts when behavior changes. Use redacted real UI evidence when relevant.
5. Obtain peer review and confirm hosted CI for the selected revision.
