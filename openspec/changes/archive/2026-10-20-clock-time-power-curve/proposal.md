# Proposal

## Why

The power curve overstates long efforts on any ride with stops. A Garmin Edge 840 mountain bike ride with 30 minutes of timer stops shows a best 20 minutes of 195 W on the activity page, while Garmin Connect and the device's own summary in the FIT file both say 179 W. The page counts each stop as a single second, so a 20-minute window joins a climb before a stop to a climb after it, an effort that never happened. The curve's chart also spends half its width on efforts under a minute and labels each point's watts on the chart, which crowds the long durations athletes compare most.

## What Changes

- Best average power for each duration is taken over the activity's clock time: a pause counts its real length at 0 W, so no window joins efforts from either side of a stop. Time in zone and normalized power keep counting a pause once.
- The curve is read at standard durations: 1, 2, 5, 10, 20 and 30 s, 1, 2, 5, 10, 20 and 30 min, then 1, 2, 3 and 5 h, each where the activity's clock time is long enough.
- The curve's chart puts those durations equally apart along its x axis instead of on a log scale from 1 s. It drops the watt labels on the line; tapping or hovering a point still shows its duration and watts, and the screen-reader description is unchanged.
- "Best 20 min power" and the curve's 20 min point both follow the new calculation, so a ride's figure matches Garmin Connect's "Max Avg Power (20 min)".

## Capabilities

### New Capabilities

- `feed/power-curve`: How an activity's best average power for each duration is worked out and how the power curve chart lays out those durations.

### Modified Capabilities

- `feed/stream-resampling`: The power curve no longer counts a pause as one second. The requirements about pauses and sparse recording keep applying to time in zone and normalized power only.

## Impact

- `core/data` `StreamAnalytics` (`meanMax`, `CURVE_SECONDS`) and the per-second resampling the curve uses. `PerSecond.hold` stays as it is for zones and normalized power.
- `app/presenters` `ChartsBuilder` (curve input) and `MetricCards` (Best 20 min power), with their tests.
- `composeApp` `ActivityChartsUi.PowerCurve`: the x-axis mapping, tick labels and removed point labels. It still uses `TimeSeriesChart`.
- The import preview gets the same numbers, since it builds charts through `ChartsBuilder`.
- Stored data and lexicons don't change. The curve is computed when a page is shown, so already imported activities show corrected figures with no backfill.
