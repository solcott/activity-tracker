# Spec Delta

## Purpose

Keeps the summary fields, training load, extra per-sample channels and laps that an imported FIT activity records, each at a visibility suited to what it reveals, and shows them on the activity's page.

## ADDED Requirements

### Requirement: Imported summary fields are published
An activity imported from FIT SHALL publish, where the file records them: elevation loss, average and maximum cadence, average, maximum and normalized power, work, calories, front and rear shift counts, and jump count, grit and flow. These SHALL be on the activity record, at the activity's visibility.

#### Scenario: Ride with a power meter and electronic shifting
- **WHEN** an athlete imports a FIT ride with average, maximum and normalized power, work, calories and shift counts
- **THEN** the published activity carries each of them

#### Scenario: Run without power
- **WHEN** an athlete imports a FIT run with cadence and no power
- **THEN** the published activity carries its cadence and no power fields

### Requirement: Training load is stored with the zones
An imported activity's FTP, intensity factor, training stress score and aerobic and anaerobic training effect SHALL NOT be on the activity record. They SHALL be stored with the activity's zones, at the narrower of its charts visibility and the athlete's training visibility.

#### Scenario: Public ride, Only-me training
- **WHEN** an athlete whose training is Only me imports a Public FIT ride with an FTP, IF and TSS
- **THEN** the activity record has none of them, and they are stored with its zones in the athlete's `self` space

### Requirement: Training load shown only to viewers its place admits
The activity's page SHALL show its training load only to viewers who may see its zones.

#### Scenario: Follower without training access
- **WHEN** a follower opens a Public ride whose training load is stored in the athlete's `self` space
- **THEN** the page shows the ride's power and no FTP, IF, TSS or training effect

#### Scenario: Owner
- **WHEN** the athlete opens their own ride
- **THEN** the page shows its FTP, IF, TSS and training effect

### Requirement: Extra channels are kept as streams
An activity imported from FIT SHALL store, where the file records them per sample, respiration rate, vertical oscillation, stance time, step length, vertical ratio, stance time balance and left/right power balance, each as a stream channel at the streams' visibility.

#### Scenario: Run with running dynamics
- **WHEN** an athlete imports a FIT run recorded with a running dynamics pod
- **THEN** its streams hold vertical oscillation, stance time, step length, vertical ratio and stance time balance

#### Scenario: Ride with dual-sided power
- **WHEN** an athlete imports a FIT ride whose records carry `left_right_balance` with the right-side flag
- **THEN** its streams hold the percent of power from the right leg

### Requirement: Laps are kept
An activity imported from FIT SHALL store each lap with its start, elapsed and moving time, distance, average and maximum speed and heart rate, average power and cadence, and ascent and descent, where recorded. Laps SHALL be stored with the streams, at their visibility. A file with a single lap spanning the activity SHALL store none.

#### Scenario: Interval run
- **WHEN** an athlete imports a FIT run with eight laps
- **THEN** its streams hold eight laps in order, each with its own time and distance

#### Scenario: One automatic lap
- **WHEN** an athlete imports a FIT ride with one lap spanning the ride
- **THEN** its streams hold no laps

### Requirement: Activity page shows the new data
The activity's page SHALL show the new summary fields among its stats, a chart for each new channel stored, and a lap table when laps are stored, each only where stored and admitted.

#### Scenario: Ride page
- **WHEN** a viewer opens a ride with power, cadence, calories, left/right balance and laps stored
- **THEN** its stats show the power, cadence and calories, a balance chart is shown, and a lap table lists each lap

#### Scenario: Recorded in the app
- **WHEN** a viewer opens a run recorded in the app
- **THEN** its page shows no lap table and no running dynamics charts

### Requirement: Merging fills missing activity data
Merging another recording into an activity SHALL add each new summary field, channel and the laps that the activity lacks, and SHALL NOT overwrite any it has.

#### Scenario: Bike computer adds power
- **WHEN** an athlete merges a bike computer's FIT ride with power into their watch's recording without power
- **THEN** the watch's recording gains the power summary and power-based channels, and keeps its own heart rate

### Requirement: Older activities are unchanged
Activities imported before this data was kept SHALL keep showing as before, with no new stats, charts or laps, and SHALL NOT be backfilled.

#### Scenario: Ride imported earlier
- **WHEN** a viewer opens a ride imported before power was published
- **THEN** its page shows the stats and charts it showed before
