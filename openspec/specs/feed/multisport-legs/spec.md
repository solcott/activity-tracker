# feed/multisport-legs Specification

## Purpose

Keeps each leg and transition of a multisport activity, such as a triathlon, with its own sport and summary, and shows them on the activity's page, so an imported race keeps more than its combined totals.

## Requirements

### Requirement: Legs are kept
A multisport activity imported from FIT SHALL store each of its sessions as a leg, in order, with its activity type, start, elapsed and moving time, distance and heart rate summary, where recorded. Transitions SHALL be stored as legs marked as transitions. Legs SHALL be stored with the streams, at their visibility. A file of one session SHALL store no legs.

#### Scenario: Triathlon
- **WHEN** an athlete imports a FIT triathlon of a swim, a transition, a ride, a transition and a run
- **THEN** it is one multisport activity whose streams hold five legs in order: a swim, a transition, a ride, a transition and a run, each with its own time and distance

#### Scenario: Single-sport file
- **WHEN** an athlete imports a FIT run with one session
- **THEN** its streams hold no legs

### Requirement: Laps stay with their legs
A multisport activity's laps SHALL be kept as for any activity, and each lap SHALL fall within the leg it was recorded in.

#### Scenario: Run leg with lap splits
- **WHEN** an athlete imports a FIT triathlon whose run leg has five laps
- **THEN** its streams hold the five laps, each within the run leg's time

### Requirement: TCX multisport files keep every leg
A TCX file holding a multisport session SHALL import as one multisport activity, with every activity in it as a leg and every one's laps and points, not only the first.

#### Scenario: TCX duathlon
- **WHEN** an athlete imports a TCX multisport session of a run, a ride and a run
- **THEN** it is one multisport activity with three legs, and its streams span all three

### Requirement: Activity page shows legs
A multisport activity's page SHALL show a legs table, to viewers the streams admit, when legs are stored: each leg's sport, time, distance and pace or speed as fits its sport, and each transition's time.

#### Scenario: Viewer who may see streams
- **WHEN** a viewer who may see the streams opens a triathlon with legs stored
- **THEN** a legs table lists the swim, ride and run with their times, distances and paces or speed, and both transitions' times

#### Scenario: Viewer who may not see streams
- **WHEN** a viewer opens a Public triathlon whose streams are Only me
- **THEN** its stats show the combined totals and no legs table is shown

### Requirement: Older multisport activities are unchanged
Multisport activities imported before legs were kept SHALL show as before, with no legs table, and SHALL NOT be backfilled.

#### Scenario: Triathlon imported earlier
- **WHEN** a viewer opens a triathlon imported before legs were kept
- **THEN** its page shows the stats it showed before and no legs table
