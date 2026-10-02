---
paths:
  - "server/appview/**"
---

# AppView

- **The AppView never writes to repos.** It only reads Jetstream and serves `app.activitytracker.*` queries.
- **Repos are untrusted input.** The `Indexer` decodes with the shared `AtJson` models, drops records that fail to decode (it catches `IllegalArgumentException` and `SerializationException`), and truncates free-text columns with `.take(n)` so they fit the column. New record handlers do the same.
- **Indexing is idempotent.** Every write is an upsert or delete keyed by AT-URI, so rewinding the Jetstream cursor replays safely. Never use a plain `insert` for record rows. Every new record table also needs a delete branch in `Indexer.delete` and a line in `deleteActor`.
- **Don't trust client timestamps for ordering.** Feed order uses `sort_at = min(createdAt, indexedAt)`, so a client can't pin a record to the top by dating it in the future.
- **Migrations.** These are Flyway files in `src/main/resources/db/migration`, named `V<n>__<description>.sql`. Never edit a migration that is already committed. Add the next `V<n+1>`, because the local Postgres has already applied the old one and Flyway rejects a changed checksum. Keep SQL portable: tests run it on H2 in `MODE=PostgreSQL`, so use no Postgres-only types or functions. Timestamps are `BIGINT` epoch millis.
- **Routes.** Add queries with `xrpcQuery(X.method, verifier) { params, viewer -> ... }` in `Routes.kt`. Auth is optional at that level. A handler that needs a viewer throws `AuthException`. Map client errors to `XrpcError(status, "ErrorName", message)`.
- **Tests** (`AppViewTest`) feed `JetstreamEvent.Commit`s into a real `Indexer` over H2 and then read back through `testApplication`. Test new indexing and views the same way, end to end.
