# Tasks

## 1. Shared geometry

- [x] 1.1 Add `ElevationProfile.maxGrade(heights, atM, halfWidthM = 25.0)` in `core/location`: the steepest absolute grade in percent over a ±25 m distance window, narrowed at the ends; null for fewer than 2 heights. Verify with `ElevationProfileTest` cases for a steady 8% climb (8.0), a steep 15% descent in a gentle line (≥ 15.0), a short last stretch, and two heights (`./gradlew :core-location:jvmTest`)

## 2. Lexicon

- [x] 2.1 Add optional integer `maxGrade` (minimum 0, tenths of a percent) to `feed.defs#elevationProfile`, mirror it in `core/lexicon` `ElevationProfile` with a null default, and verify with a `SegmentRecordsTest` round trip with and without it (`./gradlew :core-lexicon:jvmTest`)

## 3. AppView

- [x] 3.1 Add migration `V55__segment_max_grade.sql` (`terrain_max_grade INTEGER`, no job queuing) and the `Segments.terrainMaxGrade` column. Verify with `./gradlew :server-appview:test` (Flyway on H2)
- [x] 3.2 Give `TerrainElevation` a `maxGradeTenths` from `ElevationProfile.maxGrade` on the smoothed samples in `TerrainTiles.terrainOf`, and write it in `SegmentMatcher`'s elevation update, null with the profile. Verify with a `TerrainTilesTest` case on a synthetic tile with a known slope
- [x] 3.3 Widen `SegmentMatcher.backfillTerrain`'s condition to segments with no terrain gain or no max grade, still inside the tiles. Verify with a `SegmentElevationTest` case: a covered segment with terrain but no max grade is queued at start and gains one, and a second start queues nothing
- [x] 3.4 Serve `maxGrade` in `getSegment`'s `elevationProfile` from the column, and verify `SegmentElevationTest` cases: a terrain segment carries it, an uncovered or efforts segment has no profile, and a re-published line updates it

## 4. Client

- [x] 4.1 Give `SegmentProfile` `highM` and `lowM` (from its heights) and `maxGradePercent: Double?`, map the last in `segmentProfileOf`, and verify with presenter tests for a profile with and without `maxGrade` (`./gradlew :feature-segment-presenter:jvmTest`)
- [x] 4.2 Add `segment_elevation_extremes` strings (with and without max grade) and show the line in `SegmentUi` under gain, loss and average grade whenever there's a profile. Verify with `SegmentElevationUiTest`: imperial high, low and whole-percent max grade; no max grade; no line without a profile
- [x] 4.3 Update `SegmentSamples` (Longview-like profile with `maxGrade`, a profile without it) and rewrite screenshot references with `:androidApp:updateDebugScreenshotTestDefaultTestSuite`, checking the changed images by eye

## 5. Integration

- [x] 5.1 Run `./gradlew check` green, then with the local stack and AppView, open Longview in the desktop app and confirm the high, low and max grade line (about 6,021 ft, 5,303 ft and 16%)

## Workflow follow-up

- Bring this branch up to date with `main` before applying, so `SegmentJobs.enqueueElevations` and the tile-limited `backfillTerrain` are here.
