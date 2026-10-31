# Spec Delta

## MODIFIED Requirements

### Requirement: A logbook file is stored once for all its dives
Each picked file, and each activity file inside a picked archive, SHALL be uploaded once however many activities are saved from it. Each saved activity's source SHALL refer to that one copy and name which entry of it, in file order, the activity was read from. A file inside an archive SHALL be stored as it was in the archive, under its own name; the archive itself SHALL NOT be stored.

#### Scenario: Logbook of three dives
- **WHEN** an athlete imports a Subsurface logbook and saves its three dives
- **THEN** each dive has a source naming the same stored file and entries 0, 1 and 2, and the file was uploaded once

#### Scenario: Some dives picked
- **WHEN** an athlete imports a ten-dive UDDF logbook and saves only its 4th and 7th dives
- **THEN** those two dives have sources naming entries 3 and 6 of the one stored file

#### Scenario: Activity from a Strava export
- **WHEN** an athlete imports a Strava export and saves the activity read from `activities/123.fit.gz`
- **THEN** its source holds that member's gzipped bytes, named `123.fit.gz`, with format FIT, and the export zip is stored nowhere

#### Scenario: Several files in one pick
- **WHEN** an athlete picks three FIT files together and saves all three
- **THEN** each activity has its own file as its source, each uploaded once

### Requirement: Oversized files import without their original
A picked file or archive member whose stored form exceeds 10 MB SHALL import without a source, and the import screen SHALL say so before the athlete saves, naming the files in a batch. If the PDS refuses to store a source, that source SHALL be dropped and every other queued write SHALL still be sent.

#### Scenario: Large GPX
- **WHEN** an athlete picks a GPX file that is still over 10 MB once compressed
- **THEN** the import screen says the original file won't be kept, and the saved activity has no source

#### Scenario: Large member of a small batch
- **WHEN** an athlete picks a zip in which one GPX member is over 10 MB once compressed
- **THEN** the batch form says that file's original won't be kept, and the other activities keep theirs

#### Scenario: PDS refuses the blob
- **WHEN** the athlete's PDS refuses a source's file as too large
- **THEN** the activity, its streams and its track are written, the activity has no source, and later writes aren't held up
