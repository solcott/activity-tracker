# Design

## Context

See proposal.md for the motivation and the specs for the behavior. What `segments-core`, `segment-creation` and `segment-track-consent` left in place:

- **Efforts:**
  - `segment_efforts` (`V29__segments.sql`) holds one row per pass: `segment_uri`, `activity_uri`, `track_uri`, `author_did`, `sport`, `e_bike`, `start_offset_s`, `elapsed_s`, `visibility` (the stored narrowest tier) and `started_on` (local `YYYY-MM-DD`).
  - Its indexes are `(segment_uri, author_did, elapsed_s)`, `(activity_uri)` and `(author_did)`.
  - Combined-activity members never get efforts, and every pass is written: there's no plausibility check.
- **Serving:** `api/SegmentViews.kt`.
  - `effortViews` joins efforts → activities → segments (`visibleEfforts()`) and filters with `effortsVisibleTo(viewer)`: `visibleTo` on the effort's tier and the activity's row, plus `served` on the segment.
  - `best` comes from one `MIN(elapsed_s)` query per (author, segment).
  - `segmentEfforts` pages with `keysetAfter` on `(elapsed_s, id)`.
- **Grouped keysets:** the AppView uses no window functions. Two queries already page grouped rows with `GROUP BY … HAVING`: `getLikes` (`api/Views.kt`) and `threadPage` (`api/CommentQueries.kt`).
- **Settings and tracks:**
  - `segment_prefs(did, matching, updated_at)` and `segment_tracks(uri, author_did, activity_uri, data_cid, zones_revision, indexed_at)` are indexed from `self` by `index/SegmentIndex.kt`.
  - Every segment-track upsert enqueues a `track` job.
- **Client:**
  - `SegmentPrefsRepository.save(matching)` writes a whole `SegmentPrefsRecord` directly.
  - `PdsSegmentTrackStore.put` queues a `SegmentTrackRecord` with a `PendingBlob`.
  - `list()` returns each stored track's zones revision, and `SegmentTrackSweeper.write` rewrites tracks whose revision differs.
  - `EffortRow` (`composeApp/.../segment/SegmentEffortsUi.kt`) draws the e-bike and PR chips.
  - `SegmentMatchingSetting` is the Settings holder.
  - `EditActivityPresenter` → `ActivityManager.edit` saves activity edits.
- **Superseded:** `segment-track-consent`'s design put the per-activity exclusion on `feed.activity`. This change puts it on `feed.segmentTrack` instead (decision 2).

## Goals / Non-Goals

**Goals:**
- A board never shows an effort that `getSegmentEfforts` wouldn't show the same viewer. It reuses `effortsVisibleTo` rather than a second visibility rule.
- Eligibility is evaluated when a board is read, so an opt-out, exclusion or cap change takes effect without rematching.
- The queries run on both H2 and Postgres.

**Non-Goals:**
- Age and weight brackets (`segment-leaderboard-brackets`).
- Clubs, local legends, or achievements beyond the top-10 rank.
- Retuning the matcher's 25/25/30 m constants. Nothing measured yet says they're wrong.
- A materialised best-per-athlete table (see Risks).
- Notifications when someone takes an athlete's place.

## Decisions

### 1. Lexicon additions
- **`feed/getSegmentLeaderboard.json`** (query):
  - params: `segment` (at-uri, required); `period` (open enum `allTime`/`year`/`month`, default `allTime`); `scope` (open enum `everyone`/`following`, default `everyone`); `eBike` (boolean, default false); `limit` (1–100, default 30); `cursor`.
  - output: `entries: #leaderboardEntry[]`, optional `viewer: #leaderboardEntry` and `cursor`.
  - errors: `NotFound`. An unknown `period` or `scope` value, or `following` without a viewer, is `InvalidRequest`.
- **`feed/defs.json#leaderboardEntry`:** `rank` (integer ≥ 1), `athlete` (`actor.defs#profileViewBasic`), `effort` (`#effortView`). `effortView` already carries the date only (`startedOn`).
- **`effortView`** gains optional `rank` (integer 1–10) and `notRanked` (open enum `hidden`/`excluded`/`implausible`).
- **`actor/segmentPrefs.json`** gains optional `hideFromLeaderboards` (boolean, absent = false).
- **`feed/segmentTrack.json`** gains optional `excludeFromLeaderboards` (boolean, absent = false).
- **Mirrors:**
  - `SegmentPrefsRecord.hideFromLeaderboards`, `SegmentTrackRecord.excludeFromLeaderboards`, and `EffortView.rank`/`notRanked` (nullable, default null);
  - `LeaderboardEntry`;
  - `GetSegmentLeaderboard` in `Queries.kt`, plus constants objects `LeaderboardPeriod`, `LeaderboardScope` and `NotRanked`.
- **Caps:** `SegmentSportGroup.maxSpeedMmPerS(group)` beside `minMeters`, in mm/s like every lexicon speed. The values are 27 778 for ride, 11 111 for run, 8 333 for hike, 33 333 for ski and 6 944 for paddle. It lives in `core/lexicon` so tests and any later client check share it.

### 2. The per-activity exclusion is on the segment track
- **Why there:**
  - It's private, since `self` is read only by the athlete and the AppView.
  - The AppView already indexes the record.
  - The exclusion only matters while a track exists.
  - A field on `feed.activity` would be public, and would need `MatchInputs` to queue work on every change.
- **Writing:** `SegmentTrackStore.setExcluded(activity, excluded)`.
  - It reads the stored record with `getRecord` from `self` and queues a `putRecord` of the same record, with `excludeFromLeaderboards` set and the existing `data` blob ref kept. There's no `PendingBlob`, since the PDS already has the blob.
  - When the record can't be read, or is still only in the outbox, the editor doesn't offer the toggle (spec).
- **Keeping it:**
  - `SegmentTrackStore.list()` returns a small value per rkey (zones revision and excluded) instead of the revision alone.
  - `put(...)` gains an `excluded` parameter. `SegmentTrackSweeper.write` passes the stored flag when it rewrites a track.
  - `SegmentTracks.put` (publish, combine) passes false, which matches the spec: a new or rebuilt activity starts unexcluded.
- **Indexing:**
  - `segment_tracks.leaderboard_excluded` is stored on upsert.
  - An upsert whose `data_cid` and `zones_revision` equal the stored row's updates the row without enqueuing `track`, because the points haven't changed.

### 3. The opt-out is a column on `segment_prefs`
- `segment_prefs.hide_from_leaderboards` is set by `upsertSegmentPrefs`.
- Deleting the record already drops everything, and deleting the row resets it to the default.
- **Client:** `SegmentPrefsRepository` keeps the last read or saved record. It gains `saveLeaderboards(hidden)`, and its `save(matching)` keeps `hideFromLeaderboards` as stored. Both are direct writes, as now.
- **Settings:** `SegmentMatchingSetting` exposes `hiddenFromLeaderboards` and `setLeaderboards(shown)`, and Settings shows the toggle only while matching is on.

### 4. Migration `V31__segment_leaderboards.sql`
- `ALTER TABLE segment_prefs ADD COLUMN hide_from_leaderboards BOOLEAN NOT NULL DEFAULT FALSE`.
- `ALTER TABLE segment_tracks ADD COLUMN leaderboard_excluded BOOLEAN NOT NULL DEFAULT FALSE`.
- `CREATE INDEX segment_efforts_board_idx ON segment_efforts (segment_uri, e_bike, elapsed_s)`.
- **Why no column on efforts for caps or the exclusion:** both are checked at read time (decision 5). A stored copy would need re-syncing on every prefs, track or cap change, and that sync could lag a privacy choice.

### 5. `rankable()`: one predicate for "on a board"
In `SegmentViews`, beside `effortsVisibleTo`, over `visibleEfforts()` with a left join to `segment_tracks` on `track_uri`. An effort is rankable when all of these hold:
- `Segments.status = active`;
- `segment_tracks.leaderboard_excluded = FALSE`;
- `NOT EXISTS (segment_prefs WHERE did = author_did AND hide_from_leaderboards)`;
- `elapsed_s * cap >= distance_m * 1000`, with `cap` the group's mm/s.
  - The check is a `CASE` on `Segments.sport`, in integer arithmetic that both databases run. Zero elapsed fails it.

The same parts, evaluated one at a time for a single row, give `notRanked`, in the spec's order of precedence.

### 6. The board query
`SegmentViews.leaderboard(viewer, params)`:
1. **Checks:** return `NotFound` unless the segment is indexed and `segmentServed(viewer)`. Return empty unless it's `active`.
2. **Filter `F`:**
   - the segment;
   - `e_bike = params.eBike`;
   - `effortsVisibleTo(viewer)` and `rankable()`;
   - for a period, `started_on >= since`, where `since` is `YYYY-01-01` or `YYYY-MM-01` of `clock.now()` in UTC (ISO dates compare as strings);
   - for the `following` scope, `author_did = viewer OR author_did IN (effective follows of viewer)`, via `effectiveFollow()`.
3. **Page:**
   - `SELECT author_did, MIN(elapsed_s) AS best … WHERE F GROUP BY author_did`;
   - keyset `HAVING (MIN(elapsed_s), author_did) > cursor`, as `getLikes` does;
   - `ORDER BY best, author_did`, then `LIMIT`.
   - The cursor is `best::did`.
4. **Ranks:**
   - The first entry's rank is `1 + COUNT(*)` over the same grouped query `HAVING MIN(elapsed_s) < best₀`.
   - Down the page, the rank stays the same on an equal time and jumps to the entry's position on a slower one, which gives competition ranking without a window function.
5. **Efforts:**
   - One query over `F` for the page's authors with `elapsed_s = best` picks each athlete's effort: the earliest `started_on`, then `id`.
   - `effortViews` builds them, so `best` and `eBike` are filled as everywhere else.
6. **Viewer entry:**
   - For a signed-in viewer, their own `MIN(elapsed_s)` under `F`.
   - When present, rank it as in step 4 and build their effort.
7. **Athletes:** profiles come from `basicProfiles`.

**Tie order:** The spec lists the earlier effort first on equal times. Step 3 orders by `(best, author_did)` for a stable keyset. To meet the spec, the page orders entries that share a rank by date before they're returned. The cursor stays on `(best, did)`, and a tie group split across pages keeps its rank.

**Rejected alternatives:**
- **`RANK() OVER`:** H2 supports it, but nothing in the AppView uses window functions, and the grouped `HAVING` keyset already works.
- **A per-athlete best table:** it would need upkeep on every tier, prefs, track and segment change, for every viewer-dependent visibility. Visibility is per viewer, so a stored best can't serve it anyway.

### 7. `rank` and `notRanked` on `getActivitySegments`
- **`rank`:** after `effortViews`, for each effort that is rankable and is the athlete's entry on the all-time, everyone board of its `e_bike` flag (its elapsed time equals the athlete's `MIN(elapsed_s)` under `F`), run step 4's count and set `rank` when the result is ≤ 10. An activity has only a handful of efforts.
  - The entry, not `best`: an athlete's PR may be excluded or implausible, and the board then ranks a slower effort. Keying the rank on `best` would leave an athlete at the top of the board with no badge anywhere (found on the local stack).
- **`notRanked`:** only when `viewer == author` and the segment is `active`, from decision 5's parts. Every effort gets one, not only the best: an excluded ride's efforts all say so.
- `getSegmentEfforts` and `getSegment` are unchanged.

### 8. Client
- **Repository:** `SegmentRepository.leaderboard(segment, period, scope, eBike, cursor)`.
- **`SegmentLeaderboardScreen(uri, name, sport)`**, made with the `new-screen` skill:
  - State: filters (`period`, `scope`, `eBike`), `entries`, `viewerEntry`, `isLoading`, `loadingMore`, `error`, `endReached`.
  - Events: filter changes (each reloads from the first page), `LoadMore`, `Retry`, `OpenActivity(uri)`, `Back`.
  - The e-bike filter is shown only for `ride`.
  - The viewer's entry is pinned below the list when it isn't in the loaded entries.
  - The presenter test uses the existing `TestSegments` fixtures.
- **`SegmentScreen`:** gains `OpenLeaderboard`, offered only when the status is `active`, or absent, which clients treat as active.
- **`EffortRow`:** draws a rank chip ("1st"…"10th", localized ordinals in strings.xml), and for the owner a line naming the `notRanked` reason.
- **Editor:**
  - `EditActivityScreen` state gains `leaderboardExclusion: Boolean?`, null when it isn't offered.
  - The presenter loads it through `SegmentTrackStore` when the activity is own and matching is on.
  - Save calls `setExcluded` only when the value changed, beside `ActivityManager.edit`, and both go through the outbox.
- **Consent prompt:** the copy in the prompt and in Settings adds the leaderboard sentence.

## Risks / Trade-offs

- **[An athlete's absence from a board can hint that they opted out or excluded a ride]** → Accepted. The choice itself is never served. A viewer only sees a Public effort that isn't on the board, which they could already see on the activity.
- **[Board query cost on popular segments]** → It groups every visible effort on one segment, using `segment_efforts_board_idx`. `visibleTo` per row is the main cost, as in `getSegmentEfforts`. If a segment grows past thousands of athletes, a per-tier pre-aggregate is the next step, kept out of scope here.
- **[Periods use the AppView's UTC date against the effort's local date]** → An effort near midnight on the 1st can count in the neighbouring month for a few hours. The spec states it, so it's deliberate and testable.
- **[Matching off forgets exclusions]** → The tracks that hold them are deleted. The spec states it, and the editor shows the current state when matching is back on.
- **[Speed caps don't stop a dishonest client from writing a slower fake track]** → The caps catch cars and GPS jumps, not deliberate forgery. Since `segment_tracks` stays private, reporting and moderation of leaderboards can come later.
- **[Toggling an exclusion rewrites a record that may also be queued]** → `setExcluded` refuses when a put for that rkey is still in the outbox, and the editor then hides the toggle. A later sweep keeps whatever flag is stored.

## Migration Plan

- **Additive:** optional lexicon fields, one query, migration `V31`, no backfill. Existing prefs and tracks read as "appear" and "not excluded".
- **Order:** deploy the AppView first. Clients ignore `rank` and `notRanked` until updated, and older clients writing `segmentPrefs` drop `hideFromLeaderboards` (decision 3 can't prevent that). An athlete who hid themselves and then saves matching from an old client reappears. That's acceptable for a pre-release app, and the release notes say so.
- **Rollback:** remove the route. The columns stay unread.
