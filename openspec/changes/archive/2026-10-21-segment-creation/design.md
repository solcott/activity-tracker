# Design

## Context

See proposal.md for the motivation and the specs for the behavior. What `segments-core` left in place:

- **Indexing:** `upsertSegment` (`index/SegmentRecordIndex.kt`) validates a `feed.segment`, upserts `segments` (setting `indexed_at = now` on every write), rewrites `segment_cells`, and on a new line or sport deletes the efforts and enqueues a `segment` job. `deleteSegments` removes the segments, their cells and their efforts.
- **Matching:** `SegmentMatcher` (`segments/SegmentMatcher.kt`) drains `segment_match_jobs`. `replaceEfforts` finds candidates by `segment_cells` and sport group, runs `SegmentGeometry.passes`, and replaces all of the activity's efforts in one transaction. `matchSegment` enqueues `track` jobs for usable tracks sharing a cell.
- **Serving:** `SegmentViews` (`api/SegmentViews.kt`) builds `segmentView`/`segmentViewBasic`. `search` filters by bbox columns and cells, keyset on `(indexed_at, uri)`. Procedures are served by the typed `post` helper in `api/Routes.kt`.
- **Geometry:** `SegmentGeometry` (`core/location`) has `decodePolyline`/`encodePolyline` (precision 6), `lengthMeters`, `resample` and `passes`. `SegmentCells.cellsOf(line)` gives a line's cells with a 30 m margin.
- **Client:** `SegmentRepository` wraps the four queries; `SegmentScreen` and `ActivitySegments` show segments and efforts. The owner's full track is `ActivityRepository.ownRoute`, and `privacySettings()` gives the zones last read this session (null while unreadable). `PrivacySettings.pieces(route, combined)` cuts a route the same way segment tracks are cut. `ActivityMap` draws a precision-5 polyline; it has no way to highlight part of it. There's no slider in `ui/adaptive`.
- **Sport groups:** `SegmentSport.of(sport, subSport)` (`core/lexicon`).

## Goals / Non-Goals

**Goals:**
- One duplicate test, used both by `checkSegment` and on indexing, so a check that says "no duplicate" agrees with what indexing will do. The only difference is a segment indexed between the two.
- Status that is deterministic from indexed data and stable across edits: "earliest" means first indexed, never last written.
- A creation UI that works on every platform, the web included, which has no map yet.

**Non-Goals:**
- Leaderboards, or using status beyond search and matching (`segment-leaderboards`).
- Editing a segment's line in the app, or deleting one. A creator can still do both from another client, and indexing handles it.
- Picking start and end by tapping the map.
- Merging duplicates or moving efforts between segments.
- Reviving an archived segment.

## Decisions

### 1. Status columns on `segments`, derived only by the AppView
`V30__segment_status.sql` adds `status VARCHAR(16) NOT NULL DEFAULT 'proposed'`, `duplicate_of VARCHAR(512)` and `first_indexed_at BIGINT`, backfilled from `indexed_at`, then made `NOT NULL`. It also adds an index on `(status, sport)` and one on `duplicate_of`.
- **`first_indexed_at`** is set on insert and never updated, so an edit doesn't reorder who came first. `indexed_at` keeps meaning the last write.
- **Existing segments** (seeded locally) start `proposed`. The first promotion pass (decision 4) promotes any already ridden by enough athletes.
- **Lexicon:** `segmentView` gains optional `status` (`knownValues`: proposed, active, duplicate, archived) and `duplicateOf` (at-uri). `segmentViewBasic` gains optional `status`. A client treats an absent or unknown status as `active`.
- **Rejected: status in the record.** The AppView never writes records, and a creator could set any status they liked.

### 2. The duplicate test: prefilters, then discrete Fréchet
`SegmentGeometry.duplicates(a: List<GeoPoint>, b: List<GeoPoint>): Boolean`, in `core/location` beside `passes`:
1. **Length:** the longer of the two at most 1.2 × the shorter.
2. **Endpoints:** start to start and end to end each within `DUPLICATE_DISTANCE_M` (30 m). This alone rejects the opposite direction.
3. **Discrete Fréchet** on both lines resampled every 10 m (`resample`), using the iterative dynamic-programming form with early exit once a cell's coupling exceeds 30 m. It's restricted to a band around the diagonal (by distance along each line), so a 50 km pair costs roughly 5,000 × band rather than 5,000².
- **Why Fréchet:** it respects order, so a line that doubles back or skips a loop doesn't pass, which Hausdorff would allow.
- **Why 30 m:** it's the matcher's `CORRIDOR_M`. Two lines that pass this would match each other's efforts anyway.
- **Rejected: running it on the client too.** The client only needs the answer, and one implementation avoids drift. It lives in `core/location` because the tests and the AppView share it and OSM import will reuse it.

### 3. Duplicates decided inline in the indexer
In `upsertSegment`, after validation and the cell rewrite, for a new segment or one whose line or sport changed:
- Candidates are the `proposed`/`active` segments of the same sport sharing a cell with the line's **start** point's cells and **end** point's cells. Both ends must lie within 30 m, so this is sufficient and small. Each candidate also has `first_indexed_at` before this segment's (ties broken by URI) and isn't this segment.
- The earliest candidate that `duplicates` sets `status = duplicate, duplicate_of = it`, and the segment's efforts are deleted. Otherwise the segment is `proposed`, or stays `active` when it was already active and only its name changed. A changed line on an `active` segment makes it `proposed` again, since its riders were counted on another line.
- **Re-check:** when a segment is deleted, archived, or its line or sport changes, every segment with `duplicate_of` = it is re-checked in `first_indexed_at` order, in the same transaction, by the same rule. One that comes out `proposed` gets a `segment` job, so past tracks are matched.
- **Why inline:** the prefilter leaves few candidates, and Fréchet over them is milliseconds. A segment is never served without a status, and replays are deterministic, since order is by `first_indexed_at`.
- **Rejected: a `dedup` job kind.** A segment would be briefly `proposed` and matchable before it was checked, and the job would need ordering guarantees that the table doesn't give.

### 4. Promotion after matching; archiving on a timer
- **Promotion:** at the end of `replaceEfforts`, for each segment the activity now has efforts on that is still `proposed`, count `DISTINCT author_did` in `segment_efforts`. At or above `config.segmentActiveAthletes` (`SEGMENT_ACTIVE_ATHLETES`, default 3), set `active`. It's in the same transaction, so a status never lags its efforts.
- **Status pass:** `SegmentMatcher.start` also runs `updateStatuses(now)` once on start and then hourly. First it promotes every `proposed` segment already at the threshold, which covers segments from before this change and a lowered setting. Then it sets `archived` on `proposed` segments with `first_indexed_at` older than 180 days and re-checks their duplicates (decision 3). Efforts are kept.
- **Matching filter:** `replaceEfforts`'s candidate query adds `status IN ('proposed', 'active')`, and `matchSegment` skips a segment of any other status. Because `replaceEfforts` replaces all of an activity's efforts, a recompute drops its efforts on an archived segment, which is what the efforts spec asks.

### 5. The daily limit counts first indexings
In `upsertSegment`, a URI not yet in `segments` is refused (dropped like an invalid record, logged) when the author has `≥ 10` segments with `first_indexed_at > now - 24 h`. Existing URIs are never refused.
- Deleted segments leave no row, so deleting and re-creating doesn't consume the allowance. That's acceptable at this scale.
- `checkSegment` reports `remaining = 10 - that count`, floored at 0.
- **Rejected: counting by `createdAt`.** It comes from the client and can be backdated.

### 6. `feed.checkSegment` is a procedure
Input `{ sport, line }` (precision-6 polyline, ≤ 100 kB), output `{ duplicates: [segmentView], remaining: integer }`. It's a POST because a 50 km line doesn't fit in a query string. The viewer must be authenticated (`ServiceAuthAuthenticator`). It runs the same validation as indexing (minus the name), the same candidate query and `duplicates` test, and returns views of every matching candidate, earliest first, regardless of search's visibility rule. A proposed segment's line is already public in its creator's repo.

### 7. Search shows proposed segments to their creator and riders
`search` adds to its `where`:
`status = 'active' OR (status = 'proposed' AND (author_did = viewer OR EXISTS (efforts on it by viewer)))`, with no proposed segments at all for an anonymous viewer. The viewer's own efforts are always visible to them, so the `EXISTS` needs no tier check. Duplicate and archived never match.

### 8. Client: a `CreateSegmentScreen` with a range picker
- **Entry:** the owner's activity detail gains a `CreateSegment` event and a menu item, shown when the activity is the viewer's own, has a track (`ownRoute` non-null), has a sport group (`SegmentSport.of`), and `privacySettings()` has emitted non-null settings. It pushes `CreateSegmentScreen(activityUri)`.
- **Presenter:** loads the activity, `ownRoute` and the settings, and builds `pieces = settings.pieces(route, combined)` as segment tracks do. The selectable route is the pieces laid end to end by distance, with each gap marked.
  - State: the selection `start..end` in meters, its validity (`TooShort(min)`, `TooLong`, `CrossesGap`, `Ok`), the selected line at precision 5 for the map and at precision 6 for the record, a name `TextFieldState`, the public warning, and the check result.
  - `Publish` runs in `rememberSingleFlight`: `SegmentRepository.check(sport, line)`. If it finds duplicates, the state shows them (each opens `SegmentScreen`). If `remaining == 0`, it shows the limit. Otherwise it queues the record, then pops with a result the detail screen shows as a confirmation.
- **`derivedFrom`:** set with the activity's URI and CID only when `ActivityView.visibilityOrPublic` is Public and `DetailCategory.currentTiers(...)[MAP]` is Public.
- **UI:** `ActivityMap` gains an optional `highlight: String?` polyline drawn over the route in the accent colour. On the web, `RoutePreview` gains the same. Below it are a new `AppRangeSlider` wrapper (`ui/adaptive`: Material `RangeSlider` everywhere, tinted per `AppTheme`, added to `adaptive-ui.md`), the selection's distance and validity message, the name field, the warning and a Publish button.
- **Write:** `SegmentRepository.create(record)` queues a `feed.segment` `putRecord` in the repo through the outbox with a new TID, like other repo records.
- **Status display:** `ActivitySegmentsUi` shows a "Proposed"/"Archived" label beside non-active efforts. `SegmentUi` shows the status, and for a duplicate a row that opens `duplicateOf`.

## Risks / Trade-offs

- **[A race: two athletes create the same segment between check and index]** → Indexing decides by `first_indexed_at`, so the later one becomes `duplicate` and its creator sees that on its screen. It's rare, and the cost is one unused record.
- **[Status reveals that 3 athletes rode a segment, including on private activities]** → Only the status is served, never a count or who. With a threshold of 3, any one private effort is indistinguishable from the others. Noted in `.agents/rules/appview.md`.
- **[`checkSegment` lets anyone probe for proposed segments that search hides]** → Those lines are public records already. Search hiding them is about clutter, not secrecy.
- **[A creator edits a popular active segment's line]** → It goes back to `proposed`, and its efforts are recomputed against the new line by the existing `segment` job. That's honest, since the old riders didn't ride the new line, and it discourages hijacking.
- **[Fréchet cost on very long lines]** → It's banded with early exit and runs only after the length and endpoint prefilters, at most once per candidate sharing both endpoint cells. A unit test pins a 50 km pair under a time budget.
- **[The 24 h limit resets by deleting]** → Deletion removes the row. Acceptable for now; a tombstone count can come with abuse tooling.
- **[Archived segments still hold efforts that show as "Archived" on old activities]** → That's intended history. Leaderboards will ignore non-active segments.

## Migration Plan

- `V30__segment_status.sql` is additive: new columns with defaults, a backfill of `first_indexed_at` from `indexed_at`, and indexes.
- On first start, the status pass promotes existing segments that already have enough athletes, and archives none, since no segment is 180 days old yet.
- Rollback: older AppViews ignore the new columns. The lexicon fields are optional, so older clients ignore them, and the client hides creation when `checkSegment` answers `MethodNotImplemented`.
