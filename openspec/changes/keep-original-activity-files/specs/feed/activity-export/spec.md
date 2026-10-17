# Spec Delta

## MODIFIED Requirements

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
