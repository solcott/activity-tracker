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
- **Open unions** (`feed.activity#media`) are a sealed interface with one `@Serializable` class per known member, each writing its own `$type`, plus an `Unknown(json)` member, through an `OpenUnionSerializer` (`app/ActivityMedia.kt`). An unknown `$type` decodes to `Unknown` and re-encodes unchanged, so a client that rewrites a record never drops what a newer one added.
- **Open enums are strings.** Known values go in a constants object (like `Sport`), not a Kotlin `enum`, because unknown values from other clients must still decode.
- **All repo records are public.** Never put private data in a record. A profile detail the athlete hasn't made public goes to the AppView private store through `actor.putPrivateDetails`, never into `actor.profile`.
- **NSIDs live only in `Nsid`.** `Nsid.NAMESPACE` is a placeholder. The lexicon `id` fields, the `lexicons/app/activitytracker/` path and `docker/scripts/create-account.sh` repeat it, so a rename touches all of them. Add every indexed record collection to `Nsid.indexedCollections`, which is the Jetstream `wantedCollections` filter. A collection missing from it is never indexed.
- **Space types** (`"type": "space"`, like `space/athlete.json`) declare an atproto space and the collections it may hold. They have an `Nsid` constant but no Kotlin model, and they stay out of `indexedCollections`: nothing in a space reaches the firehose. The PDS copies a declaration's `collections` into the `space:` OAuth scope at sign-in, and an OAuth session can write only those, so a new space collection goes into the declaration before anything writes it (and athletes sign in again to pick it up). Locally, `scripts/dev.sh` publishes the declarations so the PDS resolves them (`docker/scripts/publish-lexicons.sh`).
- **Queries** are an `object` holding `Params`/`Output` and a `val method = XrpcQuery(...)` in `lexicon/app/Queries.kt`. Paginated queries take `limit`/`cursor` and clamp with `Paging.clamp`.
