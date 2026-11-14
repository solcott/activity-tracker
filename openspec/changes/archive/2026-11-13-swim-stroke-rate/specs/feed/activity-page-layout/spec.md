## MODIFIED Requirements

### Requirement: A foot sport's cadence reads in steps per minute
On a run, walk or hike, everywhere the page shows cadence (the stats list, the cadence card and its chart, the average/max table and
the laps table) SHALL show it as steps per minute with the unit "spm": twice the cadence the activity stores, which files record per
foot. On a swim, it SHALL show the stored cadence as strokes per minute with the unit "strokes/min". Every other sport SHALL show the
stored cadence in rpm. What is stored SHALL NOT change.

#### Scenario: A run's cadence
- **WHEN** a viewer opens a run whose record stores an average cadence of 85 and a maximum of 92
- **THEN** its stats list shows average cadence 170 spm and maximum cadence 184 spm, and its cadence card charts the stream doubled, in spm

#### Scenario: A swim's stroke rate
- **WHEN** a viewer opens a swim whose record stores an average cadence of 30
- **THEN** its stats list shows average cadence 30 strokes/min, and its cadence card and laps table give strokes/min

#### Scenario: A ride's cadence
- **WHEN** a viewer opens a ride whose record stores an average cadence of 85
- **THEN** its stats list shows average cadence 85 rpm

#### Scenario: Exported as stored
- **WHEN** the owner exports that run as FIT
- **THEN** the file's cadence is 85, as stored
