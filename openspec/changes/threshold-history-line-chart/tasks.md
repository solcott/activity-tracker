## 1. Shared Compose chart

- [x] 1.1 `TimeSeriesChart`: replace `pointLabels` with `dots` (`SeriesDot`), drawing filled/hollow points on the main line and their labels, with side padding when there are dots
- [x] 1.2 `TimeSeriesChart`: y labels and gridlines at `yLabels`' values; no y label component when there are none
- [x] 1.3 `TimeSeriesChart` and `LineChartUi`: `interactive` (no readout when false) and `height`
- [x] 1.4 `LineChartUi`: pass `chart.dots` with resolved labels

## 2. Threshold history page

- [x] 2.1 `ThresholdHistoryUi`: the full chart and the tile's sparkline draw `ThresholdHistoryTexts.chart` through `LineChartUi`; drop the side column and box
- [x] 2.2 Delete `ThresholdHistoryChart.kt`, the unused text wrappers, and chart helpers left with no caller

## 3. Verify and record

- [ ] 3.1 Update Android screenshot references and review what changed
- [ ] 3.2 `./gradlew check -Pkotlin.incremental=false`, the ad-hoc Release iOS build and `xcodebuild test` on an iOS 26.0 clone
- [ ] 3.3 Look at the page on the desktop app if possible
- [ ] 3.4 Record findings in `openspec/explorations/ios-native-ui.md` and tick the open item
