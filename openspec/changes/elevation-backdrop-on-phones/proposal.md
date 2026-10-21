# Proposal

## Why

Phones draw no elevation behind the activity page's charts. On a Pixel 9 Pro XL the backdrop added about 2 ms to each chart's draw and pushed a quarter of draws past 16 ms, so `activity-detail-redesign` kept it for windows 600dp and wider. The backdrop shows a single faint fill, so that cost comes from how it is drawn, not from what it shows. Vico draws each line layer series through full-canvas software bitmaps. The backdrop is a series of its own with an invisible stroke, so it pays that whole cost, and so does the separate fill under a line with gaps. Drawn straight onto the hardware canvas, both should cost close to nothing, and phones can have the backdrop again.

## What Changes

- `TimeSeriesChart` draws the elevation backdrop and the gap fill under a line as Vico decorations on the host's canvas, not as line layers. They look the same: same scale, same order (backdrop, fill, line), same breaks at gaps, and the marker still ignores both.
- Once a measurement on the Pixel 9 Pro XL confirms the saving, the activity page and the import preview show elevation behind charts at every width, phones included.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/activity-page-layout`: The elevation backdrop appears at every width, not only from 600dp. The phone scenarios of "Metric card contents" and "Pace card" now expect elevation behind the chart.

## Impact

- `composeApp` `ui/chart`: a new area decoration, and `TimeSeriesChart` without `rememberBackdropLayer`, `fillLayer`, `fillOnly` and `UnmarkedLayer`.
- `composeApp` `detail`/`record`: the `elevationBehind` parameter on `CardCharts`, `MetricCardUi`, `metricCards` and `PreviewCards` goes away, along with its width-based callers in `ActivityDetailUi` and `ImportPreviewUi`.
- No data, lexicon, AppView or dependency changes. Vico stays on 2.5.2.
- Out of scope: the main line's own Vico bitmap cost, which is already near a 120 Hz frame (7.7 ms median) without the backdrop.
