# Design

## Context

The reason is in proposal.md, under Why. The measurements are in the archived `2026-10-19-activity-detail-redesign/design.md`, under Measurements.

How Vico 2.5.2 (`multiplatform`) draws a `CartesianChart`, from its sources:

```
axes.drawUnderLayers, decorations.drawUnderLayers   --> host canvas (hardware on Android)
layerBitmap = cached full-canvas ImageBitmap, cleared
  for each LineCartesianLayer, for each series:
    saveLayer(full canvas)
    areaFill path
    clear "line" + "lineFill" bitmaps (full canvas each)
    stroke path -> line bitmap; fill rect -> lineFill bitmap
    lineFill SrcIn onto line; drawImage(line)
    getPixel() per point for marker colours
drawImage(layerBitmap)                               --> texture upload
axes/decorations.drawOverLayers, marker
```

Everything inside `layerBitmap` is drawn in software, and the cost grows with the canvas's pixel size. `TimeSeriesChart` has two series that exist only to show an area fill: the backdrop (`rememberBackdropLayer`) and, when the line has gaps, `fillLayer`. Both are `UnmarkedLayer`s with a transparent 0dp stroke. Each one costs about as much as the main line's series.

## Goals / Non-Goals

**Goals:**
- The backdrop and the gap fill cost almost nothing to draw, and look exactly as they do now.
- One measured result on the Pixel 9 Pro XL decides whether phones get the backdrop.

**Non-Goals:**
- The main line, `others` lines and the no-gap area fill stay Vico series in the layer bitmap.
- No Vico upgrade, and no hand-drawn `Canvas` chart replacing Vico.

## Decisions

**Area fills become a `Decoration.drawUnderLayers`.** `drawUnderLayers` runs before `layerBitmap` and draws onto the host canvas, so a filled `Path` there goes to the GPU with no offscreen layer, no bitmap clears and no upload. It also keeps today's order: under-layer decorations draw in list order (backdrop, then fill), and the line's bitmap goes on top. `EventMarks` and `PointLabels` already extend charts this way. Decorations have no marker targets, so the `UnmarkedLayer` override isn't needed.
- *Alternative, thin the backdrop's points:* rejected, because the cost comes from bitmap size, not point count.
- *Alternative, a separate Compose `Canvas` behind the `CartesianChartHost`:* rejected. It would need the chart's layer bounds, which only Vico knows after measuring its axes.
- *Alternative, a custom `CartesianLayer` subclass:* rejected. It would still draw into `layerBitmap`.

**One class, `AreaUnderlay`, serves both fills.** Its inputs are the points, the y range it maps onto `layerBounds` (`minY..maxY`), a colour, a baseline (bottom, or top for an inverted chart filled from above) and `step`.
- Backdrop: the range is `low..low + span / BACKDROP_HEIGHT`, and the `MIN_BACKDROP_SPAN` rule is kept.
- Gap fill: the range is the start axis's `minY..maxY`.

**Path geometry lives in pure helpers, and the decoration only adapts them to Vico.**
- A point's x comes from the same placement as `EventMarks`: `start + direction * ((x - minX) / xStep * xSpacing + startPadding) - scroll`.
- Its y is linear in `minY..maxY`, clamped to the layer.
- The path starts on the baseline. It joins points directly, or across and then up or down when `step`. At a point with `gapBefore` it drops to the baseline at the previous x, runs along it to this x and climbs to the point, which is what `GapConnector` does with a baseline. It closes back to the baseline at the end.
- The decoration reads `gapBefore` directly, so it doesn't need `GapConnector`'s inverse pixel-to-x lookup.

The helpers take plain numbers, such as layer bounds and spacing, and return the path's points. That lets `commonTest` check them without a Vico context.

**The decoration reuses one `Path` and one `Paint`.** It rebuilds the path only when the layer bounds, spacing or scroll change. In a `StaticChart` they change only on a resize, so scrubbing the marker repaints a cached path.

**Remove the width gate after measuring, not before.** The `elevationBehind` parameter (`CardCharts`, `MetricCardUi`, `metricCards`, `PreviewCards`) goes away and `CardCharts` always passes elevation. This happens only once the Pixel numbers meet the bar in Risks. Until then the work is only a refactor with no visible change, and it can land on its own.

**Clean-up.** `rememberBackdropLayer`, `fillLayer`, `fillOnly` and `UnmarkedLayer` are deleted. `GapConnector.Baseline` and the baseline branch of `GapConnector` are deleted too if nothing else uses them; the main line and `others` use only the break (no-baseline) form.

## Risks / Trade-offs

- [The saving is smaller than expected, for example because HWUI path tessellation is slow for a 300-point fill.] → Measure as the archived design did: a temporary `drawWithContent` timer around `StaticChart`, an Android release build on the Pixel 9 Pro XL at 448dp, the MTB sample, with and without the backdrop. Phones get the backdrop only if its median cost is about 0.3 ms or less a chart and draws over 16 ms stay within a couple of points of the no-backdrop run. Otherwise keep the gate and record the numbers.
- [Small pixel differences from Vico's own fill: anti-aliasing at the edges, or the fill's bottom edge versus `layerBounds`.] → Compare screenshots side by side on desktop (MTB backdrop, a gapped chart, gears' step, an inverted dive depth) before deleting the old layers.
- [RTL or a future scroll or zoom could put x in the wrong place.] → Use exactly the same placement as `EventMarks` and `GapConnector`, and cover RTL in the helper tests.
- [A chart could still be slow at 120 Hz (7.7 ms median) without the backdrop.] → Accepted and out of scope (proposal, Impact). The same decoration technique is the way forward if it's taken on later.

## Measurements

**Elevation backdrop draw time with the decorations** (task 2.1). This uses the archived design's method: a temporary `drawWithContent` timer around `StaticChart`, logging each chart's draw. It ran on an Android release build signed with the debug key on the Pixel 9 Pro XL (448dp, 120 Hz), on the MTB sample ride (2,700 one-second samples, thinned to 300 points a chart). The backdrop was forced on or off below 600dp. Each run opened the ride, scrolled once to warm up, then did three passes of 14 swipes down and 14 back up. "Backdrop charts" are the charts that draw it: heart rate, power, speed, cadence, grade, temperature and gears. "All charts" adds elevation, respiration and the power curve.

| Build | Backdrop charts (median / p90 / max) | All charts (median / p90 / max) |
|---|---|---|
| Backdrop on | 7.7 / 15.6 / 21.3 ms (n=98; 9 over 16 ms, 44 over 8.3 ms) | 7.5 / 16.4 / 21.3 ms (n=142; 18 over 16 ms, 63 over 8.3 ms) |
| Backdrop off | 7.5 / 16.1 / 45.7 ms (n=100; 12 over 16 ms, 46 over 8.3 ms) | 7.8 / 16.1 / 45.7 ms (n=146; 18 over 16 ms, 70 over 8.3 ms) |

The backdrop now costs nothing measurable. Its median is 0.2 ms higher on the backdrop charts, with 9% of their draws over 16 ms against 12% without. Chart by chart, the medians differ by -1.6 to +0.9 ms in both directions, averaging -0.1 ms. That is within this run's noise: elevation, which draws no backdrop in either build, differs by +2.0 ms. The one 45.7 ms draw is in the backdrop-off run, which rules out the backdrop as its cause. Before this change the backdrop cost about 2 ms a chart at the median, and a quarter of draws passed 16 ms (archived `2026-10-19-activity-detail-redesign/design.md`).

**Decision:** the bar is met (median cost at most ~0.3 ms, and draws over 16 ms within a couple of points of the run without). Phones get the backdrop. The timer measures recording a chart's draw, which is where Vico's software bitmaps are filled. Like the archived numbers, it doesn't include the GPU's later work on the frame.

**Frames on the Pixel with the gate removed** (task 3.2). These numbers come from `dumpsys gfxinfo` on clean release builds (no timer), on the same ride.
- Scrolling with the backdrop: two passes of 14 swipes down and back up gave 5,519 frames, 1.12% janky, 5 / 7 / 26 ms at p50 / p90 / p99.
- Scrubbing, 4-second drags across the heart rate chart:

  | Build | Janky frames (3 drags) | p50 / p90 |
  |---|---|---|
  | With backdrop | 21.6%, 18.9%, 16.2% | 13–14 / 18–20 ms |
  | Without (a temporary build) | 19.6%, 21.1%, 21.2% | 14 / 19–21 ms |

Scrubbing drops about a fifth of frames with or without the backdrop. Every marker move redraws the whole chart, and the main line still goes through Vico's software bitmaps. This change neither causes nor fixes that. Moving the main line out of those bitmaps is the remaining work (Non-Goals).

## Migration Plan

UI only, with nothing stored. Land it in two commits: the decoration refactor, which doesn't change what you see, then the width gate's removal together with the measurement in this design. Rolling back the second commit hides the backdrop on phones again.
