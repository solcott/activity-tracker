# Design

## Context

See proposal.md for why. Today:

- **Climb rule.** `SegmentClimb.of(gain, loss, length)` and `SegmentDirection` live in `core/location` (`SegmentDirection.kt`). The apps call them from `SegmentGroupRow.wayOf` and `GroupSwitcher.of` with a view's served `elevationGain`/`elevationLoss`.
- **Served elevation.** `servedElevation` gives terrain where the AppView has tiles (`TERRAIN_TILES_DIR`), else effort elevation once enough athletes have efforts. On the local stack no tiles exist, so every Rush segment's terrain gain and loss is null and labels fall back to compass. `SegmentMatcher.backfillTerrain` queues elevation jobs on start for any segment without terrain, so tiles fix served elevation on the next AppView start.
- **Importer.** `CandidateCutter.climbAlong(trail, piece, reversed)` judges a piece's own climb from terrain (`climbByTerrain` in `Extract.kt`) and uses it only to name whole two-way trails. `Candidate.oneWay` already travels file → stage (`segment_candidates.one_way`, V66) → check → `publish`/`refine`.
- **Paths.** `pathViews` in `api/SegmentViews.kt` reads `PathSegments ⋈ Segments` for sport and group key only. Trail rows (`SegmentBrowseTexts.joined(BrowseRow, …)`) and `PathTexts.summary` show kind, length, sports and count.

## Goals / Non-Goals

**Goals:**
- One climb per importer segment that already accounts for its trail, so a flat piece of Rush and its steep neighbours read the same.
- A path's one-way wording computed once, on the AppView, with the same rule the apps use for segment rows.
- Imported regions always have terrain.

**Non-Goals:**
- No climb for athletes' segments, and no UI to set one.
- No new strings: path wording reuses `segment_one_way_*` and `segment_sports_one_way_*`.
- No "both ways" wording on trail rows; only one-way sport groups are named.
- No change to the climb thresholds.

## Decisions

### 1. Climb is a record field set by the importer, gated like `oneWay`
`feed.segment#climb` (string, knownValues `uphill`, `downhill`) and `segmentView.climb`, served only when the author is the importer (same check as `oneWay` in `SegmentViews.kt`). Unknown values are treated as absent on read.

- **Why a record field:** only the importer knows the trail a piece belongs to. The AppView knows the path, but paths fold branches and direction of travel is per segment, so a path-level terrain sample can't say which way a piece goes.
- **Alternative:** the AppView samples each path's terrain and infers pieces' climbs. Rejected for the reason above, and it would need terrain at serving time, which is exactly what was missing.

### 2. The importer prefers a piece's own clear climb, then its trail's
In `CandidateCutter`, judge each trail once over `trail.points` (memoised per trail) and each piece as today. A candidate's climb = piece climb along travel ?: trail climb along travel ?: null; none for `alpine`. "Along travel" reverses for `reversed` candidates. Junction spans and parts use their parent trail's line (the `Trail` they're cut from), so every span of a Rush parent trail inherits that parent's climb.

- **Why own first:** a piece that clearly climbs against its trail would otherwise be mislabelled. A flat piece is the only case that inherits.
- **Then the routes through it:** a section named apart from the rest of its system is a trail of its own (Rush's "Through the Trees" way), so a gentle one gets no trail climb. After cutting, `RouteClimbs` gives a one-way candidate still without a climb the climb of the longer one-way candidates of its sport group that follow all its OSM ways in its order, when they all agree. It never lowers the 3% bar, so a rolling one-way trail with no descending route through it keeps its compass name.
- **Alternative:** a lower grade for one-way trails. Rejected: any long, gently falling one-way trail would read "Downhill only".
- Two-way whole-trail naming keeps using the piece climb (it *is* the trail), so names don't change.

### 3. Candidates carry the climb through stage, check, publish and refine
`Candidate.climb: String?` in the candidates file; `importer.defs#candidate.climb`; Flyway `V72__segment_candidate_climb.sql` adds nullable `climb varchar(16)` to `segment_candidates`; staging stores it (dropping unknown values), checks return it. `publish` copies it into the record. `refine` compares and writes it in the same write as line, name and `oneWay`, removing it when the candidate has none.

### 4. Apps take the served climb first
A small helper in `feature/segment/api` — `SegmentView.climb(): SegmentClimb?` = parse served `climb` ?: `SegmentClimb.of(elevationGain, elevationLoss, distanceMeters)` — replaces the direct `SegmentClimb.of` calls in `SegmentGroupRow.wayOf` and `GroupSwitcher.of`. Reversal for the other direction stays as `GroupSwitcher` does it today.

### 5. `pathView.oneWays` is computed on the AppView
New `feed.defs#pathOneWay { sport, direction }` (`direction` knownValues: the `SegmentDirection` words) and `pathView.oneWays: [pathOneWay]`, omitted when empty. In `pathViews`, widen the `covered` select to sport, group key, author, record JSON (for `oneWay`/`climb`), line, distance and served elevation. Per sport group (not alpine): when every listed segment is served one-way, take the longest and compute `SegmentDirection.of(heading, climb)` where climb = record climb ?: `SegmentClimb.of(served gain, loss, length)`. The AppView already depends on `:core-location`.

- **Why the longest:** it is normally the whole-trail segment, so its line and climb speak for the trail; with inherited climbs, pieces agree with it anyway.
- **Alternative:** client-side from `BrowseRow.Path.groups`. Rejected: name-search rows have no groups.

### 6. Trail lines reuse the segment row wording
Factor the per-sport wording in `SegmentBrowseTexts` so both group rows and path rows build "sports · wording" from a list of (sport, direction?) pairs: when there is one sport group, or every sport group shares one direction, the sports then `segment_one_way_<word>`; otherwise plain sports first, then `segment_sports_one_way_<word>` per direction. A path row narrowed to one sport uses only that sport's entry. `PathTexts.summary` uses the same builder. iOS draws these `AppText`s, so it follows without Swift text changes; check `PathPage.swift` and the browse row for any hand-built line.

### 7. Presets fetch terrain
`scripts/segment-fetch.sh <region>` ends by running `scripts/terrain-fetch.sh <region>` (which already reads the preset's BBOX and skips tiles on disk), so `segment-import.sh <region>` finds `build/terrain/12` and passes `--terrain`. The AppView's local-stack env already points `TERRAIN_TILES_DIR` at `build/terrain` when it exists.

## Risks / Trade-offs

- **[Utah at zoom 12 is several thousand tiles]** → `terrain-fetch.sh` resumes and skips existing tiles; the download is one-off per region.
- **[Old clients ignore `climb` and `oneWays`]** → They keep today's wording; no breakage.
- **[A branch family's parent trails disagree]** → Each span inherits from the parent trail it was cut from; spans of the same branch share it.
- **[Record climb and served terrain disagree on a borderline piece]** → The record wins only when the piece itself isn't clear; the importer and AppView sample the same tiles with the same smoothing, so a clear piece agrees.

## Migration Plan

1. Deploy the AppView (V67 adds the column; views serve `climb` and `oneWays`).
2. Per region: `scripts/segment-fetch.sh <region>` (now fetches terrain), restart the AppView so `backfillTerrain` samples segments, then `scripts/segment-import.sh <region> --all`, whose `refine` writes `climb`, `oneWay` and climb names.
3. Ship the clients.
