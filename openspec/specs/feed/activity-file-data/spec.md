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
An imported activity's FTP, intensity factor, training stress score, aerobic and anaerobic training effect, exercise load, beginning and ending stamina potential, minimum stamina and estimated sweat loss SHALL NOT be on the activity record. They SHALL be stored with the activity's zones, at the narrower of its charts visibility and the athlete's training visibility.

#### Scenario: Public ride, Only-me training
- **WHEN** an athlete whose training is Only me imports a Public FIT ride with an FTP, IF and TSS
- **THEN** the activity record has none of them, and they are stored with its zones in the athlete's `self` space

#### Scenario: Garmin physiology
- **WHEN** an athlete imports a FIT ride whose session records exercise load 186.8, stamina from 100% to 70% with a minimum of 48%, and sweat loss of 764 ml
- **THEN** its training load holds exercise load 187, stamina start 100, end 70 and minimum 48, and sweat loss 764 ml, and the activity record holds none of them

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
An activity imported from FIT or TCX SHALL store each lap with its start, elapsed and moving time, distance, average and maximum speed and heart rate, average power and cadence, ascent and descent, calories, intensity and trigger, where recorded. Laps SHALL be stored with the streams, at their visibility. A file with a single lap spanning the activity SHALL store none.

#### Scenario: Interval run
- **WHEN** an athlete imports a FIT run with eight laps
- **THEN** its streams hold eight laps in order, each with its own time and distance

#### Scenario: One automatic lap
- **WHEN** an athlete imports a FIT ride with one lap spanning the ride
- **THEN** its streams hold no laps

#### Scenario: TCX intervals
- **WHEN** an athlete imports a TCX run with eight laps alternating Active and Resting intensity
- **THEN** its streams hold eight laps in order, each with its own time, distance, calories and intensity

#### Scenario: TCX with one lap
- **WHEN** an athlete imports a TCX ride with one lap spanning the ride
- **THEN** its streams hold no laps, and the lap's totals are the activity's

### Requirement: Laps keep their intensity and trigger
A stored lap's intensity SHALL be active, rest, warm-up, cool-down, recovery or another the file names, and its trigger manual, distance, time, position or another the file names.

#### Scenario: Lap triggers
- **WHEN** an athlete imports a FIT run with automatic laps every kilometer and one manual lap
- **THEN** its stored laps' triggers are distance, except the manual one's

#### Scenario: Lap intensities
- **WHEN** an athlete imports a FIT run with a warm-up lap, active and recovery laps, and a cool-down lap
- **THEN** each stored lap's intensity is the one its file names

### Requirement: Activity page shows the new data
The activity's page SHALL show the new summary fields among its stats, a chart for each new channel stored, and a lap table when laps are stored, each only where stored and admitted. The channels this applies to include cycling dynamics, core temperature, SmO₂, total hemoglobin, e-bike battery and assist, form power and leg spring stiffness.

#### Scenario: Ride page
- **WHEN** a viewer opens a ride with power, cadence, calories, left/right balance and laps stored
- **THEN** its stats show the power, cadence and calories, a balance chart is shown, and a lap table lists each lap

#### Scenario: Recorded in the app
- **WHEN** a viewer opens a run recorded in the app
- **THEN** its page shows no lap table and no running dynamics charts

#### Scenario: Ride with cycling dynamics
- **WHEN** a viewer who may see the streams opens a ride with torque effectiveness and pedal smoothness stored
- **THEN** the page shows a torque effectiveness chart and a pedal smoothness chart, each with its left and right values

### Requirement: Older activities are unchanged
Activities imported before this data was kept SHALL keep showing as before, with no new stats, charts or laps, and SHALL NOT be backfilled. This includes activities imported before heart rate from `hr` messages, HRV, step counts and the UTC offset were kept.

#### Scenario: Ride imported earlier
- **WHEN** a viewer opens a ride imported before power was published
- **THEN** its page shows the stats and charts it showed before

#### Scenario: Swim imported before `hr` messages were read
- **WHEN** a viewer opens a pool swim imported without heart rate before this change
- **THEN** it still shows no heart rate

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

### Requirement: Heart rate from `hr` messages fills the heart rate stream
An activity imported from FIT whose file holds heart rate in `hr` messages SHALL store that heart rate in its heart rate stream: each sample SHALL take the average of the readings between the previous sample's time and its own. A sample that already has heart rate from its record SHALL keep it.

#### Scenario: Pool swim with a chest strap
- **WHEN** an athlete imports a FIT pool swim whose records have no heart rate and whose `hr` messages hold the strap's readings
- **THEN** its heart rate stream holds those readings, and its average and maximum heart rate come from them

#### Scenario: Records already have heart rate
- **WHEN** an athlete imports a FIT run whose records carry heart rate and whose file also holds `hr` messages
- **THEN** each sample's heart rate is its record's

### Requirement: HRV is summarized with the training load
An activity imported from FIT with RR intervals in `hrv` messages SHALL store their RMSSD, in milliseconds, with its training load, at the narrower of its charts visibility and the athlete's training visibility. Implausible intervals SHALL be dropped first. The intervals themselves SHALL NOT be stored outside the original file.

#### Scenario: Run with a chest strap recording HRV
- **WHEN** an athlete whose training is Only me imports a Public FIT run with `hrv` messages
- **THEN** its training load in the athlete's `self` space holds an RMSSD, and neither the activity record nor its streams hold HRV

#### Scenario: Artifacts dropped
- **WHEN** an athlete imports a FIT run whose RR intervals include a 4-second gap where the strap lost contact
- **THEN** the stored RMSSD is computed without that interval

#### Scenario: Too few intervals
- **WHEN** an athlete imports a FIT file with fewer than 30 plausible RR intervals
- **THEN** no RMSSD is stored

### Requirement: RMSSD shown only to viewers who may see training load
The activity's page SHALL show its RMSSD with its training load, only to viewers who may see its zones.

#### Scenario: Owner
- **WHEN** the athlete opens their own run with an RMSSD stored
- **THEN** the page shows its RMSSD with its training load

#### Scenario: Follower without training access
- **WHEN** a follower opens a Public run whose training load is in the athlete's `self` space
- **THEN** the page shows no RMSSD

### Requirement: Cycling dynamics, physiological and e-bike channels are kept
An activity imported from FIT SHALL store, where its records carry them, left and right torque effectiveness, left and right pedal smoothness, left and right platform centre offset, core temperature, muscle oxygen saturation (SmO₂), total hemoglobin concentration, and e-bike battery level, assist mode and assist level, each as a stream channel at the streams' visibility.

#### Scenario: Ride with cycling dynamics pedals
- **WHEN** an athlete imports a FIT ride recorded with dual-sided power pedals
- **THEN** its streams hold left and right torque effectiveness, pedal smoothness and platform centre offset

#### Scenario: Run with a CORE sensor and a Moxy
- **WHEN** an athlete imports a FIT run whose records carry core temperature, saturated hemoglobin percent and total hemoglobin concentration
- **THEN** its streams hold core temperature, SmO₂ and total hemoglobin, at the streams' visibility like heart rate

#### Scenario: E-bike ride
- **WHEN** an athlete imports a FIT e-bike ride whose records carry battery level and assist mode
- **THEN** its streams hold the battery level and assist mode

### Requirement: Known developer fields are kept
An activity imported from FIT SHALL recognize developer fields by their field name and units: Stryd's running power, form power and leg spring stiffness. Running power SHALL fill the power stream at samples whose record has no power. Form power and leg spring stiffness SHALL be stream channels. Other developer fields SHALL be ignored.

#### Scenario: Run with a Stryd and no native power
- **WHEN** an athlete imports a FIT run whose records carry a developer field `Power` in watts, `Form Power` and `Leg Spring Stiffness`, and no native power
- **THEN** its power stream holds the developer field's power, and its streams hold form power and leg spring stiffness

#### Scenario: Native power present
- **WHEN** an athlete imports a FIT run whose records carry native power and a developer `Power` field
- **THEN** its power stream holds the native power

#### Scenario: Unknown developer field
- **WHEN** an athlete imports a FIT ride with a developer field `Wind Speed`
- **THEN** no stream holds it

### Requirement: Step and stroke counts are published
An activity imported from FIT SHALL publish its session's cycle count as `cycles` and its average stroke distance, where recorded, on the activity record at the activity's visibility. On a run, walk or hike, `cycles` SHALL be steps, twice FIT's strides.

#### Scenario: Run
- **WHEN** an athlete imports a FIT run whose session records 4,200 strides
- **THEN** the published activity's `cycles` is 8,400

#### Scenario: Row
- **WHEN** an athlete imports a FIT indoor row whose session records 1,850 strokes and an average stroke distance of 10.2 m
- **THEN** the published activity's `cycles` is 1,850 and its average stroke distance is 10.2 m

### Requirement: Counts shown by sport
The activity's stats SHALL show `cycles` as steps for on-foot sports, as strokes for swimming, rowing and paddling, as pushes for wheelchair activities, and as pedal strokes for rides (every ride sub-sport, including mountain, gravel, indoor and e-bike) and velomobiles. They SHALL show average stroke distance where stored. Other sports SHALL NOT show `cycles`.

#### Scenario: Run page
- **WHEN** a viewer opens a run with `cycles` stored
- **THEN** its stats show its steps

#### Scenario: Ride page
- **WHEN** a viewer opens a mountain bike ride whose `cycles` holds 6,676 crank revolutions
- **THEN** its stats' Counts group shows 6,676 pedal strokes

#### Scenario: Handcycle
- **WHEN** a viewer opens a handcycle activity with `cycles` stored
- **THEN** its stats show no count

### Requirement: GPX and TCX notes prefill the description
A single GPX file's `trk/desc`, or a single TCX file's activity `Notes`, SHALL fill the import screen's description, which the athlete can keep, edit or clear before publishing.

#### Scenario: Strava GPX with a description
- **WHEN** an athlete imports a GPX file whose `trk/desc` reads "Hill repeats"
- **THEN** the import screen's description reads "Hill repeats"

#### Scenario: No notes
- **WHEN** an athlete imports a TCX file with no `Notes`
- **THEN** the import screen's description is empty

### Requirement: The activity keeps its UTC offset
An activity SHALL publish its UTC offset at its start, on the activity record at the activity's visibility: from FIT's `activity.local_timestamp` for a FIT import, from the zone the athlete placed a zone-less dive log's clock in, and from the device's zone for an activity recorded in the app. GPX and TCX imports and manual entries SHALL have none.

#### Scenario: FIT recorded in UTC+2
- **WHEN** an athlete imports a FIT ride whose activity message's local timestamp is two hours ahead of its timestamp
- **THEN** the published activity's UTC offset is +2:00

#### Scenario: Shearwater dive placed in a zone
- **WHEN** an athlete imports a Shearwater dive and places its clock in America/Cancun
- **THEN** the published dive's UTC offset is −5:00

#### Scenario: FIT without local time
- **WHEN** an athlete imports a FIT file whose activity message has no local timestamp
- **THEN** the published activity has no UTC offset

### Requirement: Import shows local times in the file's zone
The import screen SHALL use a FIT file's UTC offset, when it has one, for the default title's part of day and for the start time of an existing activity it may duplicate, instead of the device's zone.

#### Scenario: Morning run imported at night elsewhere
- **WHEN** an athlete in UTC−7 imports a FIT run started at 07:00 in UTC+9, with no title
- **THEN** the default title is a morning run

### Requirement: Average temperature from the samples
An activity imported from FIT whose records carry temperature SHALL publish as its average temperature the time-weighted mean of those samples, in tenths of a degree. The session's average SHALL be used only when no record carries a temperature. Activities imported earlier SHALL keep their stored average.

#### Scenario: Garmin ride
- **WHEN** an athlete imports a FIT ride whose session's average temperature is 19 °C and whose temperature samples average 19.24 °C
- **THEN** the published activity's average temperature is 192 tenths, which the page shows as 66.6 °F

#### Scenario: No temperature samples
- **WHEN** an athlete imports a FIT activity whose session records an average temperature and whose records carry none
- **THEN** the published activity's average temperature is the session's

### Requirement: Garmin physiology read on every platform
FIT import SHALL read exercise load, stamina and sweat loss the same way on Android, iOS, desktop and web, including the session fields the public FIT profile doesn't name. A file without them SHALL import with none stored, not zeros.

#### Scenario: Same file, every platform
- **WHEN** the same Garmin Edge 840 FIT ride is imported on the web app and on iOS
- **THEN** both store the same exercise load, stamina and sweat loss

#### Scenario: Older device
- **WHEN** an athlete imports a FIT ride from a device that records no stamina or sweat loss
- **THEN** its training load holds neither

### Requirement: Exported FIT keeps Garmin physiology
A FIT export SHALL write the activity's exercise load, stamina and sweat loss into its session, where the exporter may see them, in the same fields import reads.

#### Scenario: Round trip
- **WHEN** the owner exports a ride with exercise load 187 and sweat loss 764 ml to FIT and imports that file again
- **THEN** the new activity's training load holds exercise load 187 and sweat loss 764 ml
