# Tasks

## 1. Lexicon

- [x] 1.1 Add `profile` (`#pathProfile`, `#pieceProfile`, `#segmentSpan`) to `lexicons/app/activitytracker/feed/getPath.json`, and mirror it in `core/lexicon` (`GetPath.Output.profile` and the models). Verify with `./gradlew :core-lexicon:jvmTest`, including a round trip through `AtJson`, and a clean `lexicon-auditor` run.

## 2. AppView: derive and store

- [x] 2.1 Add `V75__path_terrain.sql` (the `path_terrain` table and `paths.terrain_complete`) and the matching `Tables.kt` objects. Verify that `:server-appview:test` migrates on H2.
- [x] 2.2 Add the `path-elevation` job kind. Make `PathIndex` enqueue it on create or update when tiles cover the box, and drop the rows on delete. Verify with an indexer test: indexing a covered path queues one job, and replaying the job leaves the same rows.
- [x] 2.3 Implement the job: `terrainOf` per piece, `ElevationProfile.even` at one point per about 50 m (2–512), and rows for the covered pieces only. Verify with tests on the test tiles for a covered piece, a piece past the edge, a long piece capped at 512, and an edited line.
- [x] 2.4 Extend `backfillTerrain` to queue unsampled covered paths. Verify with a test that a path indexed before tiles gets a profile after the backfill.

## 3. AppView: serve

- [x] 3.1 Serve `profile` from `getPath`: the covered pieces in order, the summed gain and loss, and a stretch per returned segment (first piece with both ends within 30 m, nearer end first). Verify with route tests for the covered trail, a segment running against the line, a segment on no piece, no terrain, and a segment the viewer can't get, which has no stretch.

## 4. Shared chart

- [x] 4.1 Add `spans` (`ChartSpan`) to `LineChart`, with Swift accessors (breaks reuse `LinePoint.gapBefore`, which both UIs already draw). Verify with `LineChartTest` and `composeStabilityCheck`.
- [x] 4.2 Make `TimeSeriesChart`/`LineChartUi` draw breaks and the spans strip with the selected shading, and report scrubbing through `onScrub`. Verify with a component screenshot at `@PreviewComponentVariants` and a UI test that scrubbing calls back and leaving it reports null.

## 5. Trail page (shared)

- [x] 5.1 Add `PathProfile` to `feature/segment/api` (`of`, `chart(units, …)`, `spansOf(rows)`, `pointAt(x)`, climb text), with grade bands and readouts reused from `SegmentProfile`. Verify with commonTest tests: two pieces with a break, spans following rows and ratings, `pointAt` on each piece and in a gap, and the climb and descent text in both unit systems.
- [x] 5.2 Carry `profile` through `PathPresenter` and `PathState` (`profileChart` from `shown` and `selection`, plus `climbText`). Verify with `PathPresenterTest`: filtering drops a mark, selecting shades a stretch, and sorting changes nothing.
- [x] 5.3 Place the chart in `PathUi`: under the map on a phone, docked under the map when wide, and the climb in the summary. A scrub marks `SegmentsMap`, and hover shades. Verify with `PathUiTest` (the chart shows with a profile, no chart without one, the summary has the climb) and add `profile` samples in `PathSamples`.
- [x] 5.4 Update the Android screenshot references (`:androidApp:updateDebugScreenshotTestDefaultTestSuite`), check the new images, and check on desktop that scrubbing marks the map and hovering a row shades its stretch, in light and dark.

## 6. iOS

- [x] 6.1 Make `LineChartView` draw breaks, spans and shading, and report the scrubbed x. `SegmentsMapView` draws the marker. Verify with the design gallery snapshots.
- [x] 6.2 Draw the profile and climb on `PathPage`, with the scrub marking the native map. Verify with Trail snapshots for the profile samples on the iOS 26.0 simulator (record, then compare), plus a manual drag check in the simulator.

## 7. Wrap-up

- [x] 7.1 Run `./gradlew check` and the iOS build, and confirm both pass.
