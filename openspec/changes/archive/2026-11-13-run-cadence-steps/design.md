# Design

## Context

Cadence comes only from imported files: no recording or form in the app writes it. FIT's `cadence` on a run is strides per minute (one
leg's cycles, with `fractional_cadence` beside it), TCX's `RunCadence` and GPX's `gpxtpx:cad` from the same devices are too, and Garmin
Connect, Strava and others show twice it as steps per minute. The app stores the file's value and writes it back unchanged on export.

## Decisions

- **Double at display, never in storage.** Doubling at import would change what every exported file says and leave old activities
  half. The page is the one place cadence is read by a person.
- **A stream kind of its own.** Units are chosen by `StreamKind` throughout `StreamUnits` and `CardLines`, without the sport. A foot
  sport's cadence becomes `StreamKind.StepCadence` (`ChartsBuilder.forSport` relabels the cadence line, doubled, and `Figures` doubles
  the record's average and maximum), with its own unit `unit_spm` and the same title and card as cadence. A sport-aware unit at each
  call would thread the sport through every text function instead.
- **On foot means `SportFamily.OnFoot`** (run, walk, hike), the family the hero already uses. Swimming's stroke rate is another
  question, left as it is.
- The laps table gets the sport's family through its existing ski flag's neighbour: a `steps` flag.
