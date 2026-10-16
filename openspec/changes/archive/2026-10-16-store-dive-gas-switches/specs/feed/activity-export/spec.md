# Spec Delta

## MODIFIED Requirements

### Requirement: FIT is a completed activity file
A FIT export SHALL be a valid FIT activity file that Garmin Connect and Strava import as a completed activity: a file ID of type activity, a start and a stop timer event, the activity's stored laps (or one lap spanning the activity when none are stored), one session spanning the activity, and an activity message, all at the activity's start and end times.

#### Scenario: Run as FIT
- **WHEN** an athlete exports a run with no stored laps as FIT
- **THEN** the file passes Garmin's FIT SDK integrity check and holds one session and one lap from the run's start to its end

#### Scenario: Interval run as FIT
- **WHEN** an athlete exports as FIT a run with eight stored laps
- **THEN** the file passes Garmin's FIT SDK integrity check and holds eight laps and one session

### Requirement: FIT records carry the activity's channels
Each point of a FIT export SHALL be a record with its time and, where stored, position, altitude, distance, speed, heart rate, cadence, power, temperature, depth, respiration rate, running dynamics, left/right balance, ppO₂, CNS, N₂ load, no-deco limit, deco stop depth and time, and time to surface, in FIT's own units and scales.

#### Scenario: Ride with power
- **WHEN** an athlete exports a ride with a track and heart rate, cadence and power streams as FIT
- **THEN** each record carries its position, heart rate, cadence and power

#### Scenario: Indoor ride
- **WHEN** an athlete exports an indoor ride with no track as FIT
- **THEN** its records carry the streams' channels and no position

#### Scenario: Deco dive
- **WHEN** an athlete exports as FIT a dive with ppO₂, no-deco limit and deco stop streams
- **THEN** each record carries its depth, ppO₂, no-deco limit and next stop depth and time

### Requirement: FIT session carries the summary and sport
The FIT session and lap SHALL hold the activity's sport and sub-sport, elapsed time, moving time (its elapsed time when it has none), distance, elevation gain and loss, average and maximum speed, heart rate summary, cadence summary, power summary (average, maximum, normalized, work), calories, shift counts, average temperature and, for the owner's file, training load, where stored. An activity type FIT has no sport for SHALL be written with FIT's nearest sport, or generic when there is none.

#### Scenario: Trail run
- **WHEN** an athlete exports a trail run as FIT
- **THEN** its session's sport is running and its sub-sport trail

#### Scenario: Moving time
- **WHEN** an athlete exports as FIT an activity with 50 minutes elapsed and 45 moving
- **THEN** its session's elapsed time is 50 minutes and its timer time 45

#### Scenario: Type FIT doesn't know
- **WHEN** an athlete exports as FIT an activity whose type has no FIT sport
- **THEN** its session's sport is generic

#### Scenario: Ride with power and training load
- **WHEN** an athlete exports as FIT a ride with a stored power summary, calories, FTP and TSS
- **THEN** its session carries the power summary, calories, threshold power and training stress score

### Requirement: FIT carries a dive's summary
A FIT export of a dive SHALL hold the dive's summary (maximum and average depth, bottom time, surface interval, dive number, start and end CNS, start and end N₂, OTU, ascent and descent rates, descent time), each gas breathed with its oxygen and helium, the gradient factors and water type and density, and each tank's pressures and gas used, where the record has them. Other activities SHALL hold none of these.

#### Scenario: Dive with two gases
- **WHEN** an athlete exports as FIT a dive breathed on air and on 50% nitrox
- **THEN** the file holds its dive summary and two gases, 21/0 and 50/0

#### Scenario: Run
- **WHEN** an athlete exports a run as FIT
- **THEN** the file holds no dive summary or gases

#### Scenario: Salt water dive
- **WHEN** an athlete exports as FIT a dive stored as salt water
- **THEN** its dive settings say salt water

### Requirement: An exported FIT imports back as the same activity
Importing a FIT export SHALL give back the activity's type, start, elapsed and moving time, distance, summary fields, streams (to the second), gear changes, laps, and dive data including every gas and each gas switch, as the app stored them. A dive stored without switch times SHALL list its later gases in the file without switching to them, and MAY come back with only its first gas.

#### Scenario: Round trip of a ride
- **WHEN** an athlete exports a ride as FIT and imports the file
- **THEN** the imported activity has the ride's type, summary, streams, gear changes and laps

#### Scenario: Round trip of a dive
- **WHEN** an athlete exports as FIT a dive with switches from 21/0 to 50/0 and imports the file
- **THEN** the imported dive has the same depth profile, summary, both gases and both switches at their times

#### Scenario: Older dive without switches
- **WHEN** an athlete exports as FIT a dive breathed on 21/0 and 50/0 with no stored switches and imports the file
- **THEN** the imported dive has the same depth profile, summary and first gas

### Requirement: Rebuilt files say what they lack
Before exporting, the app SHALL say that the file is rebuilt from what the app stores and lacks sub-second timing and fields the app doesn't keep, and also laps when the activity has none stored.

#### Scenario: Choose a format
- **WHEN** an athlete opens Export on an activity with no stored laps
- **THEN** the formats are listed with a note that the file is rebuilt and lacks laps and other fields the app doesn't keep

#### Scenario: Activity with laps
- **WHEN** an athlete opens Export on an activity with stored laps
- **THEN** the note says the file is rebuilt and lacks fields the app doesn't keep, and doesn't say it lacks laps

## ADDED Requirements

### Requirement: FIT carries gas switches
A FIT export of a dive with stored gas switches SHALL hold a `dive_gas_switched` event at each switch's time, naming the `dive_gas` slot of the gas switched to. The first switch, at time 0, SHALL name the first gas slot.

#### Scenario: Switch to deco gas
- **WHEN** an athlete exports as FIT a dive that switches from 21/0 to 50/0 at 1920 seconds
- **THEN** the file has a `dive_gas_switched` event at 1920 seconds naming the 50/0 slot

### Requirement: FIT carries tank pressure
A FIT export of a dive with stored tank pressure series SHALL hold a `tank_update` message for each tank at each second with a reading, the tanks told apart by sensor.

#### Scenario: Two tanks
- **WHEN** an athlete exports as FIT a dive with two tank pressure series and imports the file
- **THEN** the imported dive has the same two pressure series
