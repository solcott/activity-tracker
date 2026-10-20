# Spec Delta

## MODIFIED Requirements

### Requirement: Categories chosen for each activity
Recording, manual entry, import and batch import SHALL each let the athlete set the visibility of each category the new activity has data in, prefilled from the defaults, on every platform. A batch SHALL offer each category any selected activity has data in. Recording SHALL offer Start time, Map, and Pace and speed. Start time SHALL always be offered. Changing them SHALL affect only that activity or batch. Editing an activity SHALL let the athlete change them, within the cap. A category with no data on the activity SHALL NOT be offered when creating or editing it.

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
- **WHEN** an athlete imports a mountain bike ride file with a GPS track, heart rate, power, speed, cadence and calories, and no dive or physiology data
- **THEN** the form offers Start time, Heart rate, Power, Pace and speed, Calories, Map and Cadence, and doesn't offer Physiology or Dive data

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

#### Scenario: Choice kept while the offer changes
- **WHEN** an athlete on a manual entry sets Pace and speed to Only me, clears the distance, then enters it again
- **THEN** Pace and speed is offered again at Only me

#### Scenario: Category not offered publishes nothing
- **WHEN** an athlete whose Dive data default is Only me saves a manual entry
- **THEN** the activity has no Only-me details or streams record for it

## ADDED Requirements

### Requirement: Cadence label follows the activity's data
On a form for one activity, the Cadence category SHALL be labelled "Cadence" unless the activity has running-form data (vertical oscillation, stance time, step length, vertical ratio, stance time balance or leg spring stiffness). Then it SHALL be labelled "Cadence and running form". Default visibility in Settings SHALL keep the label "Cadence and running form".

#### Scenario: Ride with pedalling cadence
- **WHEN** an athlete imports a ride with a cadence channel and no running-form channels
- **THEN** its Cadence picker is labelled "Cadence"

#### Scenario: Run with running dynamics
- **WHEN** an athlete edits a run with cadence and stance time channels
- **THEN** its picker is labelled "Cadence and running form"

#### Scenario: Defaults
- **WHEN** an athlete opens default visibility in Settings
- **THEN** the category is labelled "Cadence and running form"
