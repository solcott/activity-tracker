# Design

## Context

The reason is in proposal.md, under Why. The scrubbing measurements are in `elevation-backdrop-on-phones/design.md`, under Measurements.

How Vico 2.5.2 (`multiplatform`) handles a marker move, from its sources (`CartesianChartHost.kt`, `CartesianChart.kt`, `LineCartesianLayer.kt`):

```
pointer event --> onInteraction --> markerX = target.x       (rememberSaveable state)
                                       |
          rememberCartesianMeasuringContext(..., markerX)    (markerX is a remember key)
                                       |
                                       v
Canvas { chart.prepare()            measure axes, labels, margins
         chart.draw():
           axes, decorations.drawUnderLayers               hardware canvas
           layerBitmap = getBitmap(), cleared              full canvas, CPU
             each series: saveLayer, clear "line" and "lineFill" bitmaps,
                          stroke, SrcIn, getPixel per point -> marker targets
           drawImage(layerBitmap)                           new texture upload
           axes, decorations.drawOverLayers
           marker.drawOverLayers(targets)                   guideline, dot, label
}
```

- Marker targets are only filled while the layers draw (`drawingConsumer` copies `layer.markerTargets`), so Vico can't draw its marker without redrawing every layer.
- `CartesianChart.draw` clears and uploads `layerBitmap` on every draw, even when each layer draws nothing.
- With `marker = null`, `onInteraction` is null and `markerX` never changes, so nothing invalidates the host's `Canvas` after its first draw. A `StaticChart` has scrolling, zooming and animation off.
- Maven Central lists 2.5.2 as Vico's latest release (June 2026).

`rememberValueMarker` and `rememberScrubController` (`ChartMarker.kt`) have two callers: `TimeSeriesChart` (every metric chart and the power curve, on the activity page and the import preview) and `DepthProfile`. Nothing calls `DepthProfile` yet, but it is kept working.

## Goals / Non-Goals

**Goals:**
- A marker move redraws only the marker. The Vico chart beneath records its draw once per layout and isn't redrawn while scrubbing.
- The readout looks and behaves as it does now (spec: Reading a chart's values).

**Non-Goals:**
- The main line, `others` lines and the no-gap area fill stay Vico series in the layer bitmap. That cost is paid once per layout, and scrolling reuses the recorded chart.
- No Vico upgrade, fork or patch, and no hand-drawn replacement for the chart itself.
- `ThresholdHistoryChart` has no marker and is unchanged.

## Decisions

**The marker leaves Vico and becomes an overlay with its own layer.** `StaticChart` puts a readout overlay over the `CartesianChartHost` in one `Box`, sized to match (`matchParentSize`). The overlay has its own `graphicsLayer` and reads the marker's state only in its draw block. A marker move then invalidates only the overlay's layer: Compose re-records that one `RenderNode`, and the chart's node is reused as it is. Vico gets `marker = null` and no `markerController`. The host also gets a `graphicsLayer` of its own. That is cheap, and keeps the chart's recording separate even if something above it ever re-records.
- *Alternative A, the line and its fill as decorations, with a no-op `CartesianLayer` keeping ranges and marker targets:* rejected. Every move would still redraw the whole chart: `prepare`'s measuring, the axes and their text, and the `layerBitmap` clear and upload, which this API can't avoid. The cost would drop but still grow with the chart, and it gives up Vico's line drawing (gaps through `GapConnector`, `others`) for hand-drawn paths.
- *Alternative, a second `CartesianChartHost` over the first that draws only the marker:* rejected. It would still pay `prepare` and the `layerBitmap` round trip on every move, and its marker targets would still need real layers.
- *Alternative C, a newer Vico:* there isn't one.

**The overlay gets the chart's geometry from a decoration.** A `Decoration` whose `drawOverLayers` copies the context into the `AreaFrame` that `AreaUnderlay` already uses (layer bounds, leading edge, direction, `minX`, `xStep`, `xSpacing`, `startPadding`, scroll) stores it in a plain holder the overlay shares. It is not snapshot state: it only changes when the chart lays out again, and the overlay reads it while handling pointer events and while drawing. A resize sizes the chart and the overlay together, and the `Box` draws the chart before the overlay, so the overlay's draw in that frame already sees the new frame. Until the chart has drawn once, the holder is empty and the overlay shows nothing.
- *Alternative, measuring the axes' margins in Compose:* rejected. Only Vico knows the layer bounds after measuring its axis labels, which is the same reason `AreaUnderlay` became a decoration.

**Placement, the nearest point and label layout are pure helpers.**
- A point's canvas x is `AreaFrame.x`. A pointer's data x is `dataX` (`ChartGaps.kt`), which is already its inverse.
- The nearest point is a binary search over the main line's points by x, as `nearestY` in `ChartUnits` does. It searches the main line only, not every series as Vico's targets do: the label has always described the main line (`points.first()`).
- A point's canvas y maps the start axis's `minY..maxY` onto the layer, clamped to it, which matches `areaOutline`'s `y` and Vico's `limitedCanvasY`. That mapping moves into `AreaFrame`, so the fill and the readout share one formula.
- Dots go on the main line and on each `others` line with a point at exactly that x, as Vico's targets for one x do today.
- The label goes above the topmost dot, or flips below it when it would cross the layer's top. Its x is clamped so it stays inside the layer, as `DefaultCartesianMarker`'s `AroundPoint` and `overrideXPositionToFit` do.

`commonTest` checks these without Vico or Compose: the nearest point at ends, between points and across a gap; RTL; the flip; the clamp.

**The gesture rules become a small Vico-free state machine.** `ScrubController`'s rules move over unchanged: press shows; a move hides once the drag passes half the touch slop mostly vertically; release keeps the marker unless the gesture was a scroll; mouse enter and move show; exit hides. The overlay feeds it from one `pointerInput` that reads `awaitPointerEvent()` in the main pass and consumes nothing, as Vico's `pointerInput` does (`consumeMoveEvents = false`), so the page's `LazyColumn` still gets vertical drags. A tap is a press and a release, so the tap detector Vico used isn't needed.

**The overlay draws with the theme's components, in Compose.** It draws a 1dp guideline in `onSurfaceVariant` at 50%, a 10dp `primary` dot with a 2dp `surface` ring, and the text in `labelMedium` `inverseOnSurface` on an `inverseSurface` bubble with 6dp corners and 8 by 4dp padding: the same values as `rememberValueMarker`. Text is measured with a `TextMeasurer` and cached for the point index it was measured for, so a move within the same point measures nothing. The overlay is a `ui/chart` piece like `StaticChart`, so the rule to build charts from those pieces holds. Its `DrawScope` code is limited to the marker.

**Its API stays narrow.** `StaticChart` gains an optional readout parameter: the main points, the other lines, the y range, and `text(x, y)`. `TimeSeriesChart` passes its plotted points and `flip`. `DepthProfile` passes its `-depth` points. The label lambda goes through `rememberUpdatedState`, as `rememberValueMarker` does now, so a new lambda from a recomposition (`rememberMarkerLabel` returns a fresh one each time) neither restarts the gesture loop nor rebuilds the Vico chart.

**Clean-up.** `rememberValueMarker`, `ScrubController` and `rememberScrubController` are deleted, and with them the Vico marker imports.

## Risks / Trade-offs

- [Something still re-records the Vico canvas on each move: a recomposition of `TimeSeriesChart`, a state read in composition, or a shared layer.] → `ChartReadoutUiTest` (jvm, `compose ui-test`) counts Vico's draws through a decoration while it drags and hovers, and fails if any draw is added (task 2.5).
- [The readout differs slightly from Vico's: the label's offset from the dot (Vico's `tickSize`), anti-aliasing, the bubble's shadow.] → Compare desktop `hotRun` screenshots before and after, at the start, middle and end of a chart and at the top edge where the label flips.
- [Gesture regressions: page scroll caught by the chart, or a horizontal drag lost to the list.] → The overlay consumes nothing, as Vico didn't. Check on the Pixel: a vertical drag that starts on a chart scrolls the page, and a sideways one scrubs.
- [Screen readers.] → There's no change: the chart's semantics stay on the `StaticChart` modifier, and neither the old marker nor the overlay exposes nodes.
- [Scrubbing is already smooth but the first draw of a chart is still slow (7.5 ms median per chart).] → Accepted and out of scope (proposal, Impact). Alternative A is the way to take that on later.

## Measurements

The method repeats `elevation-backdrop-on-phones` task 3.2 and runs on the same device both times, so before and after compare directly.

- **Device and build:** Pixel 9 Pro XL at 448dp, 120 Hz. A clean release build with no temporary timers or counters. `androidApp` has no release signing config, so `./gradlew :androidApp:assembleRelease`'s unsigned APK is `zipalign`ed, then signed with `apksigner` and `~/.android/debug.keystore`. That is the debug build's key, so `adb install -r` keeps the signed-in app's data. The device needs `adb reverse` for ports 2582, 2583 and 8081, as `scripts/dev.sh` sets it up.
- **Data:** the MTB sample ride, with the heart rate chart scrolled fully into view.
- **Before:** the build at this change's base commit. **After:** the build with the change.
- **For each drag:**
  1. `adb shell dumpsys gfxinfo io.github.solcott.activitytracker reset`
  2. One 4 s drag across the heart rate chart, left to right, through its middle: `adb shell input swipe <x0> <y> <x1> <y> 4000`, with `y` and the x ends read from a screenshot or `uiautomator dump`.
  3. `adb shell dumpsys gfxinfo io.github.solcott.activitytracker`, recording total frames, janky frames (%), and p50, p90 and p99.
- **Runs:** three drags per build. Repeat with a vertical drag that starts on the chart, to confirm the page still scrolls and no readout appears.
- **Earlier baseline:** 21.6%, 18.9% and 16.2% janky, p50 13–14 ms, p90 18–20 ms.
- **Bar:** every drag at most 5% janky, and p50 at most 8 ms. Scrolling the same page measures 1.1%.
- **If the bar is missed:** record the numbers, take a Perfetto trace of one drag to see what still runs per frame, and revise this design before going further.

**Results** (tasks 1.1 and 3.1). Both builds were clean release builds signed with the debug key, run on the Pixel 9 Pro XL on the same evening, on the MTB ride "Afternoon Mountain Bike Ride", with the heart rate chart in the same place on screen. Each drag was a 4 s `input swipe` from x 150 to x 920 through the chart's middle.

| Build | Drag | Frames | Janky | p50 / p90 / p99 |
|---|---|---|---|---|
| Before (base `7809c735`) | 1 | 271 | 8.49% | 12 / 16 / 26 ms |
| | 2 | 267 | 7.87% | 12 / 17 / 21 ms |
| | 3 | 265 | 12.45% | 12 / 19 / 25 ms |
| | vertical | 462 | 0.43% | 5 / 7 / 12 ms |
| After | 1 | 470 | 0.00% | 6 / 8 / 12 ms |
| | 2 | 468 | 0.00% | 7 / 9 / 12 ms |
| | 3 | 466 | 0.00% | 6 / 9 / 11 ms |
| | vertical | 467 | 0.00% | 6 / 8 / 11 ms |

- **Janky frames:** 0% on every drag, against 8–12% before. The bar is met.
- **Frame time:** p50 fell from 12 ms to 6–7 ms.
- **Frame rate:** a drag now draws about 470 frames in its 4 s, the display's full 120 Hz (it drew about 270 before).
- **Earlier baseline:** this evening's "before" is lower than `elevation-backdrop-on-phones`' 16–22% on the same procedure, but with the same 12–14 ms p50. The device's thermal state and background load differ from run to run, which is why both builds were measured back to back.
- **Readout:** after a sideways drag it shows "2:05:57 · 141 bpm · 4,809 ft", in the same place in both builds.
- **Vertical drag:** a drag that starts on the chart scrolls the page and shows no readout.
- **Edges:** at the chart's start the label is pushed inside the left edge. At a 173 bpm peak it flips below the dot.
- **Power curve:** it reads "1m · 318 W", matching its description.

`ChartReadoutUiTest` checks the chart isn't redrawn while scrubbing (task 2.5). As a mutation check, removing both `graphicsLayer`s made it fail: Vico drew 16 times during the drag instead of 2.

## Migration Plan

UI only, with nothing stored. Land it as one commit, the overlay and the removal of the Vico marker together, so no build has two markers. A second commit adds the measurements to this design. Rolling back the first commit brings the Vico marker back.
