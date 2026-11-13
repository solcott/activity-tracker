# Tasks

## 1. Design system

- [x] 1.1 `TimeSeriesChart` takes `axis`, `yLabels` and `xLabels` (`AxisLabel`), draws the backdrop on its axis, and drops `xLabel`, `yLabel`, `xLabelsAt`, `inverted`, `timeValues`, its time steps and the `formatDecimal` fallback (a blank label while loading)
- [x] 1.2 Update the charts component screenshot (`ChartsScreenshots`)

## 2. Shared Compose chart

- [x] 2.1 `LineChartUi(chart)` in `:app-common-ui` lays a `LineChart` out on `TimeSeriesChart` (labels, readouts, description, average, steps, backdrop, others, marks, fill, grade bands)
- [x] 2.2 `SegmentProfileChart` draws through it

## 3. Activity charts

- [x] 3.1 `CardCharts` draws `CardLines.of` (each `CardLine` with its header), then zones and tables; remove `LineChart`, `DepthChart`, `TankPressures`, `PowerCurve`, `Gears` and `ChartUnits.kt`
- [x] 3.2 Remove `chart_avg_np_max` and regenerate the iOS resources
- [x] 3.3 Compose tests: the card's charts carry `CardLines`' descriptions and headers (`CardChartsUiTest`); a chart writes only the labels it's given, blank for none (`AxisLabelsTest`)

## 4. Verification

- [x] 4.1 Rerecord the Android screenshot references whose charts changed, look at them, and pass `:androidApp:testDebugScreenshotTestDefaultTestSuite`
- [x] 4.2 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 4.3 An ad-hoc-signed iOS Release build passes
- [x] 4.4 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 simulator (the 18.4 run on a build made for it)
- [x] 4.5 Tick charts off in the roadmap's checklist and add this round's findings

## Workflow follow-up

- Archive the change and sync its specs (`dev/shared-display-text`, `feed/activity-page-layout`).
