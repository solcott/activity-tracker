# Spec Delta

## ADDED Requirements

### Requirement: Exported local times use the activity's UTC offset
An export SHALL write local times in the activity's stored UTC offset: the FIT activity message's local timestamp, a UDDF dive's start and the file name's date. An activity without a stored offset SHALL use the device's current zone, as before.

#### Scenario: FIT ride from another zone
- **WHEN** an athlete in UTC−6 exports as FIT a ride with a stored UTC offset of +2:00 that ended at 10:00 UTC
- **THEN** the file's activity message's local timestamp is 12:00

#### Scenario: Late-night run near midnight
- **WHEN** an athlete in UTC−8 exports a run with a stored UTC offset of +1:00 that started at 23:30 UTC on 14 October
- **THEN** the file name's date is 2026-10-15

#### Scenario: No stored offset
- **WHEN** an athlete exports as FIT a GPX import with no stored UTC offset
- **THEN** the local timestamp uses the device's current zone

## MODIFIED Requirements

### Requirement: FIT records carry the activity's channels
Each point of a FIT export SHALL be a record with its time and, where stored, position, altitude, distance, speed, heart rate, cadence, power, temperature, depth, respiration rate, running dynamics, left/right balance, left and right torque effectiveness and pedal smoothness, left and right platform centre offset, core temperature, SmO₂, total hemoglobin, e-bike battery level, assist mode and assist level, ppO₂, CNS, N₂ load, no-deco limit, deco stop depth and time, and time to surface, in FIT's own units and scales. Form power and leg spring stiffness, which FIT has no native field for, SHALL NOT be written.

#### Scenario: Ride with power
- **WHEN** an athlete exports a ride with a track and heart rate, cadence and power streams as FIT
- **THEN** each record carries its position, heart rate, cadence and power

#### Scenario: Indoor ride
- **WHEN** an athlete exports an indoor ride with no track as FIT
- **THEN** its records carry the streams' channels and no position

#### Scenario: Deco dive
- **WHEN** an athlete exports as FIT a dive with ppO₂, no-deco limit and deco stop streams
- **THEN** each record carries its depth, ppO₂, no-deco limit and next stop depth and time

#### Scenario: Ride with cycling dynamics
- **WHEN** an athlete exports as FIT a ride with torque effectiveness, pedal smoothness and platform centre offset streams
- **THEN** each record carries its left and right torque effectiveness, pedal smoothness and platform centre offset

#### Scenario: Run with Stryd fields
- **WHEN** an athlete exports as FIT a run whose streams hold power from a Stryd developer field, form power and leg spring stiffness
- **THEN** each record carries the power, and no record carries form power or leg spring stiffness

### Requirement: FIT session carries the summary and sport
The FIT session and lap SHALL hold the activity's sport and sub-sport, elapsed time, moving time (its elapsed time when it has none), distance, elevation gain and loss, average and maximum speed, heart rate summary, cadence summary, power summary (average, maximum, normalized, work), calories, shift counts, average temperature, cycle count (strides, half the stored steps, on a run, walk or hike), average stroke distance and, for the owner's file, training load, where stored. An activity type FIT has no sport for SHALL be written with FIT's nearest sport, or generic when there is none.

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

#### Scenario: Run with steps
- **WHEN** an athlete exports as FIT a run with 8,400 steps stored
- **THEN** its session's total strides is 4,200, and importing the file back stores 8,400 steps

### Requirement: UDDF writes the dive's local start time
A UDDF export's dive date and time SHALL be the dive's start in its stored UTC offset, or as the device's current time zone shows it when it has none, with no zone, as the file name's date is.

#### Scenario: Dive exported in its own zone
- **WHEN** an athlete in UTC−6 exports as UDDF a dive with no stored UTC offset that started at 15:10 UTC
- **THEN** the file's dive starts at 09:10 with no zone

#### Scenario: Dive with a stored offset exported elsewhere
- **WHEN** an athlete in UTC−6 exports as UDDF a dive with a stored UTC offset of −5:00 that started at 15:10 UTC
- **THEN** the file's dive starts at 10:10 with no zone
