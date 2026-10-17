# feed/activity-export Specification

## Purpose

Lets an athlete take any of their own activities out of the app as a GPX, TCX or FIT file (or, for a dive, UDDF), rebuilt from the record, streams and full track the app stores, including activities recorded in the app that have no original file, or, for an imported activity, as the very file it was imported from.

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
Export SHALL offer GPX when the activity has a stored track of at least two points, and TCX and FIT when it has a stored track or streams. A dive SHALL also offer UDDF whenever it offers TCX; no other activity SHALL offer UDDF. An activity with a stored or queued original file SHALL also offer it, first. An activity with neither a track, streams nor an original file SHALL NOT offer Export.

#### Scenario: Outdoor GPS activity
- **WHEN** an athlete exports an activity with a track and streams
- **THEN** GPX, TCX and FIT are offered

#### Scenario: Indoor activity with streams
- **WHEN** an athlete exports an indoor ride with heart rate and power streams and no track
- **THEN** TCX and FIT are offered, and GPX is not

#### Scenario: Manual entry
- **WHEN** an athlete opens their own manual entry with no track and no streams
- **THEN** no Export is offered

#### Scenario: Dive with a depth profile
- **WHEN** an athlete exports a dive with depth streams and an entry and exit track
- **THEN** GPX, TCX, FIT and UDDF are offered

#### Scenario: Dive with streams and no track
- **WHEN** an athlete exports a dive with depth streams and no stored track
- **THEN** TCX, FIT and UDDF are offered, and GPX is not

#### Scenario: Run
- **WHEN** an athlete exports a run with a track and streams
- **THEN** UDDF is not offered

#### Scenario: Imported activity
- **WHEN** an athlete exports a ride imported from a FIT file
- **THEN** the original file is offered first, then GPX, TCX and FIT

#### Scenario: Imported file with only a summary
- **WHEN** an athlete opens their own activity imported from a file that gave neither a track nor streams
- **THEN** Export is offered, with only the original file

#### Scenario: Recorded in the app
- **WHEN** an athlete exports a run they recorded in the app
- **THEN** no original file is offered

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

### Requirement: FIT carries gear changes
A FIT export of an activity with stored gear changes SHALL hold each as a front or rear gear change event at its time, with the front and rear gear numbers after the change.

#### Scenario: Ride with electronic shifting
- **WHEN** an athlete exports as FIT a ride whose streams hold gear changes
- **THEN** the file has a gear change event at each change's second with its front and rear gears

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
Before exporting, the app SHALL say that the file is rebuilt from what the app stores and lacks sub-second timing and fields the app doesn't keep, and also laps when the activity has none stored. The note SHALL be about the rebuilt formats only and SHALL NOT apply to the original file.

#### Scenario: Choose a format
- **WHEN** an athlete opens Export on an activity with no stored laps
- **THEN** the formats are listed with a note that the file is rebuilt and lacks laps and other fields the app doesn't keep

#### Scenario: Activity with laps
- **WHEN** an athlete opens Export on an activity with stored laps
- **THEN** the note says the file is rebuilt and lacks fields the app doesn't keep, and doesn't say it lacks laps

#### Scenario: Activity with an original
- **WHEN** an athlete opens Export on an imported activity
- **THEN** the original file is listed apart from the rebuilt formats, as the file they imported, and the rebuilt note sits with the rebuilt formats

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

### Requirement: UDDF is a one-dive logbook
A UDDF export SHALL be a UDDF 3.2 file that dive logbook software imports as one dive: one repetition group holding the dive, with its date and time, dive number and surface interval before it, and after it its greatest and average depth, duration, lowest water temperature, air temperature, the site's altitude, visibility, highest ppO₂, no-fly and desaturation times, where the record has them. Values SHALL be in UDDF's SI units (meters, seconds, Kelvin, Pascal, gas fractions).

#### Scenario: Dive with a summary
- **WHEN** an athlete exports as UDDF a dive stored with a 31.2 m maximum depth, a 48-minute duration, dive number 212 and a 90-minute surface interval
- **THEN** the file holds one dive with greatest depth 31.2, duration 2880, dive number 212 and a 5400-second surface interval

#### Scenario: First dive of the day
- **WHEN** an athlete exports as UDDF a dive with no stored surface interval
- **THEN** the file's dive has no surface interval

### Requirement: UDDF writes the dive's local start time
A UDDF export's dive date and time SHALL be the dive's start as the device's current time zone shows it, with no zone, as the file name's date is.

#### Scenario: Dive exported in its own zone
- **WHEN** an athlete in UTC−6 exports as UDDF a dive that started at 15:10 UTC
- **THEN** the file's dive starts at 09:10 with no zone

### Requirement: UDDF carries the depth profile
A UDDF export SHALL hold one waypoint per second of the dive that has a stored depth, in time order, each with its time since the start and depth, and, where stored at that second, water temperature, heart rate, ppO₂, CNS, no-deco time, the next deco stop's depth and time, and each tank's pressure. Points with no depth SHALL NOT become waypoints.

#### Scenario: Deco dive
- **WHEN** an athlete exports as UDDF a dive with depth, temperature, ppO₂, no-deco and deco stop streams
- **THEN** each waypoint has its time, depth, temperature in Kelvin and ppO₂, and the waypoints in deco carry the stop's depth and time

#### Scenario: Entry and exit track
- **WHEN** an athlete exports as UDDF a dive whose stored track is only its entry and exit positions
- **THEN** the waypoints are the depth samples, and the positions add none

### Requirement: UDDF carries gases and gas switches
A UDDF export SHALL define a mix for each gas the record lists and each gas a stored switch names, with its oxygen and helium fractions, and SHALL switch mix at each stored switch's time. A dive with gases but no stored switches SHALL switch to its first gas at the start and switch to no other. A closed-circuit dive SHALL say so.

#### Scenario: Switch to deco gas
- **WHEN** an athlete exports as UDDF a dive with stored switches to 21/0 at 0 and to 50/0 at 1920 seconds
- **THEN** the file defines mixes 0.21/0 and 0.50/0, the first waypoint switches to 0.21/0, and the waypoint at 1920 seconds switches to 0.50/0

#### Scenario: Older dive without switches
- **WHEN** an athlete exports as UDDF a dive breathed on 21/0 and 50/0 with no stored switches
- **THEN** the file defines both mixes and switches only to 21/0, at the start

#### Scenario: Merged dive
- **WHEN** an athlete exports as UDDF a dive whose record lists only 21/0 and whose stored switches name 21/0 and 32/0
- **THEN** the file defines both mixes and switches to each at its time

#### Scenario: Rebreather dive
- **WHEN** an athlete exports as UDDF a dive stored as closed circuit
- **THEN** the file's dive is in closed-circuit mode

### Requirement: UDDF carries tanks
A UDDF export SHALL hold each of the record's tanks with its start and end pressure, volume and gas where stored, and each waypoint's tank pressures SHALL name the tank they were read from. Each gas no stored tank holds SHALL also get a tank naming only that gas, since logbook software attaches gases and switches to tanks; importing the file back SHALL NOT turn those into tanks.

#### Scenario: Two tanks
- **WHEN** an athlete exports as UDDF a dive with two tanks and a pressure series for each
- **THEN** the file holds two tanks with their pressures and gases, and each waypoint's readings name their tank

#### Scenario: Gases without stored tanks
- **WHEN** an athlete exports as UDDF a dive breathed on 17/30 and 52/0 with no stored tanks
- **THEN** the file holds a tank naming each gas and nothing else, and Subsurface imports the dive with both gases

### Requirement: UDDF carries the site and dive computer
A UDDF export SHALL hold the dive site with the record's site name and, when the dive has a stored track, the track's first position as its coordinates. It SHALL name the record's device as the diver's dive computer. The site's coordinates SHALL appear only in the exported file.

#### Scenario: Dive at a named site with an entry point
- **WHEN** an athlete exports as UDDF a dive at "Blue Hole" with a stored entry and exit
- **THEN** the file's site is named "Blue Hole" at the entry's coordinates

#### Scenario: Dive with no site or track
- **WHEN** an athlete exports as UDDF a dive with no site name and no stored track
- **THEN** the file has no dive site

#### Scenario: Dive computer
- **WHEN** an athlete exports as UDDF a dive recorded by a "Shearwater Perdix 2"
- **THEN** the file names "Shearwater Perdix 2" as its dive computer

### Requirement: An exported UDDF imports back as the same dive
Importing a UDDF export SHALL give back the dive's start (read in the zone it was written in), duration, summary fields above, site name, gases, gas switches, tanks and depth, temperature, heart rate, ppO₂, CNS, no-deco, deco stop and tank pressure streams, as the app stored them.

#### Scenario: Round trip of a deco dive
- **WHEN** an athlete exports as UDDF a dive with switches from 21/0 to 50/0, two tanks and deco stop streams, and imports the file
- **THEN** the imported dive has the same start, depth profile, summary, gases, switches at their times, tanks and deco streams

#### Scenario: Round trip of an older dive
- **WHEN** an athlete exports as UDDF a dive with no stored switches, deco state or tank pressure series, and imports the file
- **THEN** the imported dive has the same start, depth profile, summary and first gas
