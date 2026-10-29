# Spec Delta

## MODIFIED Requirements

### Requirement: All stats list
The page SHALL list every summary figure the viewer was served, including the hero's, as label and value rows in named groups: Timing, Distance & speed, Heart rate, Power, Cadence, Elevation, Work & energy, Temperature, Counts, Dive and Device. The start is the exception: it shows in the page's header instead (feed/activity-start-time). A group with no figures SHALL be left out.

#### Scenario: Dive computer summary
- **WHEN** a dive with a bottom time, a gas, a surface interval and end CNS is opened
- **THEN** the stats list's Dive group shows each of them

#### Scenario: Figure not in the hero
- **WHEN** a ride with a descent and front and rear shift counts is opened
- **THEN** the stats list shows the descent under Elevation and the shifts under Counts

#### Scenario: Device
- **WHEN** an activity recorded on a Garmin Edge with a heart rate strap is opened
- **THEN** the stats list's Device group names the Edge and the heart rate sensor

#### Scenario: Timing without the start
- **WHEN** a ride with moving and elapsed times is opened
- **THEN** the Timing group shows moving time and elapsed time, and no start time
