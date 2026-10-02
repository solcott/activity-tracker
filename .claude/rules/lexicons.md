---
paths:
  - "lexicons/**"
  - "core/lexicon/**"
  - "server/appview/src/main/kotlin/**/index/**"
  - "server/appview/src/main/kotlin/**/api/**"
  - "server/appview/src/main/kotlin/**/db/**"
---

# Lexicons

`lexicons/**.json` is the schema source of truth. `core/lexicon` hand-mirrors it, and both the client and the AppView consume the mirror. A lexicon change is one change across all layers: JSON, the Kotlin model, `Nsid`, and the AppView indexer, tables, migration and views. Use the `lexicon-change` skill for the checklist, and the `lexicon-auditor` agent to check the result.

- **No floats.** Distances are integer meters, speeds integer mm/s, durations integer seconds. Document the unit in a KDoc comment on the Kotlin field (see `ActivityRecord`).
- **`$type` on every record.** Models declare `@SerialName("\$type") val type: String = Nsid.X`. `AtJson` sets `encodeDefaults = true` so the default is written. Serialize lexicon data only with `AtJson`, never a bare `Json`.
- **Optional lexicon fields are nullable with a `null` default.** `AtJson` drops nulls and ignores unknown keys, so old and new clients interoperate. Never make an existing optional field required: records already in repos lack it.
- **Open enums are strings.** Known values go in a constants object (like `Sport`), not a Kotlin `enum`, because unknown values from other clients must still decode.
- **All repo records are public.** Never put private data in a record.
- **NSIDs live only in `Nsid`.** `Nsid.NAMESPACE` is a placeholder. The lexicon `id` fields, the `lexicons/app/activitytracker/` path and `docker/scripts/create-account.sh` repeat it, so a rename touches all of them. Add every indexed record collection to `Nsid.indexedCollections`, which is the Jetstream `wantedCollections` filter. A collection missing from it is never indexed.
- **Queries** are an `object` holding `Params`/`Output` and a `val method = XrpcQuery(...)` in `lexicon/app/Queries.kt`. Paginated queries take `limit`/`cursor` and clamp with `Paging.clamp`.
