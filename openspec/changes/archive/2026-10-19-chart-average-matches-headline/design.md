# Design

## Context

- Each card's headline comes from `Figures.average(kind)` and `Figures.max(kind)`, which take the record's stored value (`avgHeartRate`, `avgPower`, `avgSpeed`, `avgCadence`, `avgTemperature`; the maxima for the first four) and fall back to the stream's.
- The chart takes `LineSeries.average` and `LineSeries.max`, which `ChartsBuilder` computes from the samples. `LineChart` uses them for the dashed average line, the description and (on the Pace card) the pace cap.
- Records and streams keep these metrics in the same units, so the stored value can stand in for the stream's without conversion.

## Goals / Non-Goals

**Goals:**
- One average per card, wherever the card shows it.

**Non-Goals:**
- Changing how either average is computed, or which one the headline prefers.
- Lines without a stored figure (running dynamics, physiology, dive streams, e-bike, left/right balance), or a second line on a card (grade under elevation).

## Decisions

### 1. The presenter hands the card a series carrying the headline's average and maximum

`MetricCards.card` copies each series whose kind has a stored figure, replacing `average` and `max` with `Figures.average(kind)` and `Figures.max(kind)`. `ActivityCharts.lines` stays as it is, so other uses (stats rows from a stream, the avg/max table, the Elevation card's highest point) keep the stream's own figures. The UI needs no change, and the rule is tested on the JVM with the rest of the page model.

- *Alternative:* have the UI read the headline figure out of `card.headline`. That ties the chart to the headline's keys and value types (a pace headline holds a `Pace`, not a speed), and puts a rule in the UI that `ActivityPageTest` can't see.

### 2. A metric with a stored average but no stored maximum keeps the stream's maximum

Temperature has `avgTemperature` but no stored maximum, so its chart keeps `LineSeries.max`, which the headline also falls back to. The copy replaces each figure only where `Figures` returns a value, so the stream's figure stays whenever the record has none.

### 3. A pace is rounded once, from the speed

The value now matches, but the text could still differ by a second. The page's pace figure truncated the seconds per kilometer, then converted to the athlete's unit and rounded, while the chart converts the speed to seconds per unit and rounds once. On the interval run's best pace that gave 5:57 /mi in the headline and 5:58 /mi in the chart's description. The page's figure now rounds once from the speed, as the chart does, so the same speed always reads the same.

## Risks / Trade-offs

- [A stored average outside the plotted range, from a file whose summary disagrees with its samples] → The dashed line still sits on the axis's range, since that's computed from the points; it can sit near an edge. It's the figure the headline already shows, so the card is at least consistent with itself.
