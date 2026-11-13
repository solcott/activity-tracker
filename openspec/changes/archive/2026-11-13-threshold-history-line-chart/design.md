## Context

`ThresholdHistoryTexts.chart(section, detailed)` (`:feature-settings-api`) builds a `LineChart`: points at `ThresholdChart`'s 0–1 placements plus a run-on point at today, `steps = true`, an axis with room for labels, `yTicks` at the lowest and highest values (detailed only) and `ChartDot`s with labels (detailed only). iOS draws it with `LineChartView`. Compose draws `ThresholdChart` itself in `ThresholdHistoryChart.kt` on Vico. `LineChartUi` (`:app-common-ui`) lays a `LineChart` on `TimeSeriesChart` (`:ui-designsystem`), which already draws steps but ignores `dots`, places y labels with Vico's step placer, has a fixed default height through `LineChartUi`, and always has a readout.

## Goals / Non-Goals

**Goals:** the Compose page draws the shared chart through `LineChartUi`; `TimeSeriesChart` draws every `LineChart` field the threshold chart uses; Compose-only chart code goes.

**Non-Goals:** changing what the chart contains (`ThresholdHistoryTexts.chart`), the iOS view, or any other chart's look.

## Decisions

- **Dots in `TimeSeriesChart`.** A new `dots: ImmutableList<SeriesDot>` (x, y, filled, resolved label or null) replaces the unused `pointLabels`. The line's Vico `PointProvider` returns a filled or hollow point by x (filled 12 dp, hollow 9 dp in the surface colour, 2 dp stroke in the line colour, as `LineChartView`'s), and `PointLabels` writes each label 4 dp above its point, the current one in `onSurface` and the rest in `onSurfaceVariant`, kept inside the chart. With dots, the layer is padded at each side so an edge dot isn't cut off. `LineChartUi` maps `chart.dots` to their points and resolves their labels in composition (a handful, unlike readouts).
- **Y labels at the chart's ticks.** The vertical axis places labels and gridlines exactly at `yLabels`' values (a placer that takes the step placer's margins), so a chart whose ticks aren't on whole steps of its axis (threshold history's lowest and highest) labels them; for every other chart `yTicks` are `axis.ticks`, the same values the step placer chose. With no y labels the axis draws no label, so a sparkline takes the full width.
- **`interactive` and `height`.** `LineChartUi(chart, modifier, height = 140.dp, interactive = true)`; not interactive passes no readout, so `StaticChart` adds no overlay and no gesture.
- **Page layout follows iOS.** The full chart is 150 dp with the start date, the undated note and Today beneath it; the side column of highest and lowest and the chart's box go, since the chart's y labels show them. The tile's sparkline is 26 dp.
- Shared look over the old one: the shared line width (1.5 dp) and fill alpha replace the page's own (2.25 dp, 0.14), and the gridlines are at the ticks rather than at quarters, matching iOS.

## Risks / Trade-offs

- [The tick placer changes every chart's y axis] → only the placement source changes; values equal the step placer's for `LineChart.of` charts. The Android references for the charts component and segment profile are checked for unintended change.
- [Vico gives a hollow dot no background of its own] → the hollow fill is the surface colour the page sits on.
