# Proposal

## Why

The activity page's time-series charts join every pair of samples with a line, however far apart they are. A ride whose bike computer died and whose watch took over 23 minutes later shows a long diagonal across the heart rate and power charts, as if the athlete had ridden through that stretch with values sliding from one to the other. The same happens wherever auto-pause or a sensor dropout leaves minutes without samples. The map already breaks its line where no position was recorded for 2 minutes (combined activities); the charts should read the same way.

## What Changes

- Each time-series chart on the activity page (heart rate, power, speed or pace, cadence, elevation, temperature and the other per-second metrics, a left/right pair's second line, tanks, a dive's ceiling, and the elevation drawn behind other charts) breaks its line, with no line or fill drawn, where its samples are further apart than a gap limit.
- The gap limit is 2 minutes, the map's break, or four of the stream's usual sample intervals when that is longer, so a stream logged every 30 s or 60 s still draws as one line. One-second and smart recording (1–8 s) never break short of 2 minutes.
- Gaps are found on the stream as recorded, before it is thinned to chart points, so thinning neither hides a gap nor makes one.
- It applies to every activity: single recordings with auto-pause or dropouts as well as combined ones.
- The gears chart (a value held until the next shift) and the power curve (not over time) are unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-page-layout`: a new requirement that time-series charts break at gaps in their samples.

## Impact

- `core/data`: `StreamAnalytics.averaged` (the chart's thinning) no longer averages across a gap and marks the first point after one; a gap-limit helper beside it.
- `app/screens`: `ChartSample` gains a flag for the first point after a gap.
- `app/presenters`: `ChartsBuilder` carries the flag through.
- `composeApp`: `ui/chart/TimeSeriesChart.kt` and its chart data draw the breaks; `detail/ChartUnits.kt`/`ActivityChartsUi.kt` pass them.
- No lexicon, AppView, map or data-layer storage changes.
