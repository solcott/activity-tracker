# Tasks

## 1. Baseline

- [x] 1.1 On the Pixel 9 Pro XL, install a clean release build of this change's base commit and run design.md's Measurements procedure: three 4 s drags across the MTB ride's heart rate chart, plus one vertical drag that starts on the chart. Record each drag's total frames, janky %, and p50 / p90 / p99 in a "Before" row of a new results table under design.md's Measurements. Verify the table has three drag results for the build.

## 2. Readout overlay

- [x] 2.1 Add pure readout helpers in `ui/chart`. `AreaFrame` gains the y mapping (`minY..maxY` onto the layer, clamped), and `areaOutline` switches to it. Add a nearest-point search over the main line by data x (from `dataX`), the dots for `others` lines with a point at that x, and the label's placement (above the topmost dot, flipped below when it would cross the layer's top, x clamped inside the layer). Verify with a new `ScrubReadoutTest` in `composeApp/src/commonTest/.../ui/chart/`: ends, between points, across a gap, RTL, the flip, and the clamp. `AreaUnderlayTest` still passes. Run `./gradlew :composeApp:jvmTest --tests "*.ScrubReadoutTest" --tests "*.AreaUnderlayTest"`.
- [x] 2.2 Move `ScrubController`'s rules into a Vico-free gesture state machine (press, move, release, enter, exit, mouse or touch, slop). Verify with `ScrubGestureTest`: a sideways drag shows and keeps the readout, a mostly vertical drag past half the slop hides it, release keeps it after a tap, hover enter and move show it, and exit outside the bounds hides it.
- [x] 2.3 Add the overlay and its frame decoration. The decoration's `drawOverLayers` stores the chart's `AreaFrame` in a shared holder. The overlay is a `Box` sibling over the `CartesianChartHost`, sized to it, with its own `graphicsLayer`. One non-consuming `pointerInput` drives the state machine. It draws the guideline, dots and bubble with `rememberValueMarker`'s colours, sizes and text style, and caches the measured text for its point. `StaticChart` takes an optional readout (main points, other lines, y range, `text(x, y)`, the last through `rememberUpdatedState`), adds the decoration to the chart, and gives the host its own `graphicsLayer`. Verify with `./gradlew :composeApp:compileKotlinJvm`.
- [x] 2.4 Switch `TimeSeriesChart` (plotted points, `plottedExtra`, `minY..maxY`, `markerLabel` through `flip`) and `DepthProfile` to the overlay. Drop `marker` and `markerController` from their `rememberCartesianChart`, and delete `rememberValueMarker`, `ScrubController` and `rememberScrubController` from `ChartMarker.kt`. Update both charts' KDoc. Verify with `./gradlew :composeApp:compileKotlinJvm`; how the readout looks is checked on the Pixel in 3.1 (the hot-reload MCP can't hover or drag a chart).
- [x] 2.5 Confirm the chart isn't redrawn while scrubbing, as a lasting test rather than a temporary counter: add `compose ui-test` to `composeApp`'s `jvmTest` and a `ChartReadoutUiTest` that renders a `StaticChart` with a readout and a decoration counting Vico's draws. A sideways touch drag shows the bubble at every step, keeps it after release on the last point, and adds no chart draws. A vertical drag shows no readout. A mouse hover reads out the nearest point and hides on exit, again with no chart draws. Run `./gradlew :composeApp:jvmTest --tests "*.ChartReadoutUiTest"`.
- [x] 2.6 Run `./gradlew check` through the gradle-runner agent and verify it passes (detekt, ktfmt, `composeStabilityCheck`).

## 3. Measure on the Pixel

- [x] 3.1 Install a clean release build of the change (no counter) on the Pixel 9 Pro XL and repeat 1.1's procedure, adding an "After" row. Verify:
  - each drag is at most 5% janky with p50 at most 8 ms;
  - the vertical drag scrolls the page with no readout;
  - a sideways drag on the heart rate chart reads out on the line, with the chart beneath not flickering.
  - against the base build, the readout's bubble, dot and guideline look the same at the chart's start, middle and end and near its top where the label flips, and the power curve reads out like "5m · 219 W".

  If the bar is missed, record the numbers and a Perfetto trace's per-frame work in design.md and stop to revise the design, rather than continuing.
- [x] 3.2 Run `./gradlew check` and verify it passes.
  - `check` ran after the last code change. Everything passed except `:app-presenters`' `ActivityDetailCommentsTest.commentsOnAFollowersActivityOnceForADoubleTap`, which timed out after 1 minute under the full suite's load. It passes on its own (17 tests), and this change doesn't touch that module.

## Workflow follow-up

- Commit group 2 as one commit (overlay plus the Vico marker's removal), then the measurements in design.md as a second, only when asked, directly to main.
- Archive the change with `/opsx:archive`, which syncs the `feed/activity-page-layout` delta.
