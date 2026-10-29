# Design

## Context

- `DecodedActivityMapper.avgTemperature` returns `sessions.weightedAverage { it.avgTemperatureCelsius }`. Garmin writes the session average as a whole °C (`sint8`), so the reference ride stores 190 tenths, although its 5,901 records average 19.24 °C.
- `ChartsBuilder.line` builds each `LineSeries(kind, points, average, max)` from a stream's present samples. `MetricCards.avgMax` turns `figures.average`/`figures.max` into a card's headline. `ActivityPage.average(kind)` prefers the record's summary and falls back to the line's.
- The Grade line is derived in `ChartsBuilder.lines` (`StreamAnalytics.grade`). Its "Avg · Max" header and average line come from the generic line rendering in `ActivityChartsUi`.
- `CycleCount.unitOf(sport)` returns steps, strokes, pushes or null. Null hides `cycles`, which is why a ride's 6,676 strokes never show.
- `Format.temperature` and the temperature `streamUnit` round to whole degrees.

## Goals / Non-Goals

**Goals:**
- Match Garmin Connect's temperature, minimum and stroke figures for the same file.
- Stop showing a misleading average grade.

**Non-Goals:**
- Backfilling the average temperature of activities already imported.
- Minimums for other streams (heart rate, power, speed, cadence): their minimum is a stop, not part of the effort.
- A combined range figure ("60.8–80.6 °F") or a range bar. The athlete chose separate Min and Max figures, which match Garmin and the cards' existing Avg/Max figures.

## Decisions

**1. Time-weighted record average at import.** `avgTemperature` averages the records' `temperatureCelsius`, each weighted by the seconds until the next record and capped by the same hold limit `PerSecond` uses, so a long pause doesn't weight one sample. It falls back to the sessions' average when no record has a temperature. TCX has no temperature, and dive logs keep their own path (`DiveLogs.summary`).
- *Rejected: preferring the line's average on the page.* That would make the hero, feed and card disagree with the record, which other clients and the AppView read.

**2. `LineSeries.min`.** We add `min: Double?` beside `max`, filled in `ChartsBuilder.line`. `MetricCards` adds `FigureKey.Lowest` to the Elevation card and `FigureKey.Min` to the Temperature and Respiration headlines, after Avg and before Max, following Garmin's Avg/Min/Max order. `ActivityPage` stats add lowest elevation and min/max temperature rows from the same lines. They exist only when the viewer was served the stream, which already gates the lines.

**3. No average for Grade.** `ChartsBuilder.line(StreamKind.Grade, …)` sets `average = null`. The chart and header code already treat a null average as "don't draw or say it" (check this; if not, add that branch generically rather than special-casing grade in the UI). This keeps `A chart's average is its headline's`, since the grade has no headline average.

**4. Pedal strokes as a `CycleCount` unit.** We add `PEDAL_STROKES` for `Sport.RIDE` (all sub-sports) and `Sport.VELOMOBILE`. Handcycles stay hidden: they're crank revolutions by hand, and calling them pedal strokes would be wrong. The stats list's Counts group (`What the Counts, Dive and Device groups hold` already lists strokes) shows it with a new `stat_pedal_strokes` plural.

**5. A Min column in the Avg/Max table.** `AvgMaxRow` gains `min: FigureValue?`, which `ActivityPage.avgMax()` fills only for temperature, from the same line minimum as decision 2. `AvgMaxTable` shows a Min column between Avg and Max only when some row has a minimum. A row without one shows an empty cell, not the missing-value dash, since nothing is missing: the metric has no minimum to show. A ride without a temperature stream keeps the two-column table.
- *Rejected: a Min column on every row.* A heart rate or power minimum is a stop, as above.

**6. One decimal for temperature.** `Format.temperature` and the temperature stream unit use `formatDecimal(…, 1)`. Stored tenths of °C convert to °F with enough precision for 80.6 °F to be exact.

## Risks / Trade-offs

- [Old and new imports of the same kind of file differ by up to half a degree] → Accepted: the spec keeps earlier activities unchanged, and re-importing gives the new figure.
- [One-decimal temperatures look busier on feed cards] → They're consistent with Garmin, and `group-digits` doesn't affect them. If it reads badly on the feed, we can round the feed alone later without changing the page.
- [Elevation minimum from the barometric stream differs a little from Garmin's min elevation] → Both read the same samples. The scenarios pin the reference ride's 4,557 ft.
