# Tasks

## 1. Shared gain/loss and altitude

- [x] 1.1 Add `ElevationGain.of(heights: List<Double?>, thresholdM: Double = 3.0): GainLoss?` to `core/location` next to `SegmentGeometry`, per the design's hysteresis decision (skip nulls, null under two samples, whole meters). Verify with `core/location` jvmTest cases for: noisy flat wobble gives 0/0, a steady 120 m climb gives 120/0, a climb then descent, nulls skipped, and fewer than two samples gives null.
- [x] 1.2 Add `val altM: Double? = null` to `TimedPoint` (`SegmentGeometry.kt`). Verify that `./gradlew :core-location:jvmTest` and the existing client and AppView callers compile unchanged (`./gradlew :server-appview:compileKotlin :composeApp:compileKotlinJvm`).

## 2. Lexicon and the Kotlin mirror

- [x] 2.1 Add `elevationGain`, `elevationLoss` (integer, minimum 0, meters) and `elevationSource` (`knownValues: ["terrain", "efforts"]`) to `feed.defs#segmentView` in `lexicons/app/activitytracker/feed/defs.json`, and mirror them in `core/lexicon` as nullable fields with an open-enum `SegmentElevationSource` (following the `lexicon-change` skill). Verify that an `AtJson` round-trip test with and without the fields passes and that the `lexicon-auditor` agent reports no mismatch for `segmentView`.

## 3. AppView schema and config

- [x] 3.1 Add migration `V53__segment_elevation.sql`: the `segments` columns `terrain_gain_m`, `terrain_loss_m`, `effort_gain_m`, `effort_loss_m` (BIGINT NULL), `effort_athletes` (INT NOT NULL DEFAULT 0), the `segment_efforts` columns `elevation_gain_m`, `elevation_loss_m` (BIGINT NULL), and a `track` job for every segment track as in V43. Mirror the columns in `db/Tables.kt`. Verify that the AppView test suite migrates H2 cleanly (`./gradlew :server-appview:test --tests "*Migration*"`, or any DB test).
- [x] 3.2 Add `TERRAIN_TILES_DIR` (optional) and `TERRAIN_TILES_ZOOM` (default 12) to `AppViewConfig`, documented with the other `SEGMENT_*` settings. Verify with a config test that an unset value means no terrain.

## 4. Terrain elevation

- [x] 4.1 Add `segments/TerrainTiles.kt`: Terrarium PNG decoding, `{z}/{x}/{y}.png` lookup under the configured directory, bilinear height at a lat/lng, a small LRU of decoded tiles, and null for a missing tile. Verify with a unit test over a tiny generated fixture tile in `src/test/resources`, which checks the decoded heights, interpolation and a missing tile.
- [x] 4.2 Add terrain sampling: resample a decoded segment line every 10 m including both ends, read heights, and give null when any sample is uncovered, else `ElevationGain.of`. Verify with unit tests: a fully covered line gives gain/loss, and a line running off the fixture gives null.
- [x] 4.3 Add `SegmentJobs.ELEVATION`. Enqueue it from `upsertSegments` when a segment is new or its line changed and terrain is configured. Handle it in `SegmentMatcher.run`, writing `terrain_gain_m` / `terrain_loss_m`. At start, enqueue one for each segment with a null `terrain_gain_m` when tiles are configured. Verify with `SegmentElevationTest` cases for the spec scenarios: covered segment, partly covered, no terrain configured, line edited recomputes, record-supplied elevation ignored, and the startup backfill.

## 5. Effort elevation

- [x] 5.1 In `segmentTrackPieces()`, fill `TimedPoint.altM` from `alt / 100`, reject an `alt` array whose length differs from `t` as malformed, and keep missing samples null. Verify with tests: a track with `alt` decodes heights, a mismatched `alt` length is malformed, and a track without `alt` matches as before.
- [x] 5.2 In `replaceEfforts`, compute each pass's gain/loss from the piece's samples within `[start, end]`, plus the interpolated end heights, and write `segment_efforts.elevation_gain_m` / `elevation_loss_m`. Verify with tests: a track with altitude records the pass's climb, and a track without it records null.
- [x] 5.3 Recompute each affected segment's aggregate (the median across athletes of each athlete's median, plus `effort_athletes`) after `replaceEfforts` and `dropSegmentUse`, covering the union of old and new segment URIs. Verify with `SegmentElevationTest` cases for the spec scenarios: enough athletes, one athlete riding often counts once, too few athletes, and an athlete turning matching off falls back.

## 6. Serving

- [x] 6.1 In `api/SegmentViews.kt`, fill `elevationGain`, `elevationLoss` and `elevationSource` on every `segmentView`: effort values when `effort_athletes >= segmentActiveAthletes`, else terrain, else none. Never select effort elevation into an effort view. Verify with `SegmentElevationTest` route tests: `getSegment` and `searchSegments` carry the same elevation (every full `segmentView` is built in one place; `getActivitySegments` serves the basic view, which has no elevation); a segment without elevation has none of the three fields; and leaderboard and effort responses carry no elevation.

## 7. Tooling and docs

- [x] 7.1 Add `scripts/terrain-fetch.sh <region|minLat,minLng,maxLat,maxLng> [--zoom N] [--out DIR]`. It reads a region preset's bbox from `scripts/segment-regions/`, computes the tile range and downloads the missing Terrarium tiles with `curl` (idempotent). Verify by running it for a tiny bbox into the scratch directory and checking that the expected tile files exist.
- [x] 7.2 Document terrain tiles in the README (fetching, `TERRAIN_TILES_DIR`, and the required Tilezen source attribution) and add the command to the CLAUDE.md/AGENTS.md command list. Have the AppView's Gradle `run` task (which `scripts/dev.sh` starts) pass `TERRAIN_TILES_DIR` when `build/terrain` exists, and list both settings in `docker/.env.example`. Verify that `scripts/check-public-safety.sh` and `scripts/check-agent-sync.sh` pass.

## 8. Segment screen

- [x] 8.1 Add strings to `:ui-resources` and an elevation line to `SegmentUi` under the distance/type row: gain and loss via `Format.elevation`, and grade `(gain - loss) / distance` to one decimal place, shown only when the view has elevation. Add an elevation sample to `:app-fixtures` and keep one without. Verify with a UI test for both scenarios (1.6 km, +84/-12 m shows 4.5%; none shows no line), `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"`, and `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite` followed by a review of the new reference images.

## 9. Integration

- [x] 9.1 Run `./gradlew check` and verify it passes (tests, lint, ktfmt, detekt, module boundaries).
- [x] 9.2 On the local stack, run this change's AppView with tiles from `scripts/terrain-fetch.sh`, publish a segment with `docker/scripts/seed-segment.sh`, and verify that `getSegment` returns terrain elevation. (Done on port 8082 with a scratch database beside another worktree's AppView: a 2.74 km line up Mount Mansfield served +687 m / -14 m from terrain. Effort elevation is covered by `SegmentElevationTest`, not exercised live.)
