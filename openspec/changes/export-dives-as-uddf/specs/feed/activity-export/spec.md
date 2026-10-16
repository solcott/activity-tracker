# Spec Delta

## MODIFIED Requirements

### Requirement: Formats offered follow the data stored
Export SHALL offer GPX when the activity has a stored track of at least two points, and TCX and FIT when it has a stored track or streams. A dive SHALL also offer UDDF whenever it offers TCX; no other activity SHALL offer UDDF. An activity with neither a track nor streams SHALL NOT offer Export.

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

## ADDED Requirements

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
A UDDF export SHALL hold each of the record's tanks with its start and end pressure, volume and gas where stored, and each waypoint's tank pressures SHALL name the tank they were read from.

#### Scenario: Two tanks
- **WHEN** an athlete exports as UDDF a dive with two tanks and a pressure series for each
- **THEN** the file holds two tanks with their pressures and gases, and each waypoint's readings name their tank

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
