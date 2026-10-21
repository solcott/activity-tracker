# Tasks

## 1. Area fills as decorations (no visible change)

- [x] 1.1 Add `ui/chart/AreaUnderlay.kt`: pure helpers that turn points, a y range, the layer bounds, x placement (`minX`, `xStep`, `xSpacing`, `startPadding`, `scroll`, direction), a baseline (bottom or top) and `step` into the outline of a closed fill. The outline drops to the baseline at each `gapBefore` point. Verify with `AreaUnderlayTest` in `composeApp/src/commonTest/.../ui/chart/`, which covers y scaling and clamping, step corners, a gap dropping to and running along the baseline, the top baseline, and RTL placement. Run `./gradlew :composeApp:jvmTest --tests "*.AreaUnderlayTest"`.
- [x] 1.2 Add the `AreaUnderlay : Decoration` itself. Its `drawUnderLayers` fills the helpers' outline with a reused `Path` and `Paint`, and rebuilds the path only when the bounds, spacing or scroll change. Verify with the build: `./gradlew :composeApp:compileKotlinJvm`.
- [x] 1.3 In `TimeSeriesChart`, draw the backdrop (range `low..low + span / BACKDROP_HEIGHT`, `onSurfaceVariant` at `BACKDROP_ALPHA`, bottom baseline) and the gap fill (range `minY..maxY`, `colors.area`, a top baseline when `fillAbove`, and `step`) as the first two decorations. Remove both from `model` and `layers`, delete `rememberBackdropLayer`, `fillLayer`, `fillOnly` and `UnmarkedLayer`, delete `GapConnector`'s baseline form if nothing uses it any more, and update the KDoc. Verify with desktop `hotRun` screenshots, before and after, that match:
  - the MTB sample's heart rate and power charts at 1200dp (backdrop);
  - a chart with an auto-pause gap (the fill breaks at the gap and the line stays on top);
  - gears (step fill);
  - a dive's depth (inverted, filled from the top).
  The marker still snaps to the main line in all of them.
  - Screenshots before and after at 1200×772 on the MTB sample, compared by PSNR over each chart: heart rate (backdrop and gap fill) 54.6 dB, respiration (gap fill) 72.1 dB, gears (step and backdrop) 56.9 dB. That's near-identical, differing only in anti-aliasing. The dive couldn't be compared: the local AppView answered `InternalServerError` for dive streams, so no depth chart rendered either before or after. `AreaUnderlayTest.aFillFromTheTopUsesTheTopEdge` covers the fill from the top instead.
  - The marker: the MCP can't tap a chart (no click action). Marker targets come only from Vico layers, and the main line's layer (with `others`) is now the only one; decorations have none.
- [x] 1.4 Run `./gradlew check` through the gradle-runner agent and verify it passes (detekt, ktfmt, composeStabilityCheck).

## 2. Measure on the Pixel

- [x] 2.1 Repeat the archived design's timing on an Android release build on the Pixel 9 Pro XL at 448dp: a temporary `drawWithContent` timer around `StaticChart`, the MTB sample, with the backdrop forced on and off. Record median, p90 and max, and draws over 16 ms and over 8.3 ms, in this change's design.md under a new Measurements section. Remove the timer afterwards and verify `git diff` shows no timer left. If the backdrop costs more than about 0.3 ms a chart at the median, or adds more than a couple of points of draws over 16 ms, stop here: record the result, keep the width gate, and revise the proposal and spec delta.

## 3. Elevation behind charts at every width

- [x] 3.1 Remove the `elevationBehind` parameter from `CardCharts`, `MetricCardUi`, `metricCards` and `PreviewCards`, along with its width-based arguments in `ActivityDetailUi` and `ImportPreviewUi`, so `CardCharts` always passes the elevation series. Update `CardCharts`' KDoc. Verify with desktop `hotRun` at 500dp (compact layout) that the MTB ride's heart rate chart and a hilly run's pace chart show elevation behind them (pace with climbs rising), that an indoor ride shows none, and that the import preview of a FIT ride with altitude shows it too.
  - Desktop at 500dp (compact): the MTB ride's heart rate chart shows the elevation behind it. I couldn't check the hilly run's pace chart, the indoor ride or the import preview here: the local AppView answered `InternalServerError` for the streams of every activity except the MTB rides, and the preview needs the native file picker. Those paths only lost the parameter, and the no-elevation case is unchanged (`firstOrNull` gives null).
- [x] 3.2 Install the release build on the Pixel 9 Pro XL and verify that the activity page's charts show the backdrop and scroll and scrub without visible jank.
  - The clean release build shows the backdrop behind heart rate and power at 448dp. The marker reads "1:20:31 · 114 bpm · 5,026 ft" on the line. Scrolling: 1.12% janky frames. Scrubbing drops about 20% of frames, and drops as many in a build without the backdrop, so this change doesn't cause it (design.md, Measurements).
- [x] 3.3 Run `./gradlew check` and verify it passes.

## Workflow follow-up

- Commit group 1 on its own (refactor, no visible change), then groups 2–3 (measurement and phones).
- Archive the change with `/opsx:archive`, which syncs the `feed/activity-page-layout` delta.
