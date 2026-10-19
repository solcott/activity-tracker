# Proposal

## Why

A run's Pace card plots pace inverted, with the faster pace at the top. From 600dp it also draws the run's elevation faintly behind the line, on the backdrop's own scale. No one has looked at the two together: the sample run is flat, and the only paced activity checked so far was a pool swim, which has no elevation. The spec also doesn't say how elevation should read behind an inverted line. A climb should look like a climb even though slower pace sits lower on the same chart.

## What Changes

- On the Pace card, the elevation backdrop keeps higher ground at the top, like every other card, and is not flipped with the pace line.
- The pace line's fill stays readable over the backdrop on a hilly run. The design settles whether the fill stays, and if so how it's drawn.
- The interval run sample gains hills, so a run with pace and real elevation is on hand to check against.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-page-layout`: the Pace card requirement says how the elevation backdrop reads behind the inverted pace line.

## Impact

- `composeApp`: `ui/chart/TimeSeriesChart.kt` (fill and backdrop layering for an inverted line) and possibly `detail/ActivityChartsUi.kt` (what the Pace card passes to its chart).
- `core/activityfile` jvmTest: `WriteWorkoutSamples.intervalRun()` gains a climb.
- No data, lexicon or API changes.
