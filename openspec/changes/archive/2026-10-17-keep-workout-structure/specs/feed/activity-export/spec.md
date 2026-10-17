# Spec Delta

## MODIFIED Requirements

### Requirement: TCX carries the activity's channels and summary
A TCX export SHALL be one activity with a lap per stored lap, each holding its moving time (its elapsed time when it has none, since readers take a lap's time as timer time), distance, heart rate summary, calories, intensity and trigger where stored, or one lap spanning the activity, holding the record's summary, when none are stored. Each point SHALL carry time and, where stored, position, altitude, distance, heart rate, cadence, speed and power. Its sport SHALL be Running, Biking or Other.

#### Scenario: Run as TCX
- **WHEN** an athlete exports a run with no stored laps as TCX
- **THEN** the file's activity is Running, with one lap of the run's moving time and distance

#### Scenario: Swim as TCX
- **WHEN** an athlete exports a swim as TCX
- **THEN** the file's activity sport is Other

#### Scenario: Interval run as TCX
- **WHEN** an athlete exports as TCX a run with eight stored laps alternating active and rest
- **THEN** the file's activity holds eight laps, each with its own time, distance and heart rate, the rests with Resting intensity

### Requirement: FIT is a completed activity file
A FIT export SHALL be a valid FIT activity file that Garmin Connect and Strava import as a completed activity: a file ID of type activity, a start and a stop timer event, the activity's stored laps (or one lap spanning the activity when none are stored), a session per stored leg (or one session spanning the activity when none are stored), and an activity message, all at the activity's start and end times.

#### Scenario: Run as FIT
- **WHEN** an athlete exports a run with no stored laps as FIT
- **THEN** the file passes Garmin's FIT SDK integrity check and holds one session and one lap from the run's start to its end

#### Scenario: Interval run as FIT
- **WHEN** an athlete exports as FIT a run with eight stored laps
- **THEN** the file passes Garmin's FIT SDK integrity check and holds eight laps and one session

#### Scenario: Triathlon as FIT
- **WHEN** an athlete exports as FIT a triathlon with five stored legs
- **THEN** the file passes Garmin's FIT SDK integrity check and holds five sessions, each with its leg's sport, times, distance and heart rate, the transitions with FIT's transition sport

### Requirement: An exported FIT imports back as the same activity
Importing a FIT export SHALL give back the activity's type, start, elapsed and moving time, distance, summary fields, streams (to the second), gear changes, laps with their calories, intensity and trigger, pool length and lengths, sets, legs, and dive data including every gas and each gas switch, as the app stored them. A dive stored without switch times SHALL list its later gases in the file without switching to them, and MAY come back with only its first gas.

#### Scenario: Round trip of a ride
- **WHEN** an athlete exports a ride as FIT and imports the file
- **THEN** the imported activity has the ride's type, summary, streams, gear changes and laps

#### Scenario: Round trip of a dive
- **WHEN** an athlete exports as FIT a dive with switches from 21/0 to 50/0 and imports the file
- **THEN** the imported dive has the same depth profile, summary, both gases and both switches at their times

#### Scenario: Older dive without switches
- **WHEN** an athlete exports as FIT a dive breathed on 21/0 and 50/0 with no stored switches and imports the file
- **THEN** the imported dive has the same depth profile, summary and first gas

#### Scenario: Round trip of a pool swim
- **WHEN** an athlete exports as FIT a pool swim with its pool length and 40 lengths stored and imports the file
- **THEN** the imported swim has the same pool length and 40 lengths with their strokes, stroke counts and times

#### Scenario: Round trip of a strength workout
- **WHEN** an athlete exports as FIT a strength workout with sets stored and imports the file
- **THEN** the imported workout has the same sets, exercises, reps, weights and rests

#### Scenario: Round trip of a triathlon
- **WHEN** an athlete exports as FIT a triathlon with legs and laps stored and imports the file
- **THEN** the imported activity is multisport with the same legs and laps

## ADDED Requirements

### Requirement: FIT carries a swim's lengths
A FIT export of a pool swim SHALL carry its pool length on the session and a length message per stored length, with its start, time, stroke type, stroke count and whether it was a rest.

#### Scenario: Pool swim as FIT
- **WHEN** an athlete exports as FIT a 25 m pool swim with 40 lengths stored
- **THEN** its session's pool length is 25 m and the file holds 40 length messages in order

### Requirement: FIT carries a workout's sets
A FIT export of an activity with stored sets SHALL carry a set message per stored set, with its start, time, whether it was active or a rest, and its exercise category and name, reps and weight where stored.

#### Scenario: Strength workout as FIT
- **WHEN** an athlete exports as FIT a strength workout of three bench press sets with rests between
- **THEN** the file holds three active set messages with the bench press exercise, reps and weight, and the rests between them

### Requirement: An exported TCX imports back with its laps
Importing a TCX export SHALL give back the activity's stored laps, with their times, distances, heart rate, calories and intensity.

#### Scenario: Round trip of an interval run as TCX
- **WHEN** an athlete exports as TCX a run with eight stored laps and imports the file
- **THEN** the imported run has eight laps with the same times, distances and intensities
