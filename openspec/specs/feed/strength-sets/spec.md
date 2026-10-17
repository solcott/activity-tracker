# feed/strength-sets Specification

## Purpose

Keeps the sets a strength, HIIT or cardio workout records (each exercise, its reps and weight, and the rests between), and shows them on the activity's page, so an imported workout keeps more than its time and heart rate.

## Requirements

### Requirement: Sets are kept
An activity imported from FIT SHALL store each set it records, in order, with its start, elapsed time, whether it was active or a rest, and, where recorded, its exercise category, exercise name, reps and weight in grams. Sets SHALL be stored with the streams, at their visibility.

#### Scenario: Strength workout
- **WHEN** an athlete imports a FIT strength workout of three sets of 8 bench presses at 60 kg with rests between
- **THEN** its streams hold three active sets, each with the bench press exercise, 8 reps and 60000 g, and the rests between them

#### Scenario: Bodyweight set
- **WHEN** an athlete imports a FIT workout with a set of 20 push-ups and no weight
- **THEN** the set is stored with its exercise and 20 reps and no weight

#### Scenario: Workout without sets
- **WHEN** an athlete imports a FIT cardio workout with no set messages
- **THEN** its streams hold no sets

### Requirement: Exercises are kept as recorded
A set's exercise category and name SHALL be kept as the file's tokens, an open set, so an exercise the app doesn't know is stored as recorded. A set with a category and no name SHALL keep its category.

#### Scenario: Unknown exercise
- **WHEN** an athlete imports a FIT set whose exercise name the app has no label for
- **THEN** the set is stored with that exercise's token

#### Scenario: Category only
- **WHEN** an athlete imports a FIT set recorded as the squat category with no exercise name
- **THEN** the set is stored with the squat category and no exercise name

### Requirement: Activity page shows sets
An activity's page SHALL show a sets table, to viewers the streams admit, when sets are stored. Each active set SHALL show its exercise, reps, weight and time; each rest its time. An exercise the app has no label for SHALL show a readable form of its token, falling back to its category.

#### Scenario: Viewer who may see streams
- **WHEN** a viewer who may see the streams opens a strength workout with sets stored
- **THEN** a sets table lists each set's exercise, reps, weight and time, and each rest's time

#### Scenario: Viewer who may not see streams
- **WHEN** a viewer opens a Public strength workout whose streams are Only me
- **THEN** no sets table is shown

#### Scenario: Unlabelled exercise
- **WHEN** a viewer opens a workout with a set whose exercise token is `single_arm_cable_row`
- **THEN** the set's exercise shows as "Single arm cable row"

### Requirement: Merging fills missing sets
Merging another recording into an activity SHALL add its sets when the activity has none, and SHALL NOT replace sets it has.

#### Scenario: Watch adds sets
- **WHEN** an athlete merges a watch's FIT strength workout into a chest strap's recording of it with no sets
- **THEN** the chest strap's recording gains the watch's sets and keeps its own heart rate

### Requirement: Older workouts are unchanged
Activities imported before sets were kept SHALL show as before, with no sets table, and SHALL NOT be backfilled.

#### Scenario: Workout imported earlier
- **WHEN** a viewer opens a strength workout imported before sets were kept
- **THEN** its page shows the stats it showed before and no sets table
