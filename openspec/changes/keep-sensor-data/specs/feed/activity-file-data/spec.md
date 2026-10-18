# Spec Delta

## ADDED Requirements

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
The activity's stats SHALL show `cycles` as steps for on-foot sports, as strokes for swimming, rowing and paddling, and as pushes for wheelchair activities, and SHALL show average stroke distance where stored. Other sports SHALL NOT show `cycles`.

#### Scenario: Run page
- **WHEN** a viewer opens a run with `cycles` stored
- **THEN** its stats show its steps

#### Scenario: Ride page
- **WHEN** a viewer opens a ride whose `cycles` holds crank revolutions
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

### Requirement: New data merges like other channels
Merging another recording into an activity SHALL add each new channel, `cycles`, average stroke distance, UTC offset and RMSSD that the activity lacks, and SHALL NOT overwrite any it has.

#### Scenario: Pedals add cycling dynamics
- **WHEN** an athlete merges a bike computer's FIT ride with cycling dynamics into their watch's recording
- **THEN** the watch's recording gains the cycling dynamics channels and keeps its own heart rate

## MODIFIED Requirements

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
