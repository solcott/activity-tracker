# feed/activity-export Specification

## Purpose

Lets an athlete take any of their own activities out of the app as a GPX, TCX or FIT file, rebuilt from the record, streams and full track the app stores, including activities recorded in the app that have no original file.

## Requirements

### Requirement: Only the owner can export an activity
The Export action SHALL be offered only on the signed-in athlete's own activities, whatever their visibility. It SHALL NOT be offered on anyone else's activity, and exporting SHALL need nothing from the AppView beyond what the athlete can already read.

#### Scenario: Own activity
- **WHEN** an athlete opens one of their own GPS activities
- **THEN** its detail screen offers Export

#### Scenario: Someone else's activity
- **WHEN** an athlete opens a Public activity of an athlete they follow
- **THEN** its detail screen offers no Export

#### Scenario: Own Only me activity
- **WHEN** an athlete opens one of their own Only me activities with GPS
- **THEN** its detail screen offers Export

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

### Requirement: Export waits until the activity is sent
An activity whose record, streams or track are still queued to be sent SHALL NOT be exported. The app SHALL say it can be exported once it's sent, rather than export a file missing data.

#### Scenario: Activity still in the outbox
- **WHEN** an athlete tries to export an activity they just recorded offline
- **THEN** the app says the activity can be exported once it's sent, and no file is produced

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

### Requirement: GPX carries the activity's channels
A GPX export SHALL be GPX 1.1 with one track named after the activity's title (or its sport) and typed with its sport. Each point SHALL carry elevation and time, and heart rate, cadence, temperature, depth, speed and power where streams have them, as extensions Strava and Garmin read.

#### Scenario: Ride with power
- **WHEN** an athlete exports a ride with heart rate, cadence and power as GPX
- **THEN** each point carries its heart rate, cadence and power in extensions

#### Scenario: Dive
- **WHEN** an athlete exports a dive with positions, depth and water temperature as GPX
- **THEN** each point carries its depth and temperature in extensions

### Requirement: TCX carries the activity's channels and summary
A TCX export SHALL be one activity with one lap spanning it, holding the record's moving time (its elapsed time when it has none, since readers take a lap's time as timer time), distance and heart rate summary. Each point SHALL carry time and, where stored, position, altitude, distance, heart rate, cadence, speed and power. Its sport SHALL be Running, Biking or Other.

#### Scenario: Run as TCX
- **WHEN** an athlete exports a run as TCX
- **THEN** the file's activity is Running, with one lap of the run's moving time and distance

#### Scenario: Swim as TCX
- **WHEN** an athlete exports a swim as TCX
- **THEN** the file's activity sport is Other

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

### Requirement: Rebuilt files say what they lack
Before exporting, the app SHALL say that the file is rebuilt from what the app stores and lacks laps, sub-second timing and fields the app doesn't keep.

#### Scenario: Choose a format
- **WHEN** an athlete opens Export on their activity
- **THEN** the formats are listed with a note that the file is rebuilt and lacks laps and other fields the app doesn't keep

### Requirement: The file is handed to the platform
An exported file SHALL open the share sheet on Android and iOS, a save dialog on desktop, and download in the browser on the web. Its name SHALL be the activity's title (or sport) and start date, with the format's extension.

#### Scenario: Export on a phone
- **WHEN** an athlete exports an activity as GPX on Android or iOS
- **THEN** the share sheet opens with a `.gpx` file named after the activity and its date

#### Scenario: Export on desktop
- **WHEN** an athlete exports an activity as TCX on desktop
- **THEN** a save dialog opens with a `.tcx` file name filled in, and the file is written where they choose

#### Scenario: Export on the web
- **WHEN** an athlete exports an activity on the web
- **THEN** the browser downloads the file

#### Scenario: Cancel
- **WHEN** an athlete dismisses the share sheet or save dialog
- **THEN** nothing is written and the detail screen is unchanged

### Requirement: A failed export says why
When the track or streams can't be read (offline, an unreadable blob), the export SHALL fail with an error on the detail screen and produce no partial file.

#### Scenario: Offline
- **WHEN** an athlete exports an activity without a connection
- **THEN** the detail screen shows that the export failed, and no file is handed off
