# Spec Delta

## MODIFIED Requirements

### Requirement: Every piece of activity data has one category
Every summary field, stream channel, and lap or leg field of an activity SHALL belong to exactly one detail category (Start time, Heart rate, Power, Pace/Speed, Calories, Map, Physiology, Cadence & running form, Dive, or Gear) or be core. Core data SHALL always be at the activity's visibility. Data in no category SHALL be core.

#### Scenario: Heart rate everywhere it appears
- **WHEN** an activity has average and max heart rate, a heart rate channel, and laps with heart rate
- **THEN** all of them are in the Heart rate category

#### Scenario: Power family
- **WHEN** an activity has average, max and normalized power, work, and power, left/right balance, torque effectiveness, pedal smoothness, platform center offset and form power channels
- **THEN** all of them are in the Power category

#### Scenario: Physiology
- **WHEN** an activity has respiration rate, core temperature, SmO2 or total hemoglobin channels
- **THEN** they are in the Physiology category

#### Scenario: Cadence and running form
- **WHEN** an activity has average and max cadence, a cadence channel, laps with cadence, and vertical oscillation, stance time, step length, vertical ratio, stance time balance or leg spring stiffness channels
- **THEN** all of them are in the Cadence & running form category

#### Scenario: Dive
- **WHEN** a dive has depth, ppO2, CNS, N2 load, NDL, deco stop depth and time, TTS, gas switch, dive alert or tank pressure data
- **THEN** all of them are in the Dive category, and the dive summary (such as max depth and bottom time) is core

#### Scenario: Gear
- **WHEN** an activity lists the gear it used
- **THEN** that list is in the Gear category

#### Scenario: Core fields aren't hidden
- **WHEN** an athlete sets every category of a Public activity to Only me
- **THEN** any viewer still sees its sport, title, date, distance, elapsed and moving time, elevation gain, and its distance, altitude and temperature charts

#### Scenario: Uncategorized data is core
- **WHEN** an activity has gears, e-bike, jumps, grit or flow data
- **THEN** it is stored and served at the activity's visibility

### Requirement: Default visibility per category
An athlete SHALL be able to set a default visibility for each category in Settings, stored on their `actor.activityDefaults` record in their `self` space. A category default wider than the default activity visibility SHALL be read as the activity default. A category never set SHALL start at the default activity visibility, except Gear, which SHALL start at Only me. There SHALL be no separate charts default.

#### Scenario: Never set
- **WHEN** an athlete who never set any defaults starts a new activity
- **THEN** the activity and every category except Gear start as Public, and Gear starts as Only me

#### Scenario: Only the activity default set
- **WHEN** an athlete whose defaults record sets only the activity visibility, Followers, starts a new activity
- **THEN** every category except Gear starts as Followers, and Gear starts as Only me

#### Scenario: Gear default set
- **WHEN** an athlete whose activity default is Public sets the Gear default to Public
- **THEN** the Gear category of a new activity starts as Public

#### Scenario: Old charts default ignored
- **WHEN** an athlete's defaults record still has a `chartsVisibility` of Only me and no category defaults, with activity Public
- **THEN** every category of a new activity except Gear starts as Public

#### Scenario: Category default capped
- **WHEN** an athlete sets the default activity visibility to Mutuals while the Calories default is Public
- **THEN** the Calories default becomes Mutuals

#### Scenario: Changing defaults leaves past activities
- **WHEN** an athlete changes the Heart rate default to Only me
- **THEN** their past activities keep their heart rate visibility

### Requirement: Categories chosen for each activity
Recording, manual entry, import and batch import SHALL each let the athlete set the visibility of each category the new activity has data in (for a batch, any selected activity; for a recording, Start time, Map, and Pace and speed), prefilled from the defaults, on every platform. Gear SHALL count as data whenever the activity lists gear. Changing them SHALL affect only that activity or batch. Editing SHALL let the athlete change them, within the cap. A category with no data SHALL NOT be offered.

#### Scenario: Override for one activity
- **WHEN** an athlete whose Power default is Only me sets one import's Power to Public
- **THEN** that activity's power is Public, and the default stays Only me

#### Scenario: Batch import
- **WHEN** an athlete imports several dives at once with Heart rate Followers
- **THEN** every activity in the batch has Heart rate Followers

#### Scenario: Edit after publishing
- **WHEN** an athlete opens edit for a Public ride and sets Power to Mutuals
- **THEN** after saving, only the athlete and their mutuals get its power data

#### Scenario: Importing a mountain bike ride
- **WHEN** an athlete imports a mountain bike ride file with a GPS track, heart rate, power, speed, cadence and calories, and no dive or physiology data, and picks no gear
- **THEN** the form offers Start time, Heart rate, Power, Pace and speed, Calories, Map and Cadence, and doesn't offer Physiology, Dive data or Gear

#### Scenario: Dive without a GPS track
- **WHEN** an athlete imports a dive with depth and heart rate data and no GPS track
- **THEN** the form offers Dive data and Heart rate, and doesn't offer Map

#### Scenario: Batch offers the union
- **WHEN** an athlete selects two dives from a logbook, only one of which has tank pressures and only the other heart rate
- **THEN** the form offers both Dive data and Heart rate

#### Scenario: Manual entry
- **WHEN** an athlete fills in a manual entry with a duration and no distance
- **THEN** the form offers only Start time

#### Scenario: Manual entry with a distance
- **WHEN** the athlete then enters a distance
- **THEN** the form also offers Pace and speed

#### Scenario: Recording
- **WHEN** an athlete starts a recording
- **THEN** the form offers Start time, Map, and Pace and speed

#### Scenario: Gear picked
- **WHEN** an athlete picks a bike for a recording before saving it
- **THEN** the form also offers Gear

#### Scenario: Choice kept while the offer changes
- **WHEN** an athlete on a manual entry sets Pace and speed to Only me, clears the distance, then enters it again
- **THEN** Pace and speed is offered again at Only me

#### Scenario: Category not offered publishes nothing
- **WHEN** an athlete whose Dive data default is Only me saves a manual entry
- **THEN** the activity has no Only-me details or streams record for it
