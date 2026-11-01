# Design

## Context

- **AppView ordering:** `activities.sort_at` is set at index time to `min(record.createdAt, now)` (`index/ActivityIndex.kt`). `Views.activityPage` pages the timeline and `getAuthorActivities` on `(sort_at, uri)` descending, with cursor `"<sortAt>::<uri>"` (`api/ViewQueries.kt` `keysetBefore`). Each activity is one row, from the repo or from one tier space, carrying only its own record's fields. Narrower details live in other tables.
- **Batch save:** `saveBatch` (`feature/record/presenter/.../BatchImport.kt`) saves combined groups first (`ActivityManager.combine`), then the activities that stand alone, through `ActivityRepository.publishBatch`. Each record's `createdAt` is `clock.now()` when it's queued.
- **Client cache:** `FeedRepository` caches each feed's first page in Room's `feed_item` (one `ActivityView` JSON per row, keyed by feed and URI) and filters it through local deletes, blocks and mutes. The timeline and author feeds share this code.
- **Constraints:** lexicons have no floats, records carry `$type`, everything served is untrusted input on index, and SQL must run on Postgres and H2 (PostgreSQL mode).

## Goals / Non-Goals

**Goals:**
- One stored feed position per activity, computed at index time, so the timeline stays a keyset scan.
- Import cards decided per viewer, from the same visibility rules the timeline already applies (`visibleTo`, `unhidden`, `shownInFeeds`, mutes).
- No change to how a single activity is stored or shown, except the new optional field.

**Non-Goals:**
- Precomputed per-viewer card counts.
- Changing the grace or threshold at runtime, per athlete or per viewer.
- Making existing activities part of a batch. Only saves after this change name one.

## Decisions

### 1. Store the post time and end time, and derive the position from them

Add `posted_at` (`min(createdAt, now)`, as `sort_at` is today) and `ended_at` (`startedAt + elapsedTime`, or `startedAt` when the record has no elapsed time) to `activities`. `sort_at` becomes `min(posted_at, ended_at + GRACE)` with `GRACE = 5 days`. A row is backfilled when `ended_at + GRACE < posted_at`. Both are computed from the row's own record only, so a field shared in a narrower tier never moves the position.

*Alternative:* sort on `started_at` alone (late syncs would be lost). *Alternative:* compute the position in the query (no index, so no keyset scan).

### 2. `importBatch` is an optional TID on `feed.activity`, not a batch record

`"importBatch": {"type": "string", "format": "tid"}`. It sits in the activity record wherever that record lives (repo or tier space), and no detail category moves it. A batch record would be public and would reveal how many activities the batch has, private ones included. The indexer keeps it only when `Tid` parses it, and stores it in `activities.import_batch`.

*Alternative:* AppView heuristics (same author, posted close together). These merge two imports made close together, and they can't name a batch for the batch screen.

### 3. Clients tag drafts; combines inside the batch carry the tag

`ActivityDraft` gains `importBatch`. `BatchImport` draws one `Tid.next(clock)` for each save and sets it on every draft. The combined record built for a group within the batch takes the tag (all its members share it). `combine(into, joining)` into a logged activity never adds it, so the logged activity keeps its own tag (or none). `ActivityLayout` keeps the field on the activity record.

### 4. Timeline page = activities merged with cards, in one keyset sequence

For an unfiltered timeline request, with `S` the timeline's existing row predicate (followed or own, not muted, `visibleTo`, `unhidden`, `shownInFeeds`):

```
qualifying = SELECT author_did, import_batch, MAX(posted_at) AS pos, COUNT(*) AS n
             FROM activities WHERE S AND backfilled AND import_batch IS NOT NULL
             GROUP BY author_did, import_batch HAVING COUNT(*) >= 5

items      = activities WHERE S AND NOT (backfilled AND (author_did, import_batch) IN qualifying)
             keyed (sort_at, uri)
           + qualifying keyed (pos, "batch:" || author_did || ":" || import_batch)
```

Each part is read with the same keyset bound and `limit`. The two lists are merged in Kotlin, cut to `limit`, and the cursor is taken from the last item: `"<pos>::<key>"`, as today. Since `"batch:…"` and `"at://…"` keys never collide, the order is total and cards never repeat across pages. A filtered request (`type`, `since`, `until`) skips the cards and reads activities only, as today.

Cards are hydrated from the rows they count: the count, the earliest and latest `started_at`, a count per `sport`, the sum of the `distance` column (the record's own distance; distance is a core field, always at the activity's tier, so a details record never adds one), and the polylines of the 4 latest-started rows whose records have one.

*Alternative:* one SQL `UNION ALL` with a CTE. It's cleaner in theory, but Exposed's union support plus the H2 tuple `IN` make it brittle, and two indexed reads give the same result.

*Alternative:* precomputed counts per (author, batch, visibility), summed over the tiers the viewer is admitted to. That's faster, but it can't account for labels or per-viewer blocks. Keep it as a later optimization.

### 5. Lexicon shapes

- `feed.defs#importCardView`: `author` (`actor.defs#profileViewBasic`), `batch` (tid), `count`, `earliestStartedAt`, `latestStartedAt`, `sports` (array of `#sportCount {sport, count}`), `distance` (meters, optional), `routes` (up to 4 encoded polylines), `postedAt`.
- `feed.getTimeline` output: `feed`, an array of union [`#activityView`, `#importCardView`] with `$type` on each item, and `cursor`. `activities` is removed (proposal: Compatibility). If keeping it turns out to be needed, the fallback is to serve `activities` as well, filled with only the stand-alone activities.
- `feed.getImportBatch`: params `actor` (at-identifier), `batch` (tid), `limit`, `cursor`. Output `activities` and `cursor`. Ordered `(started_at, uri)` descending over every row in the batch that passes `visibleTo`, `unhidden` and `shownInFeeds`, recent ones included.

### 6. Profile ordering

`authorActivities` orders on `(started_at, uri)` descending with the same cursor shape. `activities_author_started_idx` (V10) already covers it.

### 7. Client: a timeline item type, separate from author feeds

`FeedRepository.timelineItems` (and `timelineItemOutcomes`) return `List<TimelineItem>` (`Activity(view)` / `ImportCard(view)`). `timeline` keeps returning the activities alone for its other callers, and author feeds keep `List<ActivityView>`. A card is cached in `feed_item` at its place, under the key `importCard:<did>/<batch>` with the card's JSON. That key is never an activity URI, so `updateCached` and `deleteByUri` never touch it, and the schema doesn't change (no Room migration). *Alternative:* a `kind` column, which costs a migration and tells nothing the key doesn't. Local blocks and mutes drop a card by its author. Local deletes drop activities only (a card's count stays until the next refresh).

The card UI lives in `feature/feed/ui`. `ImportBatchScreen(actor, batch)` goes in `:feature-feed-api`, with its presenter in `-presenter` reading `FeedRepository.importBatch` pages and grouping by month, and its UI in `-ui`. The month headers reuse whatever the batch form uses, moved to `:app-common-ui` if it's private to the record feature.

## Risks / Trade-offs

- **Cost of the qualifying query:** it runs on every unfiltered timeline page, and scans every followed author's backfilled rows that carry a batch. → Index `(author_did, import_batch)` where `import_batch IS NOT NULL`. Watch page latency with a seeded 5,000-activity import, and fall back to Decision 4's precomputed counts if it's slow. *Measured (H2 in-process, two followed athletes with 2,500 batched activities each plus 200 recent ones, 30 per page):* carded pages took 29–33 ms after a 66 ms first page, and filtered pages (no cards) took 10–11 ms. That's acceptable, but it's the first thing to check on Postgres under real load.
- **A card's count can change between pages** (an upload still landing, a visibility change). → The card is keyed by batch, so it appears once. Its count reflects the page it was served on.
- **Cards can't widen anything:** every number comes from rows the viewer already passes `visibleTo` for, and from record columns only. → Spec scenarios on private activities and narrower distances become AppView tests.
- **Batches saved before this change have no tag,** so earlier imports sink (Decision 1) but never collapse. → This is accepted. They no longer flood the feed.
- **Breaking `getTimeline` output.** → The client and AppView ship together. Decision 5 gives the fallback.
- **An athlete can tag anything as a batch.** → Only backfilled rows collapse, so a tag can only fold the athlete's own old activities into a card. That's harmless.

## Migration Plan

1. Flyway migration (next free version): add `posted_at` and `ended_at` (nullable, then backfilled and set `NOT NULL`) and `import_batch` (nullable). Backfill: `posted_at = sort_at`, `ended_at = started_at + COALESCE(elapsed_time, 0) * 1000`, `sort_at = LEAST(posted_at, ended_at + 432000000)`. Add the index from Decision 4. `LEAST` works on both Postgres and H2.
2. Existing rows have `import_batch = NULL` until their records are re-indexed with a tag. None will be, since earlier saves never set one.
3. Deploy the AppView and the clients together.
4. **Rollback:** the old code ignores the new columns. Rows it re-indexes get the old `sort_at`. The rest keep the new one, which is harmless.
