# Database schema

MongoDbInitializer creates the four required collections and their indexes idempotently at API startup. See [the database contract](../../docs/DATABASE.md) for identifiers, embedded fields, snapshots, concurrency/recovery and local SQLite boundaries. Schema/backfill changes require review; do not infer accepted reservation dates from mutable slots.
