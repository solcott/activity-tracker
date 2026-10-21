# Design

## Context

See proposal.md for the motivation and the specs for the behavior. What already exists and constrains the design:

- **Segment tracks** (`segments/segment-track`):
  - Each one is a `feed.segmentTrack` in the athlete's `self` space: a JSON blob of pieces with E7 positions and seconds from the track's start.
  - The AppView keeps rows in `segment_tracks` (`data_cid`, `zones_revision`) and must read them only through `usableSegmentTracks()` (`server/appview/.../db/SegmentTrackQueries.kt`).
  - Turning matching off drops the rows (`index/SegmentIndex.kt`).
- **Reading blobs as a member:** the AppView already does this for tier spaces with `SpaceBlobs.fetch(space, repo, cid)` (`MemberSpaceBlobs`, `api/SpaceMedia.kt`). It's wired in `Application.kt` only when a signing key is configured; with no key, nothing syncs and nothing matches.
- **Background work** runs as `scope.launch` loops (`SpaceSyncer.start`, `BskyBlockBackfill.start`, `ModerationAccessLog.start`). There is no job framework.
- **Tier filtering:** `visibleTo(viewer, visibility, author, record, cid)` (`api/TierAccess.kt`) filters any table by tier. Blocks and moderation (`served`) are included, and it takes another table's activity column as `record`.
- **Category tiers:** `DetailCategory.currentTiers(activity, record, details, parts)` (`core/lexicon`) reads each category's tier from the record, details and streams parts the AppView already indexes.
- **Combined activities:** valid members are decided at read time (`api/Members.kt`, `validClaim`/`isMember`).
- **SQL must run on H2:** tests run on H2 in PostgreSQL mode, so there is no PostGIS (`.agents/rules/appview.md`). Migrations run up to `V28__labels_and_account_status.sql`.
- **Geometry:** `core/location` has `TrackMath` (distance, RDP simplification, precision-5 polylines) and `trimPieces`. The AppView depends on `:core-activityfile` and `:core-atproto`, not `:core-location`.
- **Sports:** they're open-enum strings (`Sport`, `SubSport` in `core/lexicon`). The client's `SportCatalog` (`core/data`) knows which are indoor, but the AppView doesn't depend on `core/data`.

## Goals / Non-Goals

**Goals:**
- Matching that is correct and repeatable:
  - the same track and segment always give the same efforts;
  - every recompute replaces an activity's efforts as a whole, so replays are safe.
- Efforts never visible wider than the data they reveal (route and pace), enforced in SQL through the existing `visibleTo`.
- Matching runs off the request path and survives restarts.

**Non-Goals:**
- Creating segments in the app, duplicate detection, the proposed/active state (`segment-creation`).
- Leaderboards, ranks across athletes, plausibility caps, the e-bike board (`segment-leaderboards`). This change only marks efforts as e-bike.
- Favorites and OSM import.
- Showing efforts on the activity map. The list on the activity page links to the segment screen instead.
- Moving or elapsed time beyond the simple elapsed time.

## Decisions

### 1. `feed.segment`, with the line as a precision-6 polyline
The record has `name` (≤ 100 graphemes), `sport` (open enum: `ride`, `run`, `hike`, `ski`, `paddle`), `line` (string, an encoded polyline at precision 6, ≤ 100 kB), `source` (open enum: `athlete`, `osm`, `agency`), `license` (string ≤ 64, e.g. `ODbL-1.0`), an optional `derivedFrom` (strong ref to the activity), and `createdAt`.
- **Why precision 6:** it's about 11 cm, enough for matching and well under GPS error, and keeps a 50 km segment at roughly 60 kB.
- **Rejected: E7 integer arrays.** They're about 4× larger in every public record and on every firehose replay.
- **Rejected: a length field.** The AppView computes the length (`segments/segment-records`), so a record can't lie about it.
- **Namespace:** `feed`, next to activities and segment tracks, rather than a new `segment.*` namespace.

### 2. Sport groups live in `core/lexicon`
`SegmentSport.of(sport, subSport): Grouped?` returns a group and an `eBike` flag, or null. Both the AppView and the client use it, so it sits beside `Sport`, not in `core/data`'s `SportCatalog`.

| Group | Sports | Sub-sports included |
|---|---|---|
| `ride` | `ride`, `handcycle` | any outdoor one (not `indoor` or `virtual`); `eBike` and `eMountain` set the e-bike flag |
| `run` | `run` | any but `treadmill`, `indoorTrack`, `virtual` |
| `hike` | `hike`, `walk`, `rucking`, `snowshoe` | any outdoor one |
| `ski` | `xcSki`, and `ski` only when `backcountry` | lift-served skiing has no group |
| `paddle` | `kayak`, `canoe`, `sup`, `row` | outdoor only; `whitewater` has none |

A unit test pins every known value, so a new sport gets a deliberate choice.

### 3. Segment geometry in `core/location`
A new `SegmentGeometry` in `core/location`. It's KMP, so the client can reuse it in `segment-creation`'s preview, and the AppView gains `implementation(projects.coreLocation)`.
- **Resampling:** `resample(line, every = 10.0)` gives the segment's reference points along its length.
- **Pass detection:** `passes(piece: List<TimedPoint>, ref: List<Point>, startRadius = 25.0, endRadius = 25.0, corridor = 30.0): List<Pass>`. A forward sweep over the piece:
  1. Find each local minimum of distance to the start point under `startRadius`.
  2. From there, advance a reference index while the track stays within `corridor` of the next reference point (each track sample may advance it several steps).
  3. Succeed when the last reference point is reached within `endRadius`.
  4. Fail when the track gets further than `corridor` from every reference point between the current one and the next 3.
- **Timing:** the start and end times are interpolated on the track segment closest to each endpoint.
- **What it rejects by construction:** reversed passes (the reference index only moves forward), shortcuts (corridor) and gaps (one piece per call).
- **Constants:** 25/25/30 m mirror common practice for consumer GPS. They're named, so leaderboards can tune them.
- **Rejected: Fréchet distance on the whole subpath.** It's costlier, and doesn't give pass times on its own.

### 4. A cell index of plain integers
A cell is `floor(lat * 100)` and `floor(lng * 100)`, about 1.1 km at mid-latitudes, packed as `latCell * 100_000 + lngCell` into a `BIGINT`.
- `segment_cells(cell, segment_uri)` holds every cell any reference point of a segment falls in, plus each neighbouring cell within 30 m of a point.
- `segment_track_cells(cell, track_uri)` holds the cells of each usable track's pieces. It's written when the track is matched, so a new segment can find past tracks without fetching every blob.
- B-tree indexes on `cell`. It's portable SQL, which PostGIS isn't (H2).
- `searchSegments` turns the box into cells (at most 101 × 101, which is why boxes are capped at 1°) and filters segments by their stored bbox columns.

### 5. A durable job table drives a matcher loop
`segment_match_jobs(kind, subject_uri, enqueued_at)` uses primary key `(kind, subject_uri)`, so enqueueing is an idempotent upsert. There are three kinds:
- **`track`** (a segment track URI): fetch the blob from the athlete's `self` space through `SpaceBlobs`, decode it, replace the track's cells, find candidate segments by cell and sport group, run pass detection, and replace all of the activity's efforts in one transaction.
- **`segment`** (a segment URI): enqueue a `track` job for every usable track sharing a cell with it. Deleting a segment deletes its efforts at index time instead.
- **`tiers`** (an activity URI): recompute the tier of the activity's efforts from indexed rows only (decision 6). No blob is read.

**Who enqueues what (the indexer, in the same transaction as the change):**
- A segment-track upsert enqueues `track`.
- A segment upsert enqueues `segment`. A changed line or sport also deletes the old efforts first.
- An activity, details or streams change for an activity with efforts or a usable track enqueues `tiers`. A change to its sport, sub-sport or member claim enqueues `track` instead.
- A deleted segment track, activity, segment or account, a setting turned off, or a dropped `self` space deletes efforts directly. Nothing is queued.

**`SegmentMatcher.start(scope)`:**
- Takes batches of 20 jobs, oldest first, every 2 s, or at once after an enqueue wakes it (a `Channel` signal).
- A job whose track isn't usable, whose activity isn't indexed yet, or which is a valid member gets its efforts deleted and is dropped. When the activity arrives later, its index enqueues `tiers`/`track` again.
- A blob fetch that fails keeps the job with backoff (re-enqueued with a later `enqueued_at`, retried up to 1 h), like `SpaceSyncer`.

**Rejected: matching inline in the indexer.** It would put blob fetches inside the Jetstream and space-sync paths, which must stay fast and transactional.

### 6. An effort's tier is stored and kept current
`segment_efforts(id, segment_uri, activity_uri, track_uri, author_did, sport, e_bike, start_offset_s, elapsed_s, visibility, started_on)`:
- **`visibility`** is the narrowest of the activity's own row visibility and `currentTiers(...)[MAP]` / `[SPEED]`, each falling back to the activity's visibility when absent. It's computed from the indexed `activities.record`, `activity_details` rows and `activity_streams` parts.
- **`started_on`** is the activity's local date (from `startedAt` and `utcOffset`), which leaderboards will need. It's only ever served with the effort, at the same tier.
- **Queries** apply `visibleTo(viewer, SegmentEfforts.visibility, SegmentEfforts.authorDid, SegmentEfforts.activityUri, null)` and also join `activities` with `visibleTo` on its own row. So an activity moved or taken down is hidden even before its `tiers` job runs.
- **Rejected: computing tiers at serve time.** It needs the details and streams of every row in SQL, which is heavy for the `getSegmentEfforts` lists leaderboards will reuse.

### 7. Views and queries
- **`segmentView`:** `uri`, `cid`, `creator` (`profileViewBasic`), `record`, `distanceMeters` (computed), `viewer.best` (an `effortView` or absent).
- **`effortView`:** `segment` (`segmentViewBasic`: `uri`, `name`, `sport`, `distanceMeters`), `activity` (URI), `startOffset`, `elapsed`, `startedOn`, `eBike`, `best` (boolean).
- **PR:** `best` is computed per query as the minimum `elapsed_s` among the same author's efforts on that segment that the viewer may see.
- **Moderation:** segments of an account that isn't served, and taken-down segment records, are hidden through `served(...)` with `Segments.uri`/`cid`, as activities are.
- **Pages:** `getSegmentEfforts` uses a keyset on `(elapsed_s, id)` with a cursor. `getActivitySegments` returns all of an activity's efforts unpaged; there are only a handful per activity.

### 8. Client
- **`SegmentRepository`** (`core/data`, SessionScope) wraps the four queries.
- **Activity detail:** the presenter loads `getActivitySegments` alongside the activity, in its own `LaunchedEffect`, so a slow or failing load never blocks the page. The state gains `segments: ImmutableList<EffortView>` and a `SegmentOpened(uri)` event, and the UI adds a section after the laps.
- **`SegmentScreen(uri: String)`:** a new screen made with the `new-screen` skill.
  - The presenter loads `getSegment` and the viewer's `getSegmentEfforts`.
  - The UI draws the line with `ActivityMap`, re-encoding the precision-6 line at precision 5 with `TrackMath`. On the web, where there's no map yet, `RoutePreview` draws it.

### 9. Seed script
`docker/scripts/seed-segment.sh <account> <name> <sport> <gpx> [startIndex endIndex]`:
- signs in with `com.atproto.server.createSession` using `TEST_ACCOUNT_PASSWORD` from `docker/.env`;
- uses an inline `python3` step, as `spaces-smoke.sh` and `syncer-smoke.sh` already do, to cut the GPX between the indices and encode it at precision 6;
- `putRecord`s a `feed.segment` with `source: "athlete"` and `license: "CC0-1.0"`.

The local-stack skill documents it.

## Risks / Trade-offs

- **[GPS noise makes a 25/30 m corridor miss real passes or accept near-misses]** → Named constants in one place. Tests use fixtures with jitter on both sides of the threshold. Leaderboards can tighten these together with plausibility caps.
- **[A popular new segment queues many `track` jobs, each fetching a blob]** → Jobs are batched and deduplicated by primary key, and the loop yields between batches. Blob fetches go to the athletes' own PDSes, so they're spread out. Caching decoded tracks can come later.
- **[Dishonest segment tracks]** → Any client can write any track. Efforts are only displayed per tier here; there's no ranking against others until leaderboards add plausibility checks.
- **[Tier changes lag by one job]** → Serving also checks the activity row through `visibleTo`, so a narrowed or deleted activity hides its efforts at once. Only a narrowed Map or Pace/Speed category waits for the `tiers` job, typically seconds.
- **[Segments are public geometry derived from someone's activity]** → Creation is out of scope here. `segment-creation` must stop a segment from starting inside its creator's privacy zone. Seeded segments are local only.

## Migration Plan

- Additive: one lexicon record, four queries, migration `V29__segments.sql`, new tables, and a new loop that starts only when the AppView has a signing key (as `SpaceSyncer` does).
- Existing usable segment tracks: the migration can't enqueue them, so `SegmentMatcher` enqueues a `track` job for every row of `usableSegmentTracks()` that has no `segment_track_cells` on its first start. That's a one-off backfill, and idempotent.
- Rollback: stop the loop. The tables stay unread. Clients ignore the missing queries (the activity page hides the section on error).

## Open Questions

- The section's exact placement and the PR badge styling on the activity page can be settled in UI review without changing the specs.
