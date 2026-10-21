# Proposal

## Why

Dragging the marker across a chart on the activity page stutters on Android. On a Pixel 9 Pro XL (release build, 120 Hz), three 4-second drags across the heart rate chart dropped 16–22% of frames, at 13–14 ms per frame (p50) against an 8.3 ms budget. The elevation backdrop makes no difference (`elevation-backdrop-on-phones`, design.md, Measurements). The cause is how Vico 2.5.2 handles the marker. The marker's position is part of the chart's state, so every move redraws the whole chart: each line through full-canvas software bitmaps, and Vico's own full-canvas layer bitmap is cleared and uploaded again. Only the marker changes while scrubbing, so only the marker should be redrawn. Vico 2.5.2 is its latest release, so no upgrade fixes this.

## What Changes

- `TimeSeriesChart` (the activity page's and import preview's metric charts, the power curve included) and `DepthProfile` stop using Vico's marker. A readout overlay on top of the chart draws the marker in its own layer, so moving it redraws only the marker and never the chart below.
- The readout looks and behaves as it does today: a guideline, a dot on the line, and a bubble with the time and value. It appears on hover, tap and drag, stays put after a tap or drag, and hides when a drag starts mostly vertical so the page scrolls. It snaps to the main line's nearest point, with a dot on each other line that has a point at that time.
- The before and after scrubbing frames on the Pixel are recorded in design.md.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/activity-page-layout`: adds a requirement for reading a chart's values by hovering, tapping or dragging, including that scrubbing keeps up with the display. No requirement for this exists today, apart from the power curve's own hover readout in `feed/power-curve`, which keeps working unchanged.

## Impact

- `composeApp` `ui/chart`: `ChartMarker.kt` (`rememberValueMarker` and `ScrubController`) is replaced by a readout overlay with Vico-free gesture and placement logic. `StaticChart` hosts that overlay. `TimeSeriesChart` and `DepthProfile` pass their points and y range to it instead of a Vico marker. A small decoration hands the overlay the chart's layer frame, reusing `AreaFrame` from `AreaUnderlay`.
- `composeApp` `commonTest` `ui/chart`: tests for finding the nearest point, placing the label, and the gesture rules.
- No data, lexicon, AppView or dependency changes. Vico stays on 2.5.2. `ThresholdHistoryChart` has no marker and is unchanged.
- Out of scope: what the main line costs to draw the first time and while the page scrolls. Scrolling already measures 1.1% janky frames.
