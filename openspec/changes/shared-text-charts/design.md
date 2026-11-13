# Design

## Context

`CardLines.of(card, charts, units)` (`:app-common-api`) already builds each metric card's charts as `CardLine`s (title, detail,
`LineChart`) and the native card draws them. `LineChart` holds the points (a pace or depth negated, on an axis `ChartAxis.of` chose
inverted), `xMin`/`xMax`, the axis and its labelled `yTicks`, `xTicks`, one readout per point, the description, average, steps, a
backdrop already scaled onto the axis (`backdropOf`), other lines, marks and `fillAbove`. `SegmentProfileChart` showed the Compose
pattern: resolve the ticks and readouts, then hand `TimeSeriesChart` lookups by tick value.

## Goals / Non-Goals

**Goals:**
- No text, axis or tick place chosen in a Compose chart: every activity chart and the segment profile draw a `LineChart`.
- `TimeSeriesChart` has no rules of its own for labels (time steps, fallbacks, inversion).

**Non-Goals:**
- How Vico draws (colours, fills, the readout's look, label thinning when labels collide).
- Threshold history's Compose chart (`ThresholdChart`), its dots and sparklines: not in this group.
- The dive profile (`DepthProfile.kt`) and other dead dive code, which is the dive round's.

## Decisions

- **One Compose composable for a `LineChart`: `LineChartUi(chart, modifier)` in `:app-common-ui` (`app.ui.chart`).** `:ui-designsystem`
  has no app types, so it can't take a `LineChart`; `:app-common-ui` can, and feature UIs depend on it. It resolves `yTicks`, `xTicks`
  and `readouts` once per composition, maps marks to events and warnings, `others` to lines (in order, which is their tone in every
  chart `CardLines` makes), and, when the chart has bands, paints the line and area with `GradeBands`. The segment profile's own
  wrapper keeps only its test tag. `CardLineUi` (in `ActivityChartsUi`) adds the header: the line's title and detail when either is set.
- **`TimeSeriesChart` takes `axis: ChartAxis`, `yLabels` and `xLabels` as `AxisLabel(value, text)` lists.** The y labels are looked up
  at the axis's gridlines, the x labels placed exactly at their values (the existing `FixedXLabels`, which keeps half a label of room at
  each end and thins labels that would collide). A chart passing no x labels draws none. The `xLabel`, `yLabel`, `xLabelsAt`,
  `inverted` and `timeValues` parameters go: `LineChart` already plots an inverted line negated, and `ChartAxis` already chose its
  steps. Rejected: keeping `TimeSeriesChart`'s own `timeStep` and passing `CardLines`' step in, which would leave two rules for where
  time labels go.
- **A label not loaded yet is a blank, not a number.** Vico refuses an empty label, and on the web a string resource reads empty until
  resources load. The chart now draws a single space in its place, rather than writing `formatDecimal(v, 0)`, which was a second
  rule for the label's text (and was wrong for pace and time labels).
- **The backdrop is on the chart's own axis.** `TimeSeriesChart` scaled its backdrop by itself (lowest at the bottom, its span over 60%
  of the height, 20 m at least); `LineChart.backdropOf` does that already, so the chart draws the given points on the main axis.
- **The readout is the chart's readout for the nearest point** (`LineChart.readout(x)`), so the power curve's readout gives a
  duration on the curve rather than one Compose interpolated, as iOS does.
- **`chart_avg_np_max` is removed.** No caller passed `normalizedPower`; the card's headline gives normalized power.

## Risks / Trade-offs

- Placing time labels exactly at each tick (rather than Vico's aligned placer) adds half a label of margin at both ends of the plot,
  narrowing it slightly; the screenshot references show the new layout.
