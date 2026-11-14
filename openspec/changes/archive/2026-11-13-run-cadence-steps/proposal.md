# Proposal

## Why

The activity page labels every cadence "rpm", so a run, walk or hike shows its cadence as "85 rpm". A foot sport's cadence isn't
revolutions, and runners read it in steps per minute. The number itself is also half what they expect: FIT, TCX and GPX files record
a foot sport's cadence per foot (strides per minute, the cycles of one leg), and Garmin and other apps show it doubled, as steps of
both feet. The bug is on `main` too; a code review of the migration branch found it.

## What Changes

- On foot (run, walk, hike), the page shows cadence as steps per minute, twice the stored value, with the unit "spm": the stats list's
  average and maximum, the cadence card's headline, chart, axis and readouts, the average/max table, and the laps table.
- Rides and every other sport keep "rpm" and the stored value.
- Nothing stored changes. Records and streams keep the cadence as the file recorded it, so exports still write it back as it came,
  and the lexicon's description of cadence fields says what is stored on foot.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `feed/activity-page-layout`: a foot sport's cadence reads in steps per minute.

## Impact

- `:app-common-api` (`StreamKind`, `FigureUnit`, `StreamUnits`, `FigureTexts`, `CardLines`), `:app-common-presenter` (`ChartsBuilder`,
  `ActivityPage`, `MetricCards`), `:ui-resources` (a new `unit_spm` string) and the generated iOS String Catalog.
- Lexicon JSON descriptions of `avgCadence`, `maxCadence` and the `cadence` stream, and their Kotlin mirror's comments; no field changes.
- Android and iOS screenshots of a run's page, if a sample shows a run's cadence.
