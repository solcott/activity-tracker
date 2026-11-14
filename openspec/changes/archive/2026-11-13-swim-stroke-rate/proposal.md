# Proposal

## Why

`run-cadence-steps` made a foot sport's cadence read in steps per minute and left swims as they were: a swim's cadence, its stroke rate,
still reads "rpm" on the activity page. A swimmer's arms don't revolve, and every swim app gives the figure as strokes per minute.

## What Changes

- A swim's cadence reads as strokes per minute ("strokes/min") everywhere the page shows cadence: the stats list, the cadence card and
  its chart, the average/max table and the laps table. The number is the stored one: FIT records a swim's cadence as strokes per
  minute, and Garmin shows it unchanged.
- `ActivityCharts` names the cadence's kind (`cadenceKind`) instead of `run-cadence-steps`' steps flag, so the laps table takes its
  unit from the same place as the chart.
- Rides and every other sport keep "rpm"; foot sports keep steps per minute.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/activity-page-layout`: a swim's cadence reads in strokes per minute.

## Impact

- `:app-common-api` (`StreamKind`, `FigureUnit`, `StreamUnits`, `FigureTexts`, `ActivityCharts`, `CardLines`), `:app-common-presenter`
  (`ChartsBuilder`, `ActivityPage`, `MetricCards`, `PageSections`), `:app-common-ui` (`LapTableUi`), a `unit_strokes_per_minute`
  string and the generated iOS String Catalog. Nothing stored changes.
