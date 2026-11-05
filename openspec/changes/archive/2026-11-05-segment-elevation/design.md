# Design

## Context

See proposal.md (Why) for motivation and `specs/segments/segment-elevation/spec.md` for the requirements.

The current state that shapes the approach:
- `segmentTrackPieces()` (`SegmentMatcher.kt`) decodes each segment track's blob into `TimedPoint(lat, lng, t)` from `core/location`, and it drops the piece's `alt` (centimeters, nullable samples).
- Passes are found per piece by `SegmentGeometry` and stored as `segment_efforts` rows. `replaceEfforts` rewrites a track's rows, and `dropSegmentUse` deletes them.
- Segment indexing (`SegmentIndex.upsertSegments`) runs inside the Jetstream indexer transaction. It enqueues a `segment` job on `segment_match_jobs`, which the matcher loop drains. Job kinds are short strings (`SegmentJobs`).
- `SEGMENT_ACTIVE_ATHLETES` (default 3) already defines how many distinct athletes' efforts are enough to reveal a status.
- Precedent for a full rematch is V43, a migration that enqueues a `track` job for every segment track.

## Goals / Non-Goals

**Goals:**
- One gain/loss algorithm, used for both terrain and effort heights.
- No network calls at index or match time. Terrain is read from local files.
- Elevation work never slows the Jetstream indexer transaction.

**Non-Goals:**
- An elevation profile chart (possible later from the same terrain samples).
- Showing an effort's own climb on the effort, the leaderboard or the activity page.
- Elevation for staged candidates, dive sites or activities. Activities keep their own `elevationGain` and `elevationLoss`.
- Global terrain coverage by default. Operators fetch regions they serve.

## Decisions

### Terrain source: Terrarium PNG tiles from AWS Open Data
Use the Mapzen/Tilezen "terrarium" elevation tiles (`s3://elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`, public and free). They are stored on disk in the same `{z}/{x}/{y}.png` layout under `TERRAIN_TILES_DIR`, at one zoom (`TERRAIN_TILES_ZOOM`, default 12, about 28 m per pixel at 45°). A height decodes as `R*256 + G + B/256 - 32768` meters and is read with `javax.imageio`. Samples use bilinear interpolation.
- *Instead of Copernicus GLO-30 GeoTIFF:* that needs a GeoTIFF/COG reader (GeoTools is heavy, and the JDK's TIFF plugin is uncertain on Float32 deflate tiles). Terrarium blends SRTM, NED (10 m in the US), ETOPO and others, and PNG decoding is already in the JDK.
- *Instead of an external API (Open-Meteo, OpenTopoData):* that would add a runtime dependency and rate limits, and it sends lines out. The lines are public, but the dependency isn't wanted.
- `scripts/terrain-fetch.sh <region|bbox> [--zoom N]` computes the tile range for a bbox, or for a region preset's bbox under `scripts/segment-regions/`, and downloads the missing tiles with `curl`. It is idempotent and resumable.
- Attribution: the README lists the terrain tile sources, as Tilezen's terms require.

### Terrain sampling and coverage
Resample the decoded segment line every 10 m along its length, including both ends, and look up each sample's height. If any sample's tile is missing, the segment has no terrain elevation; the spec calls this "fully covered". Tiles are cached in a small LRU (decoded `ShortArray`s or `IntArray`s) inside a `TerrainTiles` class, which is null when `TERRAIN_TILES_DIR` is unset.

### Gain/loss: hysteresis with a 3 m threshold
`ElevationGain.of(heights: List<Double?>, thresholdM = 3.0): GainLoss?` lives in `core/location` next to `SegmentGeometry`, so a client can reuse it later. It skips nulls and returns null when fewer than two samples remain. It tracks a reference height and moves it only when a sample departs from it by at least the threshold, adding the departure to gain or loss. The result is rounded to whole meters.
- *Instead of a moving average:* a moving average is sensitive to the sampling rate, which differs between 10 m terrain samples and 1 s GPS samples. Hysteresis behaves the same for both and is what the spec states.

### Terrain work runs as an `elevation` job
`upsertSegments` enqueues a new `SegmentJobs.ELEVATION` job alongside the `segment` job, but only when the line changed or the segment is new. The matcher's `elevation` job reads the segment's line, samples the terrain and writes `segments.terrain_gain_m` / `terrain_loss_m`, which are null when not covered. A segment with no tiles configured gets no job.
- *Instead of running it in the indexer transaction:* tile I/O for a 50 km line (5,000 samples) doesn't belong in the Jetstream path.
- *Instead of folding it into the `segment` job:* that job also re-enqueues tracks in the segment's cells. Backfilling elevation through it would rematch everything twice.

### Effort elevation: alt in `TimedPoint`, per effort, then aggregated
- `TimedPoint` gains `val altM: Double? = null`. The default keeps clients and other callers source-compatible. `segmentTrackPieces()` fills it from `alt / 100` when the piece has `alt` with the same length, and treats a length mismatch as a malformed track, as with the other arrays. Missing samples stay null.
- For each pass, the matcher takes the piece's points with `t` in `[pass.start, pass.end]`, plus the interpolated altitudes at both ends when samples exist on each side, and runs `ElevationGain.of`. It writes `segment_efforts.elevation_gain_m` / `elevation_loss_m`, both null when there is no altitude.
- After `replaceEfforts` or `dropSegmentUse` changes a track's efforts, the matcher recomputes the aggregate for every segment whose effort set changed (old ∪ new segment URIs), in the same transaction. For each athlete it takes the median of their efforts with elevation, then the median across athletes. It stores `segments.effort_gain_m`, `effort_loss_m` and `effort_athletes` (the count). The work is bounded by one segment's efforts and goes through the existing `segment_efforts_segment_idx`.
- *Instead of storing only the served value:* keeping `effort_athletes` lets the view apply the current `SEGMENT_ACTIVE_ATHLETES` at read time, so changing the setting needs no recompute.

### Serving
`SegmentViews` builds every `segmentView` from the `segments` row. When `effort_athletes >= segmentActiveAthletes` and the effort values aren't null, it serves the effort values with source `efforts`. Otherwise it serves the terrain values with source `terrain` when they're not null, and otherwise nothing. Effort rows' elevation columns are never selected into any effort view.

### Lexicon
`feed.defs#segmentView` gains `elevationGain` and `elevationLoss` (integer, minimum 0, meters) and `elevationSource` (string, `knownValues: ["terrain", "efforts"]`, open enum). `core/lexicon` mirrors them as nullable fields, with the source as an open-enum value class like `SegmentStatus`. `feed.segment` is unchanged. The indexer's record re-encoding already drops unknown fields.

### Client
`SegmentUi` adds a second stats line under the distance/type row when `elevationGain` isn't null. It shows gain (up arrow), loss (down arrow) via `Format.elevation`, and grade as `(gain - loss) / distanceMeters` to one decimal place, with new strings in `:ui-resources`. `:app-fixtures` gets a sample with elevation and keeps one without. Screenshot references are updated.

### Backfill
Migration V53 adds the columns: `segments.terrain_gain_m`, `terrain_loss_m`, `effort_gain_m`, `effort_loss_m` (BIGINT, nullable), `effort_athletes` (INT NOT NULL DEFAULT 0), and `segment_efforts.elevation_gain_m` / `elevation_loss_m` (BIGINT, nullable). It also enqueues a `track` job for every segment track, as V43 did, so efforts get elevation and the aggregates are recomputed. At startup, when terrain tiles are configured, the matcher enqueues an `elevation` job for each segment whose `terrain_gain_m` is null. Covered segments only need doing once, and uncovered ones are re-checked each start, which is cheap: one tile lookup per sample until the first missing tile.

## Risks / Trade-offs

- [30 m terrain underestimates short, steep or forested segments, and bridges and tunnels mislead it] → Effort values replace terrain once enough athletes ride a segment. The source is served, so a client could label it "est." later.
- [GPS altitude without a barometer is noisy] → The 3 m hysteresis, then a median of per-athlete medians, which also keeps one prolific athlete from dominating.
- [Privacy: a public value derived from private tracks] → It is revealed only at the same distinct-athlete threshold as status, is an aggregate of terrain shape rather than of athletes, and no per-effort value is served (spec: No single athlete's elevation is revealed).
- [The V53 full rematch is load on large deployments] → It runs through the existing job queue at its normal batch rate, with the same precedent as V43.
- [Tile disk usage: z12 is about 16.7M tiles worldwide, roughly 100 KB each] → Fetching is per region. A US state is a few thousand tiles (hundreds of MB).
- [Ski lifts: a lift's line climbs, but riders' GPS on chairs is good] → No special case. A lift gets gain and loss like any segment.

## Migration Plan

1. Deploy the AppView with V53. Efforts gain elevation as the rematch drains, and segments show effort values once enough athletes are recomputed.
2. Optionally run `scripts/terrain-fetch.sh <region>`, set `TERRAIN_TILES_DIR` and restart. The startup backfill fills terrain values.
3. Ship clients. Older clients ignore the new optional fields.

Rollback: the fields are optional and additive. Unset `TERRAIN_TILES_DIR` to stop terrain work. Reverting the AppView leaves unused nullable columns.
