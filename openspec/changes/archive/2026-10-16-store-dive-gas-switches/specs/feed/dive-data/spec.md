# Spec Delta

## Purpose

Keeps what FIT and UDDF dive files record beyond the depth profile and basic summary (decompression state and tank pressure over time, more of the dive's summary, its notes and where it was entered and exited) and shows it on the dive's page without exposing private locations.

## ADDED Requirements

### Requirement: Dive state over time is kept as stream channels
Importing a FIT or UDDF dive SHALL store, where the file records them per sample, ppO₂ (millibar), CNS (percent), N₂ load (percent), no-deco limit (seconds), deco stop depth (centimeters) and time (seconds), and time to surface (seconds), each as a stream channel aligned with the dive's samples.

#### Scenario: FIT dive with deco state
- **WHEN** an athlete imports a FIT dive whose records carry `po2`, `ndl_time`, `next_stop_depth`, `next_stop_time`, `time_to_surface`, `cns_load` and `n2_load`
- **THEN** its streams hold each of those channels, in the units above

#### Scenario: UDDF waypoints with ppO₂ and no-deco time
- **WHEN** an athlete imports a UDDF dive whose waypoints carry `calculatedpo2` and `nodecotime`
- **THEN** its streams hold ppO₂ and no-deco limit channels

#### Scenario: Recreational dive without deco data
- **WHEN** an athlete imports a dive whose file records depth and temperature only
- **THEN** its streams hold no deco channels

### Requirement: Measured ppO₂ is preferred
When a sample records both a measured and a calculated ppO₂, the stored ppO₂ SHALL be the measured one. With several measured cells, it SHALL be their average.

#### Scenario: Rebreather with three cells
- **WHEN** an athlete imports a UDDF dive whose waypoint has `measuredpo2` of 1.18, 1.20 and 1.22 bar and a `calculatedpo2` of 1.30
- **THEN** that sample's ppO₂ is 1200 millibar

### Requirement: Tank pressure over time is kept per tank
Importing a FIT or UDDF dive that records tank pressure during the dive SHALL store one pressure series per tank (millibar, aligned with the dive's samples), in the order of the dive's tanks. A sample with no reading for a tank SHALL be empty for it.

#### Scenario: FIT dive with two transmitters
- **WHEN** an athlete imports a FIT dive with `tank_update` messages from two sensors
- **THEN** its streams hold two pressure series, each at the nearest sample to its updates

#### Scenario: UDDF tank pressure
- **WHEN** an athlete imports a UDDF dive whose waypoints carry `tankpressure` referring to its one tank
- **THEN** its streams hold one pressure series

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

### Requirement: Merging fills missing dive data
Merging another recording into a dive SHALL add each dive state channel, tank pressure series and summary field the dive lacks from the other, and SHALL NOT overwrite any the dive has.

#### Scenario: Logbook adds tank pressure
- **WHEN** an athlete merges a UDDF dive with tank pressure into a FIT dive without it
- **THEN** the FIT dive gains the tank pressure series, and its own channels are unchanged
