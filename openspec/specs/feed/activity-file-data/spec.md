# feed/activity-file-data Specification

## Purpose


Keeps the summary fields, training load, extra per-sample channels and laps that an imported FIT activity records, each at a visibility suited to what it reveals, and shows them on the activity's page.

## Requirements


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
An activity imported from FIT or TCX SHALL store each lap with its start, elapsed and moving time, distance, average and maximum speed and heart rate, average power and cadence, ascent and descent, calories, intensity (active, rest, warm-up, cool-down, recovery or another the file names) and trigger (manual, distance, time, position or another the file names), where recorded. Laps SHALL be stored with the streams, at their visibility. A file with a single lap spanning the activity SHALL store none.

#### Scenario: Interval run
- **WHEN** an athlete imports a FIT run with eight laps
- **THEN** its streams hold eight laps in order, each with its own time and distance

#### Scenario: One automatic lap
- **WHEN** an athlete imports a FIT ride with one lap spanning the ride
- **THEN** its streams hold no laps

#### Scenario: TCX intervals
- **WHEN** an athlete imports a TCX run with eight laps alternating Active and Resting intensity
- **THEN** its streams hold eight laps in order, each with its own time, distance, calories and intensity

#### Scenario: Lap triggers
- **WHEN** an athlete imports a FIT run with automatic laps every kilometer and one manual lap
- **THEN** its stored laps' triggers are distance, except the manual one's

#### Scenario: TCX with one lap
- **WHEN** an athlete imports a TCX ride with one lap spanning the ride
- **THEN** its streams hold no laps, and the lap's totals are the activity's

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

### Requirement: Lap table shows intensity
An activity's lap table SHALL mark each lap's intensity when the laps record one, so rests and warm-ups stand apart from work intervals.

#### Scenario: Intervals with rests
- **WHEN** a viewer opens a run whose stored laps alternate active and rest
- **THEN** the lap table marks each rest lap as a rest

#### Scenario: Laps without intensity
- **WHEN** a viewer opens a ride whose stored laps record no intensity
- **THEN** the lap table shows no intensity marks

### Requirement: Ski runs and lifts are kept
A ski or snowboard activity imported from FIT SHALL store each run and each lift ride it records as a lap, in order, with a lift as a rest lap, its time and its ascent or descent. These SHALL replace the file's own laps when the file records runs.

#### Scenario: Resort day
- **WHEN** an athlete imports a FIT resort ski day of eight runs and eight lift rides
- **THEN** its streams hold sixteen laps in order, the eight lift rides as rest laps

#### Scenario: Backcountry ski without runs
- **WHEN** an athlete imports a FIT backcountry ski with laps and no run splits
- **THEN** its streams hold the file's laps

### Requirement: Ski activities show their runs
A ski or snowboard activity's page SHALL show its run count among its stats, to viewers the streams admit, and its lap table SHALL label laps as runs and lifts, with each run's descent.

#### Scenario: Resort day page
- **WHEN** a viewer who may see the streams opens a ski day with eight runs stored
- **THEN** its stats show 8 runs and the lap table lists each run with its descent and each lift

#### Scenario: Viewer who may not see streams
- **WHEN** a viewer opens a Public ski day whose streams are Only me
- **THEN** no run count or lap table is shown

### Requirement: Lap speed from distance and time
When a lap has a distance and a time but the file gives no average speed, the lap table SHALL show its speed as its distance over its moving time, or over its elapsed time when it has no moving time.

#### Scenario: TCX lap without the speed extension
- **WHEN** a TCX run whose laps have no `AvgSpeed` extension, one lap 1,000 m in 5:00, is shown
- **THEN** that lap's row shows 12.0 km/h (7.5 mph)

#### Scenario: Lap without distance
- **WHEN** a lap has no distance
- **THEN** its speed shows a dash
