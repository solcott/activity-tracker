# feed/activity-export Specification

## Purpose

Lets an athlete take any of their own activities out of the app as a GPX or TCX file, rebuilt from the record, streams and full track the app stores, including activities recorded in the app that have no original file.

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
Export SHALL offer GPX when the activity has a stored track of at least two points, and TCX when it has a stored track or streams. An activity with neither SHALL NOT offer Export.

#### Scenario: Outdoor GPS activity
- **WHEN** an athlete exports an activity with a track and streams
- **THEN** both GPX and TCX are offered

#### Scenario: Indoor activity with streams
- **WHEN** an athlete exports an indoor ride with heart rate and power streams and no track
- **THEN** only TCX is offered

#### Scenario: Manual entry
- **WHEN** an athlete opens their own manual entry with no track and no streams
- **THEN** no Export is offered

### Requirement: Export waits until the activity is sent
An activity whose record, streams or track are still queued to be sent SHALL NOT be exported. The app SHALL say it can be exported once it's sent, rather than export a file missing data.

#### Scenario: Activity still in the outbox
- **WHEN** an athlete tries to export an activity they just recorded offline
- **THEN** the app says the activity can be exported once it's sent, and no file is produced

### Requirement: The file holds the full track joined with streams
An exported file SHALL hold every point of the activity's stored track, untrimmed by the athlete's privacy zones, with each stream sample attached to the track point at the same second. Stream samples with no track point at that second SHALL become points of their own.

#### Scenario: Track and heart rate
- **WHEN** an athlete exports a run whose track and heart rate stream cover the same seconds
- **THEN** each point in the file has its position, time and heart rate

#### Scenario: Route through a privacy zone
- **WHEN** an athlete exports an activity that starts inside one of their privacy zones
- **THEN** the file's first points are inside the zone, as recorded

#### Scenario: Streams without a track
- **WHEN** an athlete exports an indoor ride as TCX
- **THEN** each stream sample is a point with its time and channels and no position

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
