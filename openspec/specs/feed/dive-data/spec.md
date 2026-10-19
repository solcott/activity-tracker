# feed/dive-data Specification

## Purpose

Keeps what FIT and UDDF dive files record beyond the depth profile and basic summary (decompression state and tank pressure over time, more of the dive's summary, its notes and where it was entered and exited) and shows it on the dive's page without exposing private locations.

## Requirements

### Requirement: Dive state over time is kept as stream channels
Importing a FIT, UDDF, Shearwater or Subsurface dive SHALL store, where the file records them per sample, ppO₂ (millibar), CNS (percent), N₂ load (percent), no-deco limit (seconds), deco stop depth (centimeters) and time (seconds), and time to surface (seconds), each as a stream channel aligned with the dive's samples.

#### Scenario: FIT dive with deco state
- **WHEN** an athlete imports a FIT dive whose records carry `po2`, `ndl_time`, `next_stop_depth`, `next_stop_time`, `time_to_surface`, `cns_load` and `n2_load`
- **THEN** its streams hold each of those channels, in the units above

#### Scenario: UDDF waypoints with ppO₂ and no-deco time
- **WHEN** an athlete imports a UDDF dive whose waypoints carry `calculatedpo2` and `nodecotime`
- **THEN** its streams hold ppO₂ and no-deco limit channels

#### Scenario: Shearwater records with deco state
- **WHEN** an athlete imports a Shearwater dive whose records carry `averagePPO2` of 1.2, `currentNdl` of 0, `firstStopDepth` of 6 meters and `ttsMins` of 9
- **THEN** each of those samples has a ppO₂ of 1200 millibar, a no-deco limit of 0, a deco stop depth of 600 centimeters and a time to surface of 540 seconds

#### Scenario: Shearwater record outside deco
- **WHEN** an athlete imports a Shearwater dive whose record has a `firstStopDepth` of 0 and a `currentNdl` of 25
- **THEN** that sample has no deco stop depth and a no-deco limit of 1500 seconds

#### Scenario: Subsurface samples that carry values only when they change
- **WHEN** an athlete imports a Subsurface dive whose sample at 10:00 has `ndl='12:00 min'` and whose samples after it, up to 12:00, carry no `ndl`
- **THEN** the samples from 10:00 up to 12:00 each have a no-deco limit of 720 seconds

#### Scenario: Subsurface deco stop
- **WHEN** an athlete imports a Subsurface dive whose samples carry `stopdepth='3.0 m'`, `stoptime='2:00 min'`, `tts='5:00 min'`, `cns='14%'` and `po2='1.40 bar'`
- **THEN** its streams hold deco stop depth, deco stop time, time to surface, CNS and ppO₂ channels in the units above

#### Scenario: Recreational dive without deco data
- **WHEN** an athlete imports a dive whose file records depth and temperature only
- **THEN** its streams hold no deco channels

### Requirement: Measured ppO₂ is preferred
When a sample records both a measured and a calculated ppO₂, the stored ppO₂ SHALL be the measured one. With several measured cells, it SHALL be their average.

#### Scenario: Rebreather with three cells
- **WHEN** an athlete imports a UDDF dive whose waypoint has `measuredpo2` of 1.18, 1.20 and 1.22 bar and a `calculatedpo2` of 1.30
- **THEN** that sample's ppO₂ is 1200 millibar

### Requirement: Tank pressure over time is kept per tank
Importing a FIT, UDDF, Shearwater or Subsurface dive that records tank pressure during the dive SHALL store one pressure series per tank (millibar, aligned with the dive's samples), in the order of the dive's tanks. A sample with no reading for a tank SHALL be empty for it.

#### Scenario: FIT dive with two transmitters
- **WHEN** an athlete imports a FIT dive with `tank_update` messages from two sensors
- **THEN** its streams hold two pressure series, each at the nearest sample to its updates

#### Scenario: UDDF tank pressure
- **WHEN** an athlete imports a UDDF dive whose waypoints carry `tankpressure` referring to its one tank
- **THEN** its streams hold one pressure series

#### Scenario: Shearwater transmitter in PSI
- **WHEN** an athlete imports a Shearwater dive whose records read `tank0pressurePSI` of 3000 falling to 1000, and `AI is off` for every other tank
- **THEN** the dive has one tank with start and end pressures of about 207 and 69 bar, and its streams hold one pressure series in millibar

#### Scenario: Subsurface cylinder pressure
- **WHEN** an athlete imports a Subsurface dive with two cylinders whose samples carry pressure readings for each
- **THEN** its streams hold two pressure series, in cylinder order

#### Scenario: Shearwater without air integration
- **WHEN** an athlete imports a Shearwater dive whose every tank pressure reads `AI is off` or `N/A`
- **THEN** its streams hold no tank pressure series and the dive has no tanks from them

### Requirement: UDDF heart rate fills the heart rate channel
A UDDF dive whose waypoints carry `heartrate` SHALL store it as the activity's heart rate stream.

#### Scenario: UDDF with heart rate
- **WHEN** an athlete imports a UDDF dive with a heart rate in each waypoint
- **THEN** the dive's heart rate chart shows those values

### Requirement: More of the dive's summary is kept
A dive's public summary SHALL hold, where the file records them: start and end N₂ load, average and maximum ascent and descent rates, descent time, water type and density, air temperature, visibility, altitude, no-fly time, desaturation time and highest ppO₂.

#### Scenario: FIT dive summary
- **WHEN** an athlete imports a FIT dive whose `dive_summary` has start and end N₂, ascent and descent rates and descent time, and whose `dive_settings` say salt water
- **THEN** the published dive carries each of them and its water type is salt

#### Scenario: UDDF dive summary
- **WHEN** an athlete imports a UDDF dive whose `informationbeforedive` has an air temperature and altitude and whose `informationafterdive` has visibility, no-fly time, desaturation time and highest ppO₂
- **THEN** the published dive carries each of them

### Requirement: Each tank keeps its volume and gas
A dive's tanks SHALL each hold, where the file records them, the tank's volume and the oxygen and helium of the gas it held.

#### Scenario: UDDF tank with a mix
- **WHEN** an athlete imports a UDDF dive whose `tankdata` has a `tankvolume` of 0.012 cubic meters and links to a 32% mix
- **THEN** that tank carries a 12-liter volume and the gas 32/0

### Requirement: Dive notes prefill the description
A UDDF dive's notes SHALL fill the import screen's description, which the athlete can keep, edit or clear before publishing. Buddy names SHALL NOT be published.

#### Scenario: Notes on import
- **WHEN** an athlete imports a UDDF dive whose `informationafterdive/notes` reads "Turtle at the wall"
- **THEN** the import screen's description reads "Turtle at the wall"

#### Scenario: Buddy in the logbook
- **WHEN** an athlete imports a UDDF dive that links a buddy
- **THEN** the published activity names no buddy

### Requirement: A dive without a track keeps its positions privately
A dive with no GPS track SHALL keep its entry and exit positions, or else its logbook site's position, as its track, stored like any other track in the athlete's `self` space. The positions SHALL NOT appear in the activity record, its polyline or its streams.

#### Scenario: Garmin dive with surface fixes
- **WHEN** an athlete imports a FIT dive with start and end positions and no GPS records
- **THEN** the dive's track holds those two positions, and the activity record has no polyline

#### Scenario: Logbook site position
- **WHEN** an athlete imports a UDDF dive whose site has a latitude and longitude
- **THEN** the dive's track holds that one position, and the record names only the site

#### Scenario: Dive with a track
- **WHEN** an athlete imports a dive that already has GPS records
- **THEN** its track is those records, unchanged

### Requirement: Dive page shows the new data
The dive's page SHALL show the deco stop as a ceiling under the depth line, ppO₂, CNS and tank pressure as their own charts, and the new summary fields among the dive's stats, each only where stored.

#### Scenario: Deco dive
- **WHEN** a viewer opens a dive with deco stop depth, ppO₂ and two tank pressure series stored
- **THEN** the depth chart shows the ceiling, and ppO₂ and tank pressure charts follow it, the latter with a line per tank

#### Scenario: Dive with none of it
- **WHEN** a viewer opens a dive with depth and temperature only
- **THEN** its page shows no ceiling and no ppO₂, CNS or tank pressure chart

### Requirement: Dive computer alerts are kept with their times
Importing a FIT or UDDF dive SHALL store each alert the dive computer raised in the activity's streams, with its time since the dive's start and its kind as an open-enum token. FIT `dive_alert` events and UDDF waypoint `alarm`s SHALL each become one alert. Dismissals of an alert SHALL NOT be stored.

#### Scenario: FIT no-deco limit reached
- **WHEN** an athlete imports a FIT dive with a `dive_alert` event of `ndl_reached` at 1500 seconds
- **THEN** its streams hold an alert of kind `ndlReached` at 1500 seconds

#### Scenario: UDDF ascent alarm
- **WHEN** an athlete imports a UDDF dive whose waypoint at 2400 seconds has an `alarm` of `ascent`
- **THEN** its streams hold an alert of kind `ascentCritical` at 2400 seconds

#### Scenario: Dismissal
- **WHEN** an athlete imports a FIT dive with a `dive_alert` of `alert_dismissed_by_key`
- **THEN** no alert is stored for it

#### Scenario: Dive without alerts
- **WHEN** an athlete imports a dive whose file records no alerts
- **THEN** its streams hold no alerts

### Requirement: Unknown alert kinds are kept
An alert whose kind the app doesn't name SHALL still be stored, with its kind as the file names it, so a newer dive computer's alerts aren't lost. An alert without a time SHALL NOT be stored.

#### Scenario: Newer FIT alert
- **WHEN** an athlete imports a FIT dive with a `dive_alert` event whose value the app's alert kinds don't include
- **THEN** its streams hold an alert at that time whose kind is the file's own name or number for it

### Requirement: Dive computer settings are kept
A dive's public summary SHALL hold, where the file records them: the deco model, the ppO₂ warning, critical and deco limits (millibar), the closed-circuit low and high setpoints (millibar) and the safety stop time (seconds, only when the safety stop was on). They SHALL come from FIT `dive_settings` and, for the deco model, Shearwater's `decoModel`.

#### Scenario: FIT dive settings
- **WHEN** an athlete imports a FIT dive whose `dive_settings` has model `zhl_16c`, `po2_warn` 1.4, `po2_critical` 1.6, `po2_deco` 1.6, safety stop enabled with 180 seconds, and setpoints of 0.7 and 1.3
- **THEN** the published dive carries deco model `zhl16c`, ppO₂ limits of 1400, 1600 and 1600 millibar, setpoints of 700 and 1300 millibar and a safety stop of 180 seconds

#### Scenario: Safety stop off
- **WHEN** an athlete imports a FIT dive whose `dive_settings` has the safety stop disabled
- **THEN** the published dive carries no safety stop time

#### Scenario: Shearwater VPM-B
- **WHEN** an athlete imports a Shearwater dive whose header has a `decoModel` of 1
- **THEN** the published dive carries deco model `vpmB`

### Requirement: Depth chart marks warnings
The dive's depth chart SHALL mark each stored alert that is a warning at its time with the alert's name. Informational alerts (such as a safety stop started or completed, a setpoint switch or deco completed) and alerts of a kind the app doesn't name SHALL NOT be marked. A dive with no stored alerts SHALL show no alert markers.

#### Scenario: No-deco limit reached
- **WHEN** a viewer opens a dive with an `ndlReached` alert at 25 minutes
- **THEN** the depth chart has a "No-deco limit reached" marker at 25 minutes, distinct from gas switch markers

#### Scenario: Informational alert
- **WHEN** a viewer opens a dive whose only alert is `safetyStopComplete`
- **THEN** the depth chart shows no alert markers

### Requirement: Dive page shows the dive computer settings
The dive's stats SHALL show its deco model, ppO₂ limits, setpoints and safety stop time where stored, alongside its gradient factors and water type. A dive with none of them SHALL show none.

#### Scenario: Rebreather dive
- **WHEN** a viewer opens a dive with deco model `zhl16c`, gradient factors 40/85 and setpoints of 700 and 1300 millibar
- **THEN** its stats show the deco model with its gradient factors and both setpoints in bar

#### Scenario: Older dive
- **WHEN** a viewer opens a dive imported before settings were kept
- **THEN** its stats show its gradient factors and water type as before, and no other settings
