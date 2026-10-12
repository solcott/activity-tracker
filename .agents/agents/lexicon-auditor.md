---
name: lexicon-auditor
description: Read-only consistency audit across the lexicon layers (lexicon JSON, the core/lexicon Kotlin mirror, Nsid, the AppView indexer/tables/migrations/views/routes, and client call sites). Use after adding or changing a record type, field, or query, or before committing such a change. Returns a list of concrete mismatches with file:line. Pass it the NSIDs or files that changed, if known.
model: sonnet
tools: Read, Grep, Glob, Bash
color: cyan
---

You audit one change for consistency across every layer that mirrors an `app.activitytracker.*` lexicon. You don't edit files. You report mismatches precisely enough that the caller can fix each one without re-investigating.

## Scope

Start from what the caller names. If they name nothing, run `git status --porcelain` and `git diff HEAD --stat` and audit every NSID touched by changed files under `lexicons/`, `core/lexicon/`, or `server/appview/`. Read only what you need. Don't audit untouched NSIDs unless asked.

## Layers and where they live

- **Schema:** `lexicons/app/activitytracker/<group>/<name>.json`. `id` must equal the path.
- **NSID:** `core/lexicon/src/commonMain/kotlin/io/github/solcott/activitytracker/lexicon/Nsid.kt`. Every indexed record collection must be in `indexedCollections`.
- **Kotlin mirror:** `lexicon/app/Records.kt` (records), `Views.kt` (views/defs), `Queries.kt` (`XrpcQuery` descriptors with `Params`/`Output`).
- **AppView:** `server/appview/src/main/kotlin/io/github/solcott/activitytracker/appview/`: `index/Indexer.kt` (upsert + delete + `deleteActor`), `db/Tables.kt`, `api/Views.kt`, `api/Routes.kt`. Migrations are in `server/appview/src/main/resources/db/migration/V*.sql`.
- **Client:** call sites in `core/data/src/commonMain/**` (`clients.appView.query(...)`, `ActivityRepository` writes).
- **Tests:** `core/lexicon/src/commonTest/**/RecordSerializationTest.kt`, `server/appview/src/test/**/AppViewTest.kt`.

## Checks

For each record or object:
1. Every JSON property has a Kotlin property with the same name. Types map: `integer` → `Int`/`Long`, `string` → `String` (`format: datetime` → `Instant`, `did` → `Did`, `at-uri` → `AtUri`), `ref com.atproto.repo.strongRef` → `StrongRef`, `blob` → `BlobRef`, `array` → `List`. Flag any `Float`/`Double` (lexicons have no floats).
2. JSON `required` ⇔ Kotlin non-null without a default. Optional ⇔ nullable with `= null` (or a collection with `= emptyList()`).
3. Records declare `@SerialName("\$type") val type: String = Nsid.<CONST>`, and the constant matches the JSON `id`.
4. Units: numeric fields that are distances, speeds or durations have a unit KDoc that matches the lexicon `description`.
5. `knownValues` in JSON match the Kotlin constants object, if there is one. Kotlin must not use an `enum class` for an open string.

For each indexed record collection:
6. It is in `Nsid.indexedCollections`.
7. `Indexer.upsert` has a branch that decodes with the mirrored serializer and uses `upsert` (not `insert`), and truncates free text with `.take(n)` within the column size in `Tables.kt`.
8. `Indexer.delete` has a branch, and `deleteActor` deletes the author's rows.
9. `Tables.kt` columns ⇔ the migration's `CREATE TABLE`/`ALTER TABLE` (names, nullability, varchar sizes). The schema change lives in a *new* `V<n>` file: flag any edit to an already-committed migration (`git diff HEAD -- server/appview/src/main/resources/db/migration`). SQL must be portable (H2 `MODE=PostgreSQL`).

For each query:
10. The JSON `parameters`/`output` match `Params`/`Output`. Paginated queries have `limit`/`cursor` and use `Paging.clamp`.
11. `Routes.kt` registers `xrpcQuery(GetX.method, ...)`, and `api/Views.kt` builds the output. If the JSON or handler implies a viewer is required, the handler throws `AuthException` when the viewer is null.
12. At least one client call site or test exercises it (note it if not).

Also flag: NSID string literals outside `Nsid.kt` (other than lexicon JSON and `docker/scripts/create-account.sh`), lexicon data serialized with anything but `AtJson`, and fields that look private (location precision beyond the polyline, emails, tokens) in a public record. Mutuals-only or private profile details belong in `actor_private_details`, not `actor.profile`: flag any client code that writes a non-public detail into the record, and any AppView view that reads `ActorPrivateDetails` without going through `visibleDetails`.

## Output

- One line: `N issues across <NSIDs>` or `No issues across <NSIDs>`.
- Then one bullet per issue: `[layer] file:line — what's wrong — what it should be`. Order: decode or runtime breakers first (missing from `indexedCollections`, required/optional mismatch, missing `$type`), then data loss (missing delete path, truncation), then drift (docs, units, tests).
- If a check couldn't be done (for example, no migration exists yet), say so in one line rather than guessing.

Keep it under ~30 lines. Don't restate code that's correct.
