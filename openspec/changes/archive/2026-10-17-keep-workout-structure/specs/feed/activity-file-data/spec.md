# Spec Delta

## MODIFIED Requirements

### Requirement: Laps are kept
An activity imported from FIT or TCX SHALL store each lap with its start, elapsed and moving time, distance, average and maximum speed and heart rate, average power and cadence, ascent and descent, calories, intensity (active, rest, warm-up, cool-down, recovery or another the file names) and trigger (manual, distance, time, position or another the file names), where recorded. Laps SHALL be stored with the streams, at their visibility. A file with a single lap spanning the activity SHALL store none.

#### Scenario: Interval run
- **WHEN** an athlete imports a FIT run with eight laps
- **THEN** its streams hold eight laps in order, each with its own time and distance

#### Scenario: One automatic lap
- **WHEN** an athlete imports a FIT ride with one lap spanning the ride
- **THEN** its streams hold no laps

#### Scenario: TCX intervals
- **WHEN** an athlete imports a TCX run with eight laps alternating Active and Resting intensity
- **THEN** its streams hold eight laps in order, each with its own time, distance, calories and intensity

#### Scenario: Lap triggers
- **WHEN** an athlete imports a FIT run with automatic laps every kilometer and one manual lap
- **THEN** its stored laps' triggers are distance, except the manual one's

#### Scenario: TCX with one lap
- **WHEN** an athlete imports a TCX ride with one lap spanning the ride
- **THEN** its streams hold no laps, and the lap's totals are the activity's

## ADDED Requirements

### Requirement: Lap table shows intensity
An activity's lap table SHALL mark each lap's intensity when the laps record one, so rests and warm-ups stand apart from work intervals.

#### Scenario: Intervals with rests
- **WHEN** a viewer opens a run whose stored laps alternate active and rest
- **THEN** the lap table marks each rest lap as a rest

#### Scenario: Laps without intensity
- **WHEN** a viewer opens a ride whose stored laps record no intensity
- **THEN** the lap table shows no intensity marks

### Requirement: Ski runs and lifts are kept
A ski or snowboard activity imported from FIT SHALL store each run and each lift ride it records as a lap, in order, with a lift as a rest lap, its time and its ascent or descent. These SHALL replace the file's own laps when the file records runs.

#### Scenario: Resort day
- **WHEN** an athlete imports a FIT resort ski day of eight runs and eight lift rides
- **THEN** its streams hold sixteen laps in order, the eight lift rides as rest laps

#### Scenario: Backcountry ski without runs
- **WHEN** an athlete imports a FIT backcountry ski with laps and no run splits
- **THEN** its streams hold the file's laps

### Requirement: Ski activities show their runs
A ski or snowboard activity's page SHALL show its run count among its stats, to viewers the streams admit, and its lap table SHALL label laps as runs and lifts, with each run's descent.

#### Scenario: Resort day page
- **WHEN** a viewer who may see the streams opens a ski day with eight runs stored
- **THEN** its stats show 8 runs and the lap table lists each run with its descent and each lift

#### Scenario: Viewer who may not see streams
- **WHEN** a viewer opens a Public ski day whose streams are Only me
- **THEN** no run count or lap table is shown
