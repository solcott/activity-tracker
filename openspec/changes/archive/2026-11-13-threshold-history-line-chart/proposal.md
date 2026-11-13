## Why

Threshold history's Compose page is the one Compose chart not drawn from its shared `LineChart`: iOS draws `ThresholdHistoryTexts.chart(section, detailed)` (steps with labelled dots), while Compose keeps its own Vico chart (`ThresholdHistoryChart.kt`) over `ThresholdChart` with its own insets, dots, labels and gridlines. Two drawings of one chart drift; the roadmap's round 2 charts findings left this open.

## What Changes

- The Compose threshold history page draws `ThresholdHistoryTexts.chart(section, detailed = true)` and, in a collapsed group's tile, `chart(section, detailed = false)` through `LineChartUi`, as iOS draws them through `LineChartView`.
- The shared Compose chart path learns what that chart needs: dots on chosen points, filled or hollow, with their labels over them (`LineChart.dots`), y labels and gridlines exactly at the chart's `yTicks` rather than at Vico's own steps, a height, and a non-interactive mode for a sparkline (no readout, no touch handling).
- The Compose page shows the lowest and highest values as the chart's y labels (as iOS does) instead of a column beside the chart, and drops the chart's own box.
- Deleted: `ThresholdHistoryChart.kt` (`ThresholdStepChart`, `ThresholdDots`), the Compose-only text wrappers it alone used (`chartDescription`, `pointLabel` in `ThresholdHistoryText.kt`), `TimeSeriesChart`'s unused `pointLabels`, and any chart helper in `:ui-designsystem` left with no caller.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: adds that both UIs draw threshold history's chart from the one shared `LineChart`, not only that shared code chooses it.

## Impact

- `:ui-designsystem` `app.ui.chart` (`TimeSeriesChart`, `StaticChart`, `PointLabels`), `:app-common-ui` `LineChartUi`, `:feature-settings-ui` threshold history.
- Android screenshot references for threshold history change (Compose only). No iOS view or snapshot changes; no contract change.
