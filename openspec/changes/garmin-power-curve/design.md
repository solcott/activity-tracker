# Design

## Context

`ChartsBuilder.build` turns the power stream into one value per recorded second with `StreamAnalytics.perSecond`, which calls `PerSecond.hold`: a sample holds across a gap of up to 5 s (or two median intervals), and a longer gap adds one second. The same per-second list feeds normalized power and `StreamAnalytics.meanMax`, which slides a window over it for each of `CURVE_SECONDS`. `MetricCards` takes Best 20 min power as the curve's last point between 60 and 1,200 s. `ActivityChartsUi.PowerCurve` plots `log10(seconds)` from 0 (1 s) to the last point, labels the axis at 5 s, 1 m, 5 m, 20 m and 1 h, and draws a watt label at each of those points.

We checked this against the reference ride: Garmin Edge 840 file `24785356441_ACTIVITY.fit`, 5,901 records over 7,701 s of clock time, 14 gaps over 5 s totalling 1,814 s.

| Method | 5 min | 20 min | 1 h |
|---|---|---|---|
| Today (pause = 1 s) | 219 W | 195 W | 159 W |
| Clock time, pause = 0 W | 219 W | **179 W** | 133 W |
| Windows confined to one unbroken stretch | 219 W | none (longest stretch 19:47) | none |
| Garmin Connect / FIT msg 113 (`1200000 ms → 179 W`) | | **179 W** | about 135 W |

Garmin's own chart for this ride also reaches 2 h on a ride with 1:38 of timer time, which only clock-time windows can produce.

## Goals / Non-Goals

**Goals:**
- Match Garmin's best-power figures on rides with stops.
- Match Garmin's even spacing of durations on the curve.

**Non-Goals:**
- Changing normalized power or time in zone. They keep `PerSecond.hold`, and the NP of 177 W already matches Garmin.
- Reading Garmin's device-computed best-power messages (FIT msg 113). They are undocumented, give only one duration, and only exist in Garmin files.
- Records or comparison curves (Garmin's grey "Record" area).

## Decisions

**1. A separate clock-time resampler for the curve.** We add `StreamAnalytics.clockSeconds(time, values)`, which returns one value per second from the first sample to the last. Each sample holds until the next while the gap is within the stream's hold limit (the same rule as `PerSecond.hold`, through its `MAX_HOLD_SECONDS`/`HOLD_INTERVALS`). A longer gap fills with 0 W for its real length. A null sample is skipped as it is today, so a short dropout holds and a long one fills with 0. `ChartsBuilder` passes this list to `meanMax`, and NP keeps `perSecond`.
- *Rejected: making `PerSecond.hold` fill pauses.* That would change time in zone and NP, which the existing spec deliberately keeps free of stopped time.
- *Rejected: windows confined to unbroken stretches.* That gives no 20 min figure at all for this ride, and Garmin gives 179 W.

**2. The duration list becomes Garmin's.** `CURVE_SECONDS` = 1, 2, 5, 10, 20, 30, 60, 120, 300, 600, 1200, 1800, 3600, 7200, 10800, 18000. Garmin's list stops at 2 h on this ride. We keep 3 h and 5 h for longer days, since Garmin extends the same way. `meanMax` already drops durations longer than its input, and that input is now the clock span, so a ride with 2:08 of clock time gets a 2 h point.

**3. An index axis with log interpolation.** `PowerCurve` maps each curve point to its index (0, 1, 2, …) as its x. A marker x between index i and i+1 converts back to seconds by log interpolation between those two durations, so hover reads sensible values such as "40s". Axis labels sit at the integer indices (`xLabelsAt`). At compact widths the labels thin to every other one, always keeping the first and last. The chart keeps `TimeSeriesChart`, so the theme, the marker and the content description stay shared.
- *Rejected: log axis starting at 5 s.* Under a minute still takes 38% of the width, and it isn't what Garmin does.

**4. No point labels.** `pointLabels` is dropped for the curve. The screen-reader description keeps `CURVE_STATS` (5 s, 1 m, 5 m, 20 m, 1 h).

**5. Combined activities.** Their streams' `time` runs across the gap between recordings, which now fills with 0 W. That cannot raise a best figure. It only lets long durations exist across the gap. We accept that rather than special-casing it, because it is what clock time means and Garmin shows multi-hour points the same way.

## Risks / Trade-offs

- [Activities with long stops show lower long-duration figures than before] → This is the intended correction, and the scenarios pin the Garmin numbers.
- [A combined activity hours apart gets 3 h and 5 h points that are mostly zeros] → They're labelled honestly and never overstate. If it looks odd, we can cap the curve at the summed recording spans later without changing the spec's single-recording behavior.
- [Memory: a five-hour clock span is 18,000 ints] → That's trivial, and `buildOffMain` already runs off the main thread.

## Migration Plan

Nothing is stored, so nothing migrates. Every activity page and import preview recomputes on its next view.
