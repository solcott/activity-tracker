# Proposal

## Why

The native iOS activity page draws every card chart from `CardLines` (`:app-common-api`): its points, axes, ticks, readouts,
description, marks and header. The Compose card (`ActivityChartsUi`, `ChartUnits`) still builds the same words itself from the
series: pace values with `Format.duration`, readouts by `%1`/`%2`/`%3` replacement, unit values with `formatDecimal`, the power curve's
durations by replacing "1" in the shared label, gear labels, gas and alert marks, and time steps in `TimeSeriesChart` that differ from
`CardLines.timeTicks`. So the same chart can label its axis or read out a point differently on Android, desktop and web than on iOS.
This is the charts group of the shared-text checklist in the roadmap.

## What Changes

- **The Compose card draws `CardLines.of`.** Each line, the power curve and the gears are a `CardLine` (title, detail, `LineChart`),
  drawn by one Compose composable (`LineChartUi`, `:app-common-ui`) that lays a `LineChart` out on Vico's `TimeSeriesChart`, as the
  native `LineChartView` lays it out on Swift Charts. Its y labels, x labels, readouts, description, average, steps, backdrop, other
  lines, marks and fill come from the `LineChart`; Compose chooses no text and no axis.
- **`TimeSeriesChart` takes resolved ticks.** It draws its y labels at the axis it is given and its x labels exactly at the given
  ticks, so the activity charts' time steps are `CardLines.timeTicks`' (60 s to 12 h, at most four gaps). Its own time steps, its
  inversion and its `formatDecimal(v, 0)` fallback go: a label not yet loaded (web) is blank rather than a raw number.
- **The segment profile draws through the same composable**, which tints a chart that has bands by grade.
- **Dead code goes.** `ChartUnits.kt` (`streamUnit`, `paceUnit`, `rememberMarkerLabel`, …), the header's unused normalized-power
  branch and its string `chart_avg_np_max`.

Visible text changes, Android, desktop and web only (iOS already draws these texts and changes nothing):
- A chart over an activity's time labels its axis at `CardLines.timeTicks`' steps, placed exactly at each tick (a 1 h ride: 0:00,
  15:00, 30:00, 45:00, 1:00:00), the same steps as iOS. A step of 20 minutes is no longer used; 3, 4, 6 and 12 hours are.
- The power curve labels only 5s, 1m, 5m, 20m and 1h, as iOS does, rather than every duration it has room for, and its readout gives
  the nearest duration on the curve rather than one interpolated between two.
- A chart of several tanks no longer repeats "Tank pressure" over itself (the card's title names it); its header gives each tank's
  maximum, as on iOS.
- On the web, axis labels whose strings are still loading are blank instead of bare numbers.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: a chart's axis and labels requirement covers the activity charts' time ticks and readouts in every UI,
  and no UI writes a stand-in label of its own.
- `feed/activity-page-layout`: the shared card's charts are drawn from the same shared charts the native page draws.

## Impact

- `:ui-designsystem` (`TimeSeriesChart`, `ChartData`), `:app-common-ui` (new `LineChartUi`; `ActivityChartsUi`; `ChartUnits.kt`
  removed), `:feature-segment-ui` (`SegmentProfileChart`), `:ui-resources` (`chart_avg_np_max` removed) and the generated iOS catalog.
- Tests: Compose UI tests that the card's charts read `CardLines`' descriptions and that the time axis takes `CardLines.timeTicks`.
- Android screenshot references for the activity page, import preview, segment page and charts component.
- Roadmap `openspec/explorations/ios-native-ui.md`: the charts group ticked, and this round's findings.
