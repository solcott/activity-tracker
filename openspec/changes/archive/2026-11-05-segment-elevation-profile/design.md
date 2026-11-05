# Design

## Context

See proposal.md (Why) for the motivation and `specs/segments/segment-elevation/spec.md` for the requirements. The archived `2026-11-05-segment-elevation` design describes the pipeline this change builds on.

Current state:
- `TerrainTiles.elevationOf(line)` resamples the decoded line every 10 m (`SegmentGeometry.resample`), looks up each height bilinearly, returns null when any tile is missing, and passes the heights straight to `ElevationGain.of` (3 m hysteresis, `core/location`).
- The matcher's `elevation` job (`SegmentMatcher.sampleTerrain`) writes `segments.terrain_gain_m` / `terrain_loss_m`, guarded on the line still being the one it sampled. At startup it queues an `elevation` job for each segment whose `terrain_gain_m` is null.
- `servedElevation(row, activeAthletes)` (`segments/SegmentElevation.kt`) returns the effort values when `effort_athletes >= activeAthletes`, then terrain, then none. Every `segmentView` builder uses it.
- `feed.getSegment`'s output is `{ segment }`. `SegmentRepository.segment(uri)` returns only `.segment`, and `SegmentState.content` holds a `SegmentView?`.
- `TimeSeriesChart` (`:ui-designsystem`) already takes a non-time x axis (`xLabelsAt`), a scrub readout (`markerLabel`), and draws its fill as an `AreaUnderlay` decoration in one colour.
- The highest Flyway migration is V53 (V52 was never used), so this change adds V54.

The spike that set the numbers below sampled six Corner Canyon trails against USGS 3DEP 1 m lidar. Its scratch script isn't part of the change.

## Goals / Non-Goals

**Goals:**
- One set of smoothed heights per segment feeds its gain, its loss and its profile, so the chart and the numbers above it always agree.
- The profile adds nothing to list-sized responses.
- Nothing about efforts or their privacy changes except which source wins.

**Non-Goals:**
- Recomputing totals on the client from the 128 profile points. A 128-point resample of Longview counts 227 m against the 235 m full-resolution total.
- Cleverer smoothing (Savitzky–Golay, a threshold that depends on resolution). The plain moving average met the 5% target.
- Library swaps for resampling (spatial-k turf), terrain formats (GeoTIFF readers) and smoothing helpers. A separate spike (`openspec/explorations/elevation-library-spikes.md` on its own branch) found none worth adopting here.
- A profile for staged candidates or dive sites.

## Decisions

### Smoothing: a centred 50 m moving average, narrowed at the ends
`core/location` gets `ElevationProfile.smooth(heights, spacingM = 10.0, halfWidthM = 25.0)`. Each sample becomes the mean of the samples within `halfWidthM` either side: two samples each way at 10 m spacing. Near either end the window narrows evenly to what's available on both sides, so the first and last samples keep their own heights and a straight climb comes through unchanged. A plain truncated window would pull each end towards its neighbours: on a 10% climb that loses about 1 m at each end, and Spring Hollow lost 4 m. `TerrainTiles.elevationOf` runs smoothing, then `ElevationGain.of`. Effort elevation (`passElevation`) is unchanged.
- *Instead of 100 m:* it flattened real short climbs (Ann's Trail fell from 31 m up to 14 m, against lidar's 31 m).
- *Instead of a higher Terrarium zoom:* z14 and z15 were no better than z12, and sometimes worse (Longview z15 gave 299 m up, against lidar's 223 m).
- *Instead of a larger hysteresis threshold:* 5 m without smoothing still left z12 10–25% high, and it would also change effort counting.

### One terrain pass returns totals and profile
`TerrainTiles.elevationOf` becomes `terrainOf(line): TerrainElevation?`, holding `gain`, `loss` and `profileDm: List<Int>`. That's all three from the same smoothed series, or null when any sample isn't covered. The profile is 128 points at fractions `j / 127` of the resampled length, linearly interpolated between the smoothed 10 m samples and rounded to decimeters. Interpolation and rounding live in `core/location` (`ElevationProfile.even(heights, atM, count)`, given each height's distance along the line, since resampling leaves a shorter last stretch), so a client could reuse them later. While `TerrainTiles` is being changed, its hand-written `LinkedHashMap` cache of decoded tiles becomes a Caffeine cache (already an AppView dependency) with the same size limit. A tile that is missing or doesn't decode is cached as missing, as now.
- *Instead of Douglas–Peucker:* uneven spacing would need x values on the wire and in the chart. Evenly spaced points need only their count, and 128 kept the shape within 2 m on a 5 km trail.
- *Instead of 256 points:* twice the bytes, and a phone chart is about 350 dp wide, so the extra points don't show.

### Storage: a text column holding the heights
Migration V54 adds `segments.terrain_profile` (`TEXT`, nullable): the heights as comma-separated integers, about 900 bytes. H2 and Postgres both store it the same way, and nothing queries inside it. `sampleTerrain` writes it alongside the gain and loss under the same `line eq` guard, and nulls all three when the segment isn't covered.
- *Instead of a Postgres array or `jsonb`:* neither is H2-portable in the way the rules ask.
- *Instead of `BYTEA` with delta encoding:* harder to inspect, for a saving that doesn't matter at this size.

### Serving: terrain first, profile only in getSegment
- `servedElevation` checks terrain first and uses efforts only when terrain is null and `effort_athletes >= activeAthletes`. Nothing else changes in the effort aggregation.
- `SegmentViews.segment` reads `terrain_profile` and sets `GetSegment.Output.elevationProfile` only when the served source is `terrain`. With terrain first, the profile is present exactly when terrain values are.
- Lexicon: `feed.getSegment` output gains an optional `elevationProfile` ref to a new `feed.defs#elevationProfile` object, `{ heights: integer[] (minLength 2, maxLength 512) }`, decimeters, evenly spaced from the line's start to its end. The x spacing comes from `distanceMeters / (n - 1)`. The max length leaves room to change the point count without changing the lexicon. `segmentView` descriptions are updated for the new source order.
- *Instead of a field on `segmentView`:* search returns up to a page of segments, and 900 bytes each is wasted on lists.
- *Instead of a separate `getSegmentProfile` query:* it costs a second round trip on every segment open, for data that's always wanted there.

### Backfill: queue an elevation job for every segment
The migration also runs `INSERT INTO segment_match_jobs ... SELECT 'elevation', uri ... FROM segments WHERE NOT EXISTS (...)`, the same pattern as V43 and V53 with tracks. Startup's "queue where `terrain_gain_m` is null" stays as it is for newly configured tiles. On an AppView without tiles the jobs are no-ops: `sampleTerrain` returns early when `terrain` is null.

### Client: profile through the repository and state
- `SegmentRepository.segment(uri)` returns the whole `GetSegment.Output`. Callers that only want the view (`segmentLookup`) take `.segment`.
- The `SegmentState` contract gains `profile: SegmentProfile?`, a stable `-api` type holding `heightsM: ImmutableList<Double>` and `distanceMeters`, which the presenter maps from the output. The UI never sees lexicon decimeters.
- `SegmentUi` adds a `SegmentProfileChart` item after the stats column. It builds `SeriesPoint`s with x = distance in the viewer's unit. `xLabelsAt` uses whole or half miles or km, chosen by length. `yLabel` uses `Format.elevation`. `markerLabel` reads distance · elevation · grade, where grade is the slope between the points either side, about ±25 m.

### Grade tint: a hard-stop gradient brush through Vico's own fill
- `:ui-designsystem` gets `GradeBands`: the thresholds 3, 6, 10 and 15%, and five colours defined in the theme with light and dark values (green, yellow-green, yellow, orange, red), checked for contrast against the surface in both themes. It also builds the brush: `GradeBands.brush(xFractions, bands, colors)` returns a `Brush.horizontalGradient` whose colour stops repeat at every band change, so the colour switches sharply instead of blending.
- `TimeSeriesChart` gets an optional `areaBrush: Brush?`. When it's set, the line's area fill and stroke use `Fill(brush)`. Vico 2.5.2 applies a fill's brush across the layer's bounds (`AreaFills.kt`), and a profile spans the whole fixed x range, so stop fractions match x positions. Without the brush, drawing is unchanged.
- `AreaUnderlay` is left alone. It only draws fills for lines with gaps, and a profile has none. With a brush set and gaps present, the chart falls back to its plain fill.
- Each point's grade is computed over about ±25 m, the absolute value of (height ahead − height behind) / distance, then mapped to a band. The presenter computes the list once, and the UI only resolves band index to colour and builds the brush.
- *Instead of per-point colours in `AreaUnderlay` (one trapezoid per pair):* that means our own drawing code for something Vico's fill already supports.
- *Instead of a smooth gradient:* hard stops between bands read as bands, which is what the spec asks for. A smooth gradient would blur short steep pitches into their neighbours.

## Risks / Trade-offs

- [Served values change once on upgrade, including segments whose efforts served before] → Expected and called out in the proposal. Leaderboards and efforts are unaffected, because elevation isn't part of matching.
- [Smoothing over 50 m understates genuinely short, punchy features, such as a 15 m-long 6 m step] → These are below what 30 m terrain can resolve anyway. The spike's ripple-heavy trails stayed within 5% of lidar.
- [The spike covered one region with good 10 m NED data] → Other regions may differ. Wiring this up needs no tuning, and the parameters are constants in `core/location`, easy to revisit.
- [Effort elevation work now rarely shows where tiles exist] → It still pays for uncovered regions and keeps operators' choice of not fetching tiles. The cost is unchanged.
- [Profiles add about 900 bytes per covered segment to the database] → 100k segments is about 90 MB. That's acceptable, and it's text that compresses well in Postgres TOAST.
- [Per-band trapezoids cost more to draw than one path] → At most 127 rects per chart, drawn only on resize. That's negligible beside Vico's line.

## Migration Plan

1. Deploy the AppView with the new migration. The queued `elevation` jobs drain through the matcher at its normal batch rate, recomputing smoothed terrain and writing profiles. Until then, a segment serves its old terrain value, or effort value if efforts served before.
2. Ship clients. Older clients ignore `elevationProfile`, and they just see slightly different gain and loss numbers.

Rollback: the column and the lexicon field are optional and additive. Reverting the AppView restores efforts-first serving, and the stored totals stay smoothed, which is harmless.
