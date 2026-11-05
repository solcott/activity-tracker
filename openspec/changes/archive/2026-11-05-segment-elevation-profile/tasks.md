# Tasks

## 1. Smoothing and profile in core/location

- [x] 1.1 Add `ElevationProfile.smooth(heights, spacingM = 10.0, halfWidthM = 25.0)` in `core/location`: a centred moving average whose window narrows evenly near the ends, so the first and last heights are kept. Test in `commonTest`: a straight climb comes back unchanged, the 4 m every 20 m ripple on a 100 m climb gives `ElevationGain.of` 100/0, and ends are preserved. Run `./gradlew :core-location:jvmTest`.
- [x] 1.2 Add `ElevationProfile.even(heights, atM, count = 128)`: evenly spaced linear interpolation from start to end, rounded to whole decimeters. Test the endpoints, the point count, a two-sample input and a known midpoint value. Run `./gradlew :core-location:jvmTest`.

## 2. Lexicon

- [x] 2.1 Add `feed.defs#elevationProfile` (`heights`: integer array, minLength 2, maxLength 512, decimeters, evenly spaced start to end) and an optional `elevationProfile` ref in `feed.getSegment`'s output. Update the `segmentView` `elevationSource` and `elevationGain` descriptions for the terrain-first order. Follow the `lexicon-change` skill's checklist.
- [x] 2.2 Mirror the change in `core/lexicon`: an `ElevationProfile` model and a nullable field on `GetSegment.Output`. Add a round-trip test with and without the field, using `AtJson`. Run `./gradlew :core-lexicon:jvmTest`.

## 3. AppView: terrain, storage, serving

- [x] 3.1 Add migration `V54__segment_elevation_profile.sql`. It adds `segments.terrain_profile TEXT` (nullable) and queues an `elevation` job for every segment that has none queued, following V53's pattern. Add the column in `Tables.kt`. Run `./gradlew :server-appview:test` (Flyway on H2).
- [x] 3.2 Replace `TerrainTiles.elevationOf` with `terrainOf(line): TerrainElevation?` (gain, loss, `profileDm`): resample 10 m, look up heights, `smooth`, then `ElevationGain.of` and `even`. It's null when any sample isn't covered. Replace its hand-written decoded-tile LRU with a Caffeine cache of the same size, keeping missing tiles cached as missing. Extend the TerrainTiles tests with tiles that ripple and a check that a profile has 128 points.
- [x] 3.3 `SegmentMatcher.sampleTerrain` writes `terrain_profile` (comma-separated decimeters) with the gain and loss under the existing `line eq` guard, and nulls all three when the segment isn't covered. Test: a covered segment gets a profile, an uncovered one gets none, and a re-lined segment's profile follows the new line.
- [x] 3.4 Make `servedElevation` serve terrain first and use efforts only when terrain is null and `effort_athletes >= activeAthletes`. Update the elevation tests: a covered segment with five athletes serves `terrain`, and an uncovered one with three serves `efforts`, two serves none. When a third athlete drops on an uncovered segment, it serves none.
- [x] 3.5 `SegmentViews.segment` sets `GetSegment.Output.elevationProfile` from `terrain_profile` only when the served source is `terrain`. Route tests: a terrain segment's output has 128 heights, an efforts or bare segment's has none, and the search, name search and check outputs never carry a profile.
- [x] 3.6 Run the `lexicon-auditor` agent on `feed.defs#elevationProfile` and `feed.getSegment`, and fix any mismatch it reports.

## 4. Client data and contract

- [x] 4.1 Add `SegmentRepository.segmentDetail(uri)` returning `GetSegment.Output` (with `segmentDetailOutcome`), with `segment(uri)` and `segmentLookup` reading `.segment` from it. Update `SegmentRepositoryTest` with a `MockEngine` response that has a profile. Run `./gradlew :core-data:jvmTest`.
- [x] 4.2 Add `SegmentProfile` (`heightsM: ImmutableList<Double>`, `distanceMeters`, `gradesPercent: ImmutableList<Double>`, with `gradeBands` derived through `GradeBand.of`) to the segment feature's `-api`, and `profile: SegmentProfile?` to `SegmentState`. The presenter maps it from the output: decimeters to meters, and each point's grade over about ±25 m. Presenter test: a profile maps with the expected bands, and no profile gives null. Run `./gradlew :composeApp:jvmTest --tests "*Segment*"` or the feature presenter module's tests.
- [x] 4.3 In `:app-fixtures` `SegmentSamples`, add a sample with a profile (a climb with a steep middle) and keep the existing samples without one, including an efforts-sourced one.

## 5. Chart and segment screen

- [x] 5.1 Add `GradeBands` to `:ui-designsystem`: thresholds 3, 6, 10 and 15%, and five theme colours with light and dark values. Check the contrast against the surface in both themes.
- [x] 5.2 Add `GradeBands.brush(xFractions, bands, colors)` (a hard-stop `Brush.horizontalGradient`) and an optional `areaBrush: Brush?` on `TimeSeriesChart` that Vico's area fill and line stroke use through `Fill(brush)`. Leave `AreaUnderlay` alone. Unit-test the brush's stops: they repeat at each band change. Confirm the existing chart screenshot tests still pass.
- [x] 5.3 Add `SegmentProfileChart` in `feature/segment/ui`, drawn after the stats column when `state.profile` isn't null. It uses a distance x axis (`xLabelsAt`, whole or half miles or km by length) and `Format.elevation` y labels. The scrub readout reads distance · elevation · grade, with its strings in `:ui-resources`. The fill and line are tinted with `GradeBands.brush` built from `gradeBands`. Lifts and runs draw it alike.
- [x] 5.4 Add a UI test rendering the profile sample through `AppPreview`: the chart is present with its description, and it's absent for a sample without a profile. Update the screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, then run `./gradlew :composeApp:jvmTest --tests "*ContentLoadingConformanceTest"`.

## 6. Verification

- [x] 6.1 Run `./gradlew check` and `./gradlew composeStabilityCheck` (the new `SegmentProfile` must be stable).
- [x] 6.2 End to end on the local stack, with `scripts/terrain-fetch.sh us-utah` tiles: run the AppView, let the V54 jobs drain, and open a Corner Canyon segment (for example Longview) on desktop. It shows smoothed gain and loss within about 5% of the spike's lidar figures (223 m up / 6 m down for Longview) and a grade-tinted profile. Take a screenshot as proof.
