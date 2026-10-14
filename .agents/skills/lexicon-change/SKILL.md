---
name: lexicon-change
description: Add or change an app.activitytracker lexicon (a new record type, a new or changed field, or a new XRPC query) and carry the change through every layer that mirrors it (JSON, core/lexicon Kotlin, Nsid, AppView indexer, tables, Flyway migration, views, routes, client repositories and tests). Use whenever a task touches lexicons/ or needs new data stored in repos or served by the AppView.
argument-hint: <what to add or change>
---

# Lexicon change

Task: `$ARGUMENTS`

Lexicon JSON is the source of truth, and everything else is hand-mirrored. Work top-down in this order, so each layer compiles against the one above it. The `lexicons.md` and `appview.md` rules hold the conventions. This skill is the checklist.

## 0. Decide the shape (confirm with the user if ambiguous)

- **Record or query?** Data an athlete *owns and writes* is a record in their repo. Data *computed across repos* (counts, feeds, viewer state) is a view returned by an AppView query. Never put aggregates in records.
- **Public?** Every record is public. If the field is private (health data the user didn't opt to publish, precise home location, contact info), stop and raise it with the user. Non-public profile details already have a home (the athlete's tier spaces, `actor.privateDetails` records, see the `data-and-auth.md` rule); anything else private needs a decision.
- **Compatibility.** New fields on existing records must be optional, because records already in repos lack them. Never rename or retype a published field. Add a new one and deprecate the old in its `description`.
- **Units.** Integers only: meters, mm/s, seconds, bpm. Name the unit in the `description`.
- **Open enums** use `knownValues`, not `enum`.

## 1. Schema: `lexicons/app/activitytracker/<group>/<name>.json`

Copy a sibling: `feed/kudos.json` (a small record), `feed/activity.json` (a rich record), `feed/getActivity.json` (a query), `feed/defs.json` (views). Records use `"key": "tid"`, or `"literal:self"` for singletons like the profile. `id` must match the path.

## 2. Kotlin mirror: `core/lexicon/src/commonMain/kotlin/io/github/solcott/activitytracker/lexicon/`

- `Nsid.kt`: add the constant. For a record the AppView should index, also add it to `indexedCollections`. Missing it means Jetstream never delivers the record.
- `app/Records.kt`: a `@Serializable data class XRecord(...)` with `@SerialName("\$type") val type: String = Nsid.X` last. Optional fields are `T? = null`. Add unit KDocs.
- `app/Views.kt`: view and def classes for query outputs.
- `app/Queries.kt`: `object GetX { Params; Output; val method = XrpcQuery(Nsid.X, Params.serializer(), Output.serializer()) }`. Paginate with `limit`/`cursor` and `Paging.clamp`.
- Test: extend `core/lexicon/src/commonTest/.../RecordSerializationTest.kt`. Assert `$type` is emitted, nulls are omitted, unknown fields are ignored, and the record round-trips.

## 3. AppView: `server/appview/`

For a **record**:
- `src/main/resources/db/migration/V<next>__<desc>.sql`: a new file. Never edit a committed one (a hook will ask). Write portable SQL for H2 `MODE=PostgreSQL`, with `BIGINT` epoch-millis timestamps, a `uri VARCHAR(512)` primary key, and indexes for the queries that will read it.
- `db/Tables.kt`: an Exposed `Table` matching the migration exactly.
- `index/Indexer.kt`: a branch in `upsert` (decode with `AtJson` + `XRecord.serializer()`, `Table.upsert`, `.take(n)` on free text), a branch in `delete`, and a line in `deleteActor`. Never trust record timestamps for ordering (see `sort_at`).
- For a new field on an existing record, decide whether a column is needed (filtering or sorting) or whether the stored `record` JSON is enough.

For a **query**:
- `api/Views.kt`: a `suspend fun` in `db.tx { }` that builds the `Output`. Reuse `hydrate`/`basicProfiles`/`countBySubject` where they fit.
- `api/Routes.kt`: `xrpcQuery(GetX.method, verifier) { params, viewer -> views.x(viewer, params) }`. Throw `AuthException` if a viewer is required, and `XrpcError(400, "Name", msg)` for bad input.

Test in `src/test/.../AppViewTest.kt`: commit Jetstream events through the real `Indexer` (including a `delete` op), then read back through `testApplication` over HTTP.

## 4. Client: `core/data`

- Reads: a repository method that calls `clients.appView.query(GetX.method, GetX.Params(...))` and, if the UI should render offline, caches through Room. A Room entity change requires an `AppDatabase.version` bump and a migration; never hand-edit `core/data/schemas`.
- Writes: go through `ActivityRepository`'s outbox (a TID rkey, idempotent `putRecord`), with an optimistic `FeedRepository.updateCached` where a cached view changes.
- Test in `core/data/src/jvmTest` with `inMemoryDatabase()` + `MockEngine`.

## 5. Verify

1. Run the `lexicon-auditor` subagent or skill with the NSIDs you touched. Fix everything it reports.
2. Verify with the `gradle-runner` agent/skill or execute directly: `./gradlew :core-lexicon:jvmTest :server-appview:test :core-data:jvmTest`, then `./gradlew format` and `./gradlew check` once those pass.
3. Optional end-to-end: the `local-stack` skill's smoke test, writing one record of the new type with `putRecord` and reading it back from the AppView.

If you renamed `Nsid.NAMESPACE`, also update the `lexicons/app/activitytracker/` path, every JSON `id`, and `docker/scripts/create-account.sh`.
