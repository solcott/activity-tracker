# Proposal

## Why

Segment elevation shipped with raw terrain samples and efforts winning once enough athletes ride a segment. We checked six Corner Canyon (Draper, UT) trails against USGS 3DEP 1 m lidar. Raw Terrarium z12 terrain overstates climbing on rolling and contouring trails by up to 40%: Longview gives 266 m up and 46 m down, where lidar gives 223 m up and 6 m down. Higher Terrarium zooms (z14, z15) don't help, because the US source is the same 10 m NED data enlarged. Smoothing the samples over 50 m before the existing 3 m threshold brings z12 within about 5% of lidar on every trail. With terrain that good, a median of phone-GPS effort altitudes is the less accurate source. Athletes also expect what other trail apps show: an elevation profile of the segment, tinted by grade, which the derived totals alone can't give.

## What Changes

- Terrain heights sampled along a segment's line are smoothed over 50 m before gain and loss are counted. Effort altitude keeps the 3 m threshold alone.
- **BREAKING (served values)**: where terrain covers a segment's whole line, its terrain elevation is served, whatever its efforts show. Effort elevation now only fills in for segments terrain doesn't cover. `elevationSource` keeps its two values. A segment's served numbers may change once on upgrade.
- The AppView keeps a terrain elevation profile for each covered segment: 128 heights evenly spaced along the line, start to end, in decimeters. Only `feed.getSegment` serves it, as an optional `elevationProfile` beside the segment, and only when the segment's elevation comes from terrain. Search, name search and duplicate checks don't carry it.
- A one-time backfill recomputes every segment's terrain elevation with smoothing and gives covered segments a profile.
- The segment screen draws the profile under the gain, loss and grade line: a distance axis, elevation in the viewer's units, a scrub readout of distance, elevation and grade, and a fill tinted by grade in five bands. Runs, alpine runs and lifts all show it.
- Not in scope: other elevation sources (lidar, Copernicus), a profile from efforts, and a map marker that follows the scrub.

## Capabilities

### New Capabilities
<!-- None: the profile belongs to segment elevation. -->

### Modified Capabilities
- `segments/segment-elevation`: terrain is smoothed before counting, and wins wherever it covers a segment, with efforts only as the fallback. `getSegment` serves a terrain profile. Existing segments are recomputed. The segment screen draws the profile.

## Impact

- **Lexicon**: `feed.getSegment` output gains an optional `elevationProfile` object (`heights`, integer decimeters). `feed.defs#segmentView` is unchanged apart from descriptions. The `core/lexicon` mirror and `Queries.kt` change to match.
- **Shared geometry**: `core/location` gains distance-based smoothing and even resampling of a height series, next to `ElevationGain`.
- **AppView**: `TerrainTiles` (smoothing, profile), the matcher's `elevation` job (stores the profile), `servedElevation` (terrain first), `SegmentViews.segment` (serves the profile), `Tables.kt`, and a Flyway migration adding the profile column and queuing an `elevation` job for every segment.
- **Client**: `SegmentRepository.segment` returns the profile with the view, and the `SegmentState` contract gains it. `SegmentUi` draws the chart. `:ui-designsystem` charts gain a grade-tinted fill and grade band colours (light and dark). `:ui-resources` gets strings. `:app-fixtures` gets samples, and screenshot references are updated.
- **Data and privacy**: the profile is pure terrain from the public line, with no athlete data. Effort elevation and its privacy limits are unchanged.
