# Proposal

## Why

Comparing a mountain bike ride against Garmin Connect turned up summary figures that are wrong, misleading or missing even though the data is already in the app:

- The average temperature reads 66 °F where Garmin says 66.6 °F, because import takes the FIT session's whole-degree average (19 °C) instead of averaging the samples (19.24 °C).
- The Grade chart's "Avg 2.7%" comes from averaging per-sample grade over time, so slow climbs outweigh fast descents. A loop ending where it started reads as uphill.
- The lowest elevation (4,557 ft), lowest temperature (60.8 °F) and lowest respiration rate (16 brpm) are in the streams but never shown. For metrics that vary over a range like these, an average and a maximum without the minimum give only half the range.
- A ride's pedal strokes (6,676) are already stored as `cycles` but deliberately hidden.

## What Changes

- FIT import averages the temperature samples, weighted by time, and uses the session's average only when the records carry none. This applies to new imports; earlier activities keep their stored average.
- The activity page shows temperatures to one decimal, so whole-degree Celsius samples read exactly in Fahrenheit (27 °C = 80.6 °F), as on Garmin Connect.
- The Grade chart shows its maximum only: no average figure, no average line, and none in its screen-reader description.
- The Elevation card adds the lowest point beside the highest. The Temperature and Respiration cards add a minimum to their headlines. The stats list's Elevation and Temperature groups gain lowest elevation and minimum and maximum temperature rows. The wide-window Avg/Max table gains a Min column between Avg and Max. It is filled for temperature and left empty for heart rate, power, speed or pace and cadence, whose minimum is just a stop. So wherever the page gives the average and maximum of temperature, respiration or elevation, it gives the minimum too, as separate figures in Garmin's Avg, Min, Max order.
- Cycling activities show `cycles` as pedal strokes in the Counts group.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/activity-file-data`: The imported average temperature comes from the samples. Counts by sport now show a ride's `cycles` as pedal strokes.
- `feed/activity-page-layout`: The Grade chart drops its average. The Elevation, Temperature and Respiration cards, the stats list and the Avg/Max table show minimums. Temperatures show one decimal.

## Impact

- `core/activityfile` `DecodedActivityMapper.avgTemperature` (time-weighted record average) and its tests.
- `core/lexicon` `CycleCount` gets a pedal-strokes unit for cycling sports. Its token is code only: the `cycles` field and its description already cover crank revolutions.
- `app/presenters` `ChartsBuilder.line` (minimum per series, no average for grade), `MetricCards` (new figures), `ActivityPage` (stats rows and the table's minimum), `AvgMaxRow.min`, and `app/screens` `FigureKey` entries.
- `composeApp` labels and strings for the new figures, and the Min column in `AvgMaxTable` (`PageCardsUi`).
- No lexicon schema, AppView or stored-data changes. Existing activities need no backfill: their minimums, grade and strokes come from data already stored. Only their average temperature stays as imported.
