# Design

## Context

See proposal.md (Why) for the motivation and specs/feed/detail-visibility for the behavior. Here is how the pieces fit together today:

- **Placement is visibility.** Every client write goes to `clients.placeOf(visibility)` (`core/data/.../activity/Places.kt`): the repo for Public, otherwise the tier space. Nothing carries a visibility field.
- **One streams record per activity.** `PdsActivityStreamsStore` writes a single `feed.activityStreams` record at `ActivityStreams.visibility`. `ActivityRepository.setStreamsVisibility` moves it with `moveOwn`, which writes the new place before deleting the old. `ActivityZonesStore` places zones at `narrower(chartsVisibility, trainingVisibility)`.
- **One row per URI in the AppView.** `Activities`, `ActivityStreams` and `ActivityZones` (`server/appview/.../db/Tables.kt`) all key on `uri`. A record held by two origins mid-move keeps its wider copy in `RecordShadows`, which is promoted if the narrower copy is deleted. `ActivityStreamsViews` serves one row with one visibility.
- **Activity views pass the record through.** `activityView.record` is the raw `feed.activity` JSON. Clients read summary stats straight from it.
- **The defaults record is self-only.** `actor.activityDefaults` holds `visibility` and `chartsVisibility`. `VisibilityChoice(activity, charts)` mirrors it on the client.
- **Space grants list collections.** The PDS fills each `space:` grant from the space type's declared collections, and `SessionClients.canWriteToSpaces(collection)` checks it.

## Goals / Non-Goals

**Goals:**
- One place decides which category each field, channel and lap field belongs to. The client splitter, the merger and the AppView all use it.
- Where data is stored stays the only source of its visibility. There is no "hidden" flag that a reader could ignore.
- Activities published before this change keep working, with no backfill.

**Non-Goals:**
- Hiding the upload time (rkey TID, `createdAt`) or the activity's existence.
- Per-category visibility for zones beyond the Heart rate and Power tiers. Zones stay one record.
- A "hide the map for every activity" setting as a separate toggle. A Map default of Only me covers it.
- Rewriting past activities when the defaults change.

## Decisions

### 1. Split by tier, not by category
Each place (repo, `followers`, `mutuals`, `self`) holds at most one record of each kind under the activity's rkey: the `feed.activity`, a `feed.activityDetails` and a `feed.activityStreams`. Each record holds every category stored at that place.
- **Why:** no new rkey scheme is needed. A tier holds at most four records per activity whatever the category count, and it matches what spaces can already express.
- **Alternative:** one record per category (`rkey = <activity>-hr`). That breaks the "rkey is the activity's" convention the indexer validates, and it multiplies records and blobs.

### 2. A new `feed.activityDetails` record, not stats inside the streams record
The details record holds the narrower summary fields (all optional, the same shapes and units as on `feed.activity`), plus `startedAt` and `polyline`.
- **Why:** feeds need summary stats for many activities at once, and those shouldn't require fetching and decoding stream blobs. A ride with hidden power and no streams still needs somewhere to put its average power.
- **Alternative:** put the summary fields on the streams record. That ties the feed to blob fetches and fails for manual entries, which have no streams.

### 3. A single category table in `core/lexicon`
`DetailCategory` is an open-enum-style set of tokens (`startTime`, `heartRate`, `power`, `speed`, `calories`, `map`, `physiology`, `cadence`, `dive`). With it go maps from category to `ActivityRecord` fields, to `ActivityStreamData` channels (`INT_CHANNELS` plus the structured ones), and to lap and leg fields, plus an explicit `CORE` list (distance, altitude, temperature, gears, e-bike, shifts, jumps, grit, flow, cycles, stroke distance, lengths, sets, the lap/leg structure and the dive summary). Core data is written at the activity's tier.
- **Why:** the client splitter, the client merger and the AppView merger all need it, and it lives next to the models it describes. A unit test fails when a `knownValues` channel or a stat field is in neither a category nor `CORE`, so a newly imported stream gets a deliberate choice rather than defaulting to core.
- **Alternative:** an "Everything else" catch-all category. That brings back a charts-style setting with no clear meaning to athletes, so it was dropped. Families that are worth hiding (cadence and running form, dive) get their own categories instead.

### 4. The client writes in two phases: narrow first, then strip wide
`ActivityLayout.split(draft, categories)` turns a draft into its records for each tier. `ActivityRepository` diffs that layout against the places that hold records now. It puts every new or changed record, narrowest tier first, and only then rewrites or deletes the wider ones.
- New activities go through the outbox, so idempotent `putRecord`s keep their order.
- Edits that change visibility are direct calls, like `moveOwn`, so a failure shows. A failed save is retried by saving again, which recomputes the same layout. This replaces `setStreamsVisibility` and the charts picker.
- The streams blob is split per tier too: each record gets its own blob with `time` plus that tier's channels. `encode()`'s halving applies to each blob.
- **Alternative:** move whole records, as today. That can't express "this field leaves the record but the record stays".

### 5. The AppView keys by `(uri, origin)` and merges per viewer
- A new `activity_details` table has `uri`, `origin`, `visibility`, `activity_uri`, `record` and `indexed_at`, keyed on `(uri, origin)`. It is indexed only from tier spaces. Repo copies are skipped (spec: details in the repo are ignored).
- `activity_streams` moves to the `(uri, origin)` key in a migration. Several tiers are now normal rather than mid-move, so streams leave the `RecordShadows` path. Activities and zones keep it, because each still has exactly one place.
- **Serving:** `Views` collects the details rows for a page of activities in one query, filtered to the tiers each viewer is admitted to (reusing `visibleTo`). It returns them as `activityView.details`, a list of `{visibility, record}` ordered wide to narrow. `getActivityStreams` returns `parts: [{visibility, channels}]` and data merged from every admitted streams row, where a narrower tier's channel wins. It keeps `visibility` (the widest admitted part) for older clients.
- **Client merge:** `ActivityView.effectiveRecord()` overlays the details onto the record, narrowest last. All UI reads go through it. The athlete's edit screen derives each category's current tier from where its data appears in `details` and `parts`.
- **Alternative:** the AppView rewrites `record` per viewer. That makes `cid` no longer match the record, and the owner's edit screen could no longer tell where each field lives.

### 6. Start time rounding
When Start time is hidden, `startedAt` on the activity record is `localMidnight(startedAt, utcOffset)` in UTC. The exact value goes on the details record. The `started_at` column and the date filters use the record's value, so they never see finer than the date. `recordingView.startedAt` comes from each recording's own record, so it is rounded the same way.

### 7. Defaults record
`actor.activityDefaults` gains an optional `details` object with one optional visibility per category token, and `chartsVisibility` is removed from the lexicon. Readers ignore it if an old record still carries it, and saves stop writing it. A category with no default starts at the activity default. `VisibilityChoice` becomes `{activity, details: Map<DetailCategory, String>}` with a `capped()` that narrows every category to the activity.
- **Alternative:** read `chartsVisibility` as the default for the channel categories. That was rejected because "charts" no longer maps to anything the athlete sees.

### 8. Space declaration and scope
`feed.activityDetails` is added to the tier collections of `space.athlete` and to `TIER_COLLECTIONS` in the indexer. Sessions whose grant predates it fail `canWriteToSpaces(FEED_ACTIVITY_DETAILS)`, so publishing anything narrower than its activity is refused up front with the existing "sign in again" error, as for likes. Publishing with every category at the activity's visibility still works for them.

### 9. UI
A shared `DetailVisibilityPicker` (built from `ui/adaptive` components) shows one row per category with Same as activity / Followers / Mutuals / Only me, capped. It is used in Settings, the record/manual/import/batch forms and Edit Activity. On the feed card and detail screen, the owner gets a small lock with the tier on any stat or chart whose category is narrower than the activity.

## Risks / Trade-offs

- [Data is briefly visible at the wider tier during a narrowing edit] → Narrower places are written before wider ones are stripped, which matches today's mid-move window. A failed strip shows as an error and is retried on save.
- [Several blobs per activity increase PDS storage] → Each tier blob carries only its channels, so the total stays close to one full blob plus repeated `time` arrays.
- [The upload time reveals roughly when a recorded activity ended] → This is accepted and documented (proposal Known limit).
- [The AppView feed query gets a join per page] → One batched `IN (activity_uri)` query per page, filtered by admitted tiers, plus an index on `activity_uri`.
- [An older client reading a split activity shows fewer stats] → It shows only what is on the record. It still never shows more than the viewer is allowed.
- [An athlete's saved `chartsVisibility` default stops applying, so new activities' charts follow the activity default] → The categories are new pickers prefilled at the activity default, and the change is called out in the release notes. The app is pre-release, so there are no outside clients to keep in step.
- [The first edit of an old activity moves its core charts (elevation, distance) from a narrow streams tier to the activity's tier] → The client warns before saving (spec: Activities from before categories).
- [Losing `RecordShadows` for streams could leave stale copies after a crash] → The client's session-start repair also recomputes the layout for its own activities whose tiers disagree with their categories, as zones repair does.

## Migration Plan

1. A Flyway migration (the next V number) creates `activity_details` and re-keys `activity_streams` to `(uri, origin)`. Existing rows are unaffected because each has one origin.
2. The lexicon, `core/lexicon` and AppView indexer/views ship first. Responses are additive (`details`, `parts`), so current clients keep working.
3. Then the clients ship: the splitter, merger, pickers and defaults. No backfill: old activities are served as before and re-stored on their next edit.
4. **Rollback:** older clients ignore `details` and `parts`. Hidden data stays hidden from them, and owners lose their view of the split fields until they upgrade.

## Open Questions

- The exact copy and icons for each category row, and whether Physiology, Cadence & running form and Dive collapse behind an "Advanced" expander. This doesn't change behavior.
