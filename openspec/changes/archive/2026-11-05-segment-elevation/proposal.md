# Proposal

## Why

The segment screen shows a segment's length and nothing about how hilly it is. Climb is the first thing athletes want to know about a segment, and it gives context to every time on the leaderboard. No segment source records altitude: the `feed.segment` line is 2D, OSM trail ways have no elevation, and the importer drops any height in agency GeoJSON. The AppView does already receive altitude, though: every indexed `feed.segmentTrack` piece carries an `alt` array, which the matcher currently discards.

## What Changes

- The AppView derives each segment's elevation gain and loss and serves them on every `feed.defs#segmentView` as optional `elevationGain`, `elevationLoss` (whole meters) and `elevationSource` (`terrain` or `efforts`). The `feed.segment` record is unchanged, and any elevation a record might carry is never read.
- **Terrain baseline**: when a segment is indexed, the AppView samples its line against terrain elevation tiles it holds locally and stores the gain and loss. Every segment with terrain coverage gets values right away, the importer's included. Where there is no coverage, a segment has no terrain values.
- **Effort refinement**: the matcher keeps altitude when it reads a segment track and records gain and loss for each effort whose track has altitude. Once efforts with altitude come from at least as many distinct athletes as make a segment active (`SEGMENT_ACTIVE_ATHLETES`), the segment serves the median of each athlete's median instead of the terrain values. A single effort's elevation is never served.
- A backfill computes terrain values for segments already indexed and effort values from efforts already stored.
- A script downloads a region's terrain tiles into the AppView's tile directory. A new config option (`TERRAIN_TILES_DIR`) points the AppView at it. Without it, segments get only effort values.
- The segment screen shows gain, loss and average grade next to the distance whenever the view has them.

## Capabilities

### New Capabilities
- `segments/segment-elevation`: how the AppView derives a segment's elevation gain and loss from terrain tiles and matched efforts, what it serves, the privacy limit on effort-derived values, and how the segment screen shows them.

### Modified Capabilities
<!-- None: segment-records, segment-efforts and the segment screen keep their requirements; elevation is additive and owned by the new capability. -->

## Impact

- **Lexicon**: `lexicons/app/activitytracker/feed/defs.json` (`segmentView`) and its `core/lexicon` mirror.
- **Shared geometry**: `core/location` `TimedPoint` gains an optional altitude, and a new hysteresis gain/loss helper sits next to `SegmentGeometry`.
- **AppView**: `SegmentMatcher` (keeps `alt`, per-effort and per-segment elevation), `SegmentIndex` (terrain sampling), a new terrain tile reader, `SegmentViews` (serving), `AppViewConfig` (`TERRAIN_TILES_DIR`), and a Flyway migration (V53) adding columns to `segments` and `segment_efforts`.
- **Tooling**: a new `scripts/terrain-fetch.sh`; `scripts/dev.sh` and the README mention the tiles directory.
- **Client**: `feature/segment/ui` `SegmentUi` stats, `:app-fixtures` segment samples, and screenshot references.
- **Data**: terrain tiles (Terrarium PNG, AWS Open Data) are stored on the AppView host. They need the source attribution in the README.
