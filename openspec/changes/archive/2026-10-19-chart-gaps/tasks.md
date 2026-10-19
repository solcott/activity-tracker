# Tasks

## 1. Gaps in the chart data

- [x] 1.1 Add the gap limit (`max(CombinedRoute.BREAK_SECONDS, 4 × usual interval)` of present samples) to `StreamAnalytics`, make `averaged` cut runs at gaps and mark the first point after each, and cover it in `StreamAnalyticsTest` (a 1 s stream with a 10-minute gap, a 90 s stop, 1–8 s smart recording, 60 s steady logging, a null-value dropout, and a thinned stream whose run would straddle the gap); verify `./gradlew :core-data:jvmTest --tests "*.StreamAnalyticsTest"` passes
- [x] 1.2 Add `gapBefore` to `ChartSample` and carry it from `averaged` through `ChartsBuilder` (lines and the dive ceiling); verify `./gradlew :app-presenters:jvmTest` passes

## 2. Breaks in the chart

- [x] 2.1 Add `gapBefore` to `SeriesPoint`, map it in `rememberPoints`, and add the gap connector (with a pure helper matching a canvas x to a gap point) to `ui/chart`; cover the helper in a `composeApp` jvmTest; verify it passes
- [x] 2.2 In `TimeSeriesChart`, break the main line's stroke, each other line and the backdrop at their gaps, and draw the main line's fill in its own marker-less layer that drops to its baseline at gaps; verify `./gradlew :composeApp:jvmTest` passes
- [x] 2.3 On desktop, open a ride with a 23-minute gap in heart rate and power and screenshot its charts; verify the lines and fills break across the gap and a regular 1 s ride's charts draw unbroken; record the finding in this task
  - Finding: rendered headlessly on desktop Skia (`ImageComposeScene`, a throwaway jvmTest, not kept), since the running desktop app belongs to the main checkout and holds the shared app directory and OAuth port. Data went through `StreamAnalytics.averaged`: 1:40 at 1 s, a 23-minute gap, then 1:00 of 1–4 s smart recording. Heart rate and power lines and fills stop at 100 min and resume at 123 min with nothing drawn between; the elevation backdrop breaks at the same place; an inverted depth chart (filled from the top) with a second line, and an inverted pace chart, break the same way. A 1 s ride with a 90 s stop draws unbroken. The same data with the flags dropped shows the old diagonal.

## 3. Checks

- [x] 3.1 Run `./gradlew check composeStabilityCheck` and verify it passes
