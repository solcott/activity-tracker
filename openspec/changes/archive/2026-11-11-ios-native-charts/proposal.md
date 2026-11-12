# Proposal

## Why

Every page on iOS that draws a chart is still the shared Compose UI's Vico chart, so it stays a fallback page: the segment page (its
elevation profile), the activity page (its streams), threshold history and the import preview. A native chart view is what each of them
waits for. The segment page is the cheapest first page for it: it draws one chart, and everything else it shows already has a native
piece (`RouteMapView`, `PlaceNamesSection` and `placeNames(_:)`, the boards it opens, `PlaceTexts`), while the rules behind its chart
(the y axis's round steps, the distance labels, the readout, the grade bands' runs) are still worked out inside Compose code a native page
can't ask.

## What Changes

- **A native line chart on iOS** (`LineChartView` in `iosApp/iosApp/Design`), drawn with Swift Charts (the system's own): a line over
  an area filled beneath it, on the y axis and gridlines Kotlin chose, with Kotlin's x labels, optionally tinted across by bands (an
  elevation profile's grade), breaking at gaps, and a readout that follows a press or drag across it, reading Kotlin's text for the
  nearest point. It draws a Kotlin value and a few layout flags, so the activity page can place it later unchanged.
- **Kotlin decides what a chart shows, both UIs ask** (`LineChart` in `:app-common-api`, package `app.chart`): the points, the x range,
  the y axis (round steps, moved out of `TimeSeriesChart` into `:core-data`'s `ChartAxis`, which the Compose chart asks too), the x
  ticks and every label, the readout of each point, the description read to assistive technology, and the band stops (the grade bands'
  run rule, moved out of `GradeBands` into `:core-data`). A segment's profile becomes one (`SegmentProfile.chart(units)`), and the
  Compose profile chart draws the same value. Nothing changes on Android, desktop or web.
- **The segment page is native on iOS** (`SegmentPage`), registered in `ScreenRegistry`: an inset grouped list titled with the
  segment's current name, Star in the bar, its line on the native map (expand opens the enlarged map, which stays the shared UI's),
  "originally" its old name, its length and type with the ski kind's icon, its gain, loss and average grade, its high and low points
  and maximum grade, the native elevation chart, who created it, its data credit and licence, its status with Open the original, the
  Leaderboard button and the Mayor card for an active run, the names (`PlaceNamesSection`, not for a lift), then the viewer's efforts
  as rows (date, PR, rank, why not ranked, e-bike, time) or that they have none. The place-name sheet shows over it.
- **Kotlin chooses what the page says, both UIs ask** (`SegmentPageTexts` in `:feature-segment-api`, and `EffortTexts` in
  `:app-common-api` for an effort row, which the activity page's rows ask too): the star's label, the length and type line, the
  elevation and extremes lines, the credit lines, the Mayor card's lines, the efforts' heading and empty message, and an effort row's
  date, rank, PR, reasons and time. The Compose segment page asks the same.
- **Samples, previews and snapshots** of the page in every shared sample (loading, failed, loaded, refreshing, renamed, voting
  unavailable, elevation with a profile, elevation from efforts, a lift) and new variants (an OpenStreetMap segment with Mayor and
  efforts, a duplicate), light and dark; a chart gallery in the design snapshots; the registry entry and its test.

What waits (open items in the roadmap):

- **Threshold history** keeps the shared UI: its page is a step chart per threshold with dots and value labels over each point, a
  sparkline overview per collapsed group, expand and collapse, and removal; the chart view can grow a step style and point labels for
  it, in a change of its own.
- **The activity page** (many charts, zones, the power curve), **the import preview's charts** and **the enlarged map** stay shared.

Out of scope: any change to Android, desktop or web beyond the shared rules moving; Vico's removal from iOS (phase 5).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui/ios-design-system`: a native line chart that draws the shared chart value, with a readout.
- `segments/segment-efforts`: the segment page is native on iOS.
- `segments/segment-elevation`: the segment page's elevation profile is drawn natively on iOS, with the same axis, labels, bands and
  readout.
- `dev/shared-display-text`: a chart's axis, labels and readout, and the segment page's and an effort row's text, are chosen in shared
  code.

## Impact

- **`:core-data`:** `ChartAxis` (from `TimeSeriesChart`'s `YAxis`) and `BandStop` with its run rule (from `GradeBands`); tests.
- **`:ui-designsystem`:** `TimeSeriesChart` asks `ChartAxis` and takes an optional axis; `GradeBands` keeps its colours and brush.
- **`:app-common-api`:** `LineChart`, `ChartPoint`, `ChartTick`; `EffortTexts`; tests.
- **`:app-common-ui`:** `EffortRow` asks `EffortTexts`.
- **`:feature-segment-api`, `-ui`:** `SegmentProfile.chart`, `SegmentPageTexts`; `SegmentProfileChart`, `SegmentUi`, `Credit`,
  `MayorCard` ask them.
- **`:app-fixtures`:** `SegmentSamples.variants`.
- **`iosApp`:** `LineChartView` (Swift Charts), `GradeColors`, `SegmentPage`, `EffortRowView`, a shared leader card, type aliases,
  samples, previews, snapshot and text tests, the registry entry.
- **Docs:** the roadmap (phase 4 entry, findings, open items); `.agents/rules/compose-screens.md` and `adaptive-ui.md`.
- **Risk carried:** Swift Charts lays out ticks and the area itself, so the native chart looks close to, not pixel-equal with, Vico's;
  snapshots compare only on iOS 26.0; the walk can only show segments the local stack has.
