# dev/sample-activity-files Specification

## Purpose

A repeatable set of sample activity files, generated from the app's own writers, for checking by hand how imports, the activity page and exports handle workout structure.

## Requirements

### Requirement: Samples generated on request
A documented Gradle command SHALL write the sample files to `build/workout-fixtures`, and SHALL NOT run as part of `./gradlew check`.

#### Scenario: Generating the samples
- **WHEN** a developer runs the documented command
- **THEN** `build/workout-fixtures` holds the pool swim, strength workout, triathlon, ski day, MTB ride, TCX interval run, TCX duathlon and sparse TCX run files, each importable by the app

#### Scenario: Ordinary checks
- **WHEN** `./gradlew check` runs
- **THEN** no sample files are written

### Requirement: Samples are self-consistent
Each sample's laps, splits and legs SHALL carry an average speed that matches their distance and time, and every stored average (heart rate, power, cadence, speed) SHALL match its samples and lie between their minimum and maximum.

#### Scenario: Triathlon heart rate
- **WHEN** the triathlon sample is imported
- **THEN** its average heart rate lies within the range of its heart-rate samples

#### Scenario: Lap speed
- **WHEN** the MTB ride sample is imported
- **THEN** its lap table shows a speed for every lap
