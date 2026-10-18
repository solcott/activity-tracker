# Spec Delta

## ADDED Requirements

### Requirement: FIT carries dive computer settings
A FIT export of a dive SHALL write, where stored, its deco model, ppO₂ warning, critical and deco limits, closed-circuit low and high setpoints and safety stop time in its `dive_settings`, beside the gradient factors and water type. A deco model FIT has no value for SHALL NOT be written. A stored safety stop time SHALL write the safety stop as enabled.

#### Scenario: Dive with settings
- **WHEN** an athlete exports as FIT a dive with deco model `zhl16c`, ppO₂ limits of 1400, 1600 and 1600 millibar and a 180-second safety stop
- **THEN** its `dive_settings` has model `zhl_16c`, `po2_warn` 1.4, `po2_critical` 1.6, `po2_deco` 1.6, the safety stop enabled and `safety_stop_time` 180

#### Scenario: Deco model FIT can't name
- **WHEN** an athlete exports as FIT a dive with deco model `vpmB`
- **THEN** its `dive_settings` has no model, and its other settings are written

### Requirement: FIT carries dive alerts
A FIT export of a dive with stored alerts SHALL hold a `dive_alert` event at each alert's time, naming the alert's FIT value. An alert of a kind FIT has no value for SHALL NOT be written.

#### Scenario: No-deco limit reached
- **WHEN** an athlete exports as FIT a dive with an `ndlReached` alert at 1500 seconds
- **THEN** the file has a `dive_alert` event of `ndl_reached` at 1500 seconds

#### Scenario: UDDF-only alert
- **WHEN** an athlete exports as FIT a dive with a `microbubbles` alert from a UDDF import
- **THEN** the file has no event for it

### Requirement: UDDF carries dive alerts
A UDDF export of a dive with stored alerts SHALL put an `alarm` on the first waypoint at or after each alert's time, naming the UDDF alarm the alert's kind maps to. An alert of a kind with no UDDF alarm SHALL NOT be written.

#### Scenario: Ascent alert
- **WHEN** an athlete exports as UDDF a dive with an `ascentCritical` alert at 2400 seconds
- **THEN** the waypoint at 2400 seconds has an `alarm` of `ascent`

#### Scenario: FIT-only alert
- **WHEN** an athlete exports as UDDF a dive with a `batteryLow` alert
- **THEN** the file has no alarm for it

### Requirement: An exported FIT dive imports back with its dive data
Importing a FIT export of a dive SHALL give back every gas, each gas switch, each alert FIT can name and each dive computer setting FIT can hold, as the app stored them. A dive stored without switch times SHALL list its later gases in the file without switching to them, and MAY come back with only its first gas.

#### Scenario: Round trip of a dive
- **WHEN** an athlete exports as FIT a dive with switches from 21/0 to 50/0 and imports the file
- **THEN** the imported dive has the same depth profile, summary, both gases and both switches at their times

#### Scenario: Round trip of a dive's alerts and settings
- **WHEN** an athlete exports as FIT a dive with `ndlReached` and `safetyStopComplete` alerts, deco model `zhl16c`, ppO₂ limits and setpoints, and imports the file
- **THEN** the imported dive has both alerts at their times and the same deco model, ppO₂ limits and setpoints

#### Scenario: Older dive without switches
- **WHEN** an athlete exports as FIT a dive breathed on 21/0 and 50/0 with no stored switches and imports the file
- **THEN** the imported dive has the same depth profile, summary and first gas

## MODIFIED Requirements

### Requirement: An exported FIT imports back as the same activity
Importing a FIT export SHALL give back the activity's type, start, elapsed and moving time, distance, summary fields, streams (to the second), gear changes, laps with their calories, intensity and trigger, pool length and lengths, sets and legs, as the app stored them. A dive's own data comes back as the next requirement says.

#### Scenario: Round trip of a ride
- **WHEN** an athlete exports a ride as FIT and imports the file
- **THEN** the imported activity has the ride's type, summary, streams, gear changes and laps

#### Scenario: Round trip of a pool swim
- **WHEN** an athlete exports as FIT a pool swim with its pool length and 40 lengths stored and imports the file
- **THEN** the imported swim has the same pool length and 40 lengths with their strokes, stroke counts and times

#### Scenario: Round trip of a strength workout
- **WHEN** an athlete exports as FIT a strength workout with sets stored and imports the file
- **THEN** the imported workout has the same sets, exercises, reps, weights and rests

#### Scenario: Round trip of a triathlon
- **WHEN** an athlete exports as FIT a triathlon with legs and laps stored and imports the file
- **THEN** the imported activity is multisport with the same legs and laps

### Requirement: An exported UDDF imports back as the same dive
Importing a UDDF export SHALL give back the dive's start (read in the zone it was written in), duration, summary fields above, site name, gases, gas switches, tanks, alerts UDDF can name and depth, temperature, heart rate, ppO₂, CNS, no-deco, deco stop and tank pressure streams, as the app stored them.

#### Scenario: Round trip of a deco dive
- **WHEN** an athlete exports as UDDF a dive with switches from 21/0 to 50/0, two tanks and deco stop streams, and imports the file
- **THEN** the imported dive has the same start, depth profile, summary, gases, switches at their times, tanks and deco streams

#### Scenario: Round trip of a dive's alerts
- **WHEN** an athlete exports as UDDF a dive with an `ascentCritical` alert at 2400 seconds and imports the file
- **THEN** the imported dive has an `ascentCritical` alert at 2400 seconds

#### Scenario: Round trip of an older dive
- **WHEN** an athlete exports as UDDF a dive with no stored switches, deco state or tank pressure series, and imports the file
- **THEN** the imported dive has the same start, depth profile, summary and first gas
