# Design

## Context

Chart data is built in the presenter (`ChartsBuilder`): each stream becomes a `LineSeries` of `ChartSample`s by `StreamAnalytics.averaged`, which thins a stream to about 300 points by averaging runs of consecutive samples (by index), each placed at the mean time of its run. `composeApp`'s `TimeSeriesChart` draws the points with Vico 2.5.2 (`vico-multiplatform`, common code for every target): one `LineCartesianLayer` holding the main line (stroke plus area fill) and any other lines, and optionally a backdrop layer (elevation, fill only). Vico joins every consecutive pair of a series with the line's `PointConnector`; its entries take non-null numbers only, so there is no "null point" to break a series.

The map's break (`CombinedRoute.BREAK_SECONDS`, 120 s) lives in `core/data`, beside `StreamAnalytics`.

## Goals / Non-Goals

**Goals:** breaks that follow the recorded samples exactly, on every target, with no extra cost for a chart without gaps.

**Non-Goals:** changing how zone times, the power curve or other stats treat pauses (`PerSecond` already does); breaking the gears chart; marking gaps with a label or shading.

## Decisions

### 1. Find gaps on the stream as recorded, in `StreamAnalytics`

A 4-hour ride at 1 s is thinned to a point about every 48 s, a 10-hour one to every 120 s. Found on chart points, a gap would depend on the thinning: a run straddling a gap puts its averaged point in the middle of it, which can hide a gap of a few minutes, and smart recording's uneven runs space points unevenly enough to look like gaps. So the gap is found on the present samples (time and non-null value), before thinning:

- The limit is `max(CombinedRoute.BREAK_SECONDS, 4 × usual interval)`, the usual interval being the median positive gap between present samples. Four intervals keep a 30 s or 60 s logged stream whole while one- and smart-recorded streams keep the map's 2 minutes.
- `averaged` starts a new run at the first present sample after a gap (a run never spans one) and marks that run's point. It returns points carrying that mark, so no point is drawn inside a gap.

Alternative: infer gaps from chart-point spacing in the UI. Rejected for the reasons above.

### 2. Carry the mark as a flag on each point

`ChartSample` and `SeriesPoint` gain `gapBefore: Boolean = false`. The series stays one flat list, so everything that reads `LineSeries.points` (headline stats, markers, tests) is unchanged.

Alternative: a list of pieces per series. Rejected: a wider contract change for the same information.

### 3. Break the stroke with a connector, the fill with its own layer

Vico's options for a break:

- One series per piece. Works on every target, but Vico allocates two canvas-sized bitmaps per series per chart, so a commute with a dozen pauses would cost tens of megabytes across the page's charts, and the line-to-series mapping (`LineProvider.series` repeats lines by index) needs a custom provider.
- A path `moveTo` at the gap inside the connector. Breaks the stroke cleanly, but Vico's area fill adds the whole line path and closes only its last subpath to the baseline, so earlier pieces would fill as chords.

So: a `GapConnector` wraps the line's connector (sharp or step). It's built with the sorted x values of the points marked `gapBefore`, and for a segment whose end point is one of them it moves the path to that point instead of joining it. A connector is handed canvas coordinates, so it maps the end point back to data x with the layer's own geometry (`layerBounds`, `layerDimensions.startPadding`/`xSpacing`, `ranges.minX`/`xStep`, `scroll`, layout direction) and matches within half a pixel. For the fill, the main line's area moves to a fill-only line in a layer of its own under the stroke (like the backdrop, with no marker targets, so the marker's dot and readout stay on the stroke). Its connector drops to the fill's baseline at a gap, runs along it and rises at the next piece, so the fill has no area across the gap; its stroke is transparent, so the path along the baseline isn't seen. The backdrop uses the same dropping connector.

Charts with no gap get the plain connector and keep their single layer's look; the extra fill layer is there only when a series has gaps.

### 4. Only time charts break

`TimeSeriesChart` breaks wherever its points carry the flag; the gears and power curve points never do, since they aren't built by `averaged`.

## Risks / Trade-offs

- [Pixel-to-data mapping in the connector depends on Vico's layout internals] → It uses only public `CartesianDrawingContext` members and the same formula Vico uses to place points; a chart test-renders on desktop, and a unit test covers the matching helper.
- [A gap at the very start or end of the chart, or a piece of one point] → A single point draws nothing (no segment), the same as Vico today; the marker can still read it.
- [The thinning runs are now cut at gaps, so a chart may have a few more than 300 points] → At most one extra point per gap.
