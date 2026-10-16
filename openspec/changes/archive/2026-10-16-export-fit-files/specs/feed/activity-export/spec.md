# Spec Delta

## MODIFIED Requirements

### Requirement: Formats offered follow the data stored
Export SHALL offer GPX when the activity has a stored track of at least two points, and TCX and FIT when it has a stored track or streams. An activity with neither SHALL NOT offer Export.

#### Scenario: Outdoor GPS activity
- **WHEN** an athlete exports an activity with a track and streams
- **THEN** GPX, TCX and FIT are offered

#### Scenario: Indoor activity with streams
- **WHEN** an athlete exports an indoor ride with heart rate and power streams and no track
- **THEN** TCX and FIT are offered, and GPX is not

#### Scenario: Manual entry
- **WHEN** an athlete opens their own manual entry with no track and no streams
- **THEN** no Export is offered

### Requirement: The file holds the full track joined with streams
An exported file SHALL hold every point of the activity's stored track, untrimmed by the athlete's privacy zones, with each stream sample attached to the track point at the same second. Stream samples with no track point at that second SHALL become points of their own. A FIT file, whose times are whole seconds, SHALL hold one point per second: the first of the points in that second.

#### Scenario: Track and heart rate
- **WHEN** an athlete exports a run whose track and heart rate stream cover the same seconds
- **THEN** each point in the file has its position, time and heart rate

#### Scenario: Route through a privacy zone
- **WHEN** an athlete exports an activity that starts inside one of their privacy zones
- **THEN** the file's first points are inside the zone, as recorded

#### Scenario: Streams without a track
- **WHEN** an athlete exports an indoor ride as TCX
- **THEN** each stream sample is a point with its time and channels and no position

#### Scenario: Two track points in one second as FIT
- **WHEN** an athlete exports as FIT a track with two points in the same second
- **THEN** the file has one point for that second, at the first point's position

## ADDED Requirements

### Requirement: FIT is a completed activity file
A FIT export SHALL be a valid FIT activity file that Garmin Connect and Strava import as a completed activity: a file ID of type activity, a start and a stop timer event, one lap and one session spanning the activity, and an activity message, all at the activity's start and end times.

#### Scenario: Run as FIT
- **WHEN** an athlete exports a run as FIT
- **THEN** the file passes Garmin's FIT SDK integrity check and holds one session and one lap from the run's start to its end

### Requirement: FIT records carry the activity's channels
Each point of a FIT export SHALL be a record with its time and, where stored, position, altitude, distance, speed, heart rate, cadence, power, temperature and depth, in FIT's own units and scales.

#### Scenario: Ride with power
- **WHEN** an athlete exports a ride with a track and heart rate, cadence and power streams as FIT
- **THEN** each record carries its position, heart rate, cadence and power

#### Scenario: Indoor ride
- **WHEN** an athlete exports an indoor ride with no track as FIT
- **THEN** its records carry the streams' channels and no position

### Requirement: FIT session carries the summary and sport
The FIT session and lap SHALL hold the activity's sport and sub-sport, elapsed time, moving time (its elapsed time when it has none), distance, elevation gain, average and maximum speed, heart rate summary and average temperature, where the record has them. An activity type FIT has no sport for SHALL be written with FIT's nearest sport, or generic when there is none.

#### Scenario: Trail run
- **WHEN** an athlete exports a trail run as FIT
- **THEN** its session's sport is running and its sub-sport trail

#### Scenario: Moving time
- **WHEN** an athlete exports as FIT an activity with 50 minutes elapsed and 45 moving
- **THEN** its session's elapsed time is 50 minutes and its timer time 45

#### Scenario: Type FIT doesn't know
- **WHEN** an athlete exports as FIT an activity whose type has no FIT sport
- **THEN** its session's sport is generic

### Requirement: FIT carries gear changes
A FIT export of an activity with stored gear changes SHALL hold each as a front or rear gear change event at its time, with the front and rear gear numbers after the change.

#### Scenario: Ride with electronic shifting
- **WHEN** an athlete exports as FIT a ride whose streams hold gear changes
- **THEN** the file has a gear change event at each change's second with its front and rear gears

### Requirement: FIT carries a dive's summary
A FIT export of a dive SHALL hold the dive's summary (maximum and average depth, bottom time, surface interval, dive number, start and end CNS, OTU), each gas breathed with its oxygen and helium, the gradient factors and each tank's pressures and gas used, where the record has them. Other activities SHALL hold none of these.

#### Scenario: Dive with two gases
- **WHEN** an athlete exports as FIT a dive breathed on air and on 50% nitrox
- **THEN** the file holds its dive summary and two gases, 21/0 and 50/0

#### Scenario: Run
- **WHEN** an athlete exports a run as FIT
- **THEN** the file holds no dive summary or gases

### Requirement: An exported FIT imports back as the same activity
Importing a FIT export SHALL give back the activity's type, start, elapsed and moving time, distance, heart rate summary, streams (to the second), gear changes and dive data, as the app stored them. A dive's gases after the first SHALL be in the file but MAY not come back, since the app doesn't store when each was switched to and the file doesn't invent switch times.

#### Scenario: Round trip of a ride
- **WHEN** an athlete exports a ride as FIT and imports the file
- **THEN** the imported activity has the ride's type, summary, streams and gear changes

#### Scenario: Round trip of a dive
- **WHEN** an athlete exports a dive as FIT and imports the file
- **THEN** the imported dive has the same depth profile, summary and first gas
