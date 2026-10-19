# feed/activity-source-file Specification

## Purpose

Keeps the file an athlete imported an activity from, exactly as it was, where only they can read it, and lets them download it again, so data the app doesn't parse or store isn't lost at import.

## Requirements

### Requirement: An imported activity keeps its original file
Importing an activity from a file SHALL store that file as the activity's source: its bytes as imported, its format, its file name as picked and its size. Activities recorded in the app or entered by hand SHALL have no source.

#### Scenario: Import a FIT file
- **WHEN** an athlete imports and saves `Morning_Ride.fit`
- **THEN** the activity has a source holding that file's bytes, format FIT, name `Morning_Ride.fit` and its size

#### Scenario: Import a gzipped file
- **WHEN** an athlete imports `12345.fit.gz` from a Strava bulk export
- **THEN** the activity's source holds the gzipped bytes as picked, named `12345.fit.gz`, with format FIT

#### Scenario: Recorded in the app
- **WHEN** an athlete records a run in the app and saves it
- **THEN** the run has no source

#### Scenario: Manual entry
- **WHEN** an athlete saves a manual entry
- **THEN** it has no source

### Requirement: The source is stored only in the athlete's self space
An activity's source SHALL be stored in the athlete's `self` space, under the activity's record key, whatever the activity's visibility, and nowhere else. Changing the activity's visibility SHALL NOT move or change it.

#### Scenario: Public import
- **WHEN** an athlete imports a GPX file as a Public activity
- **THEN** its source is in the athlete's `self` space, and their repo holds no record or blob of the file

#### Scenario: Visibility change
- **WHEN** an athlete changes an imported activity from Followers to Public
- **THEN** its source stays in the `self` space, unchanged

### Requirement: Only the athlete reads the source
An activity's source SHALL be readable only by the athlete who owns it. The AppView SHALL NOT index or serve it, and no view of the activity SHALL link to it.

#### Scenario: Follower opens an imported activity
- **WHEN** a follower opens an athlete's Public activity imported from a FIT file
- **THEN** the views they get name no source file, and they can't read the athlete's `self` space

#### Scenario: AppView sees the source
- **WHEN** the AppView syncs the athlete's `self` space holding a source
- **THEN** it stores nothing from it

### Requirement: The source is written with its activity
The source SHALL be queued with its activity and sent after it, offline or online, so an imported activity that reaches the PDS gets its source too.

#### Scenario: Import offline
- **WHEN** an athlete imports a TCX file without a connection, then reconnects
- **THEN** both the activity and its source are written

### Requirement: A logbook file is stored once for all its dives
When several activities are imported from one logbook file (UDDF or Subsurface), the file SHALL be uploaded once, and each saved activity's source SHALL refer to that one copy and name which entry of the file, in file order, it was read from.

#### Scenario: Logbook of three dives
- **WHEN** an athlete imports a Subsurface logbook and saves its three dives
- **THEN** each dive has a source naming the same stored file and entries 0, 1 and 2, and the file was uploaded once

#### Scenario: Some dives picked
- **WHEN** an athlete imports a ten-dive UDDF logbook and saves only its 4th and 7th dives
- **THEN** those two dives have sources naming entries 3 and 6 of the one stored file

### Requirement: Oversized files import without their original
A file whose stored form exceeds 10 MB SHALL import without a source, and the import screen SHALL say so before the athlete saves. If the PDS refuses to store a source, that source SHALL be dropped and every other queued write SHALL still be sent.

#### Scenario: Large GPX
- **WHEN** an athlete picks a GPX file that is still over 10 MB once compressed
- **THEN** the import screen says the original file won't be kept, and the saved activity has no source

#### Scenario: PDS refuses the blob
- **WHEN** the athlete's PDS refuses a source's file as too large
- **THEN** the activity, its streams and its track are written, the activity has no source, and later writes aren't held up

### Requirement: Grouped recordings keep their own source
An imported activity combined with a logged activity SHALL keep its own source as a member, and a combined activity SHALL have no source of its own. Splitting a combined activity, removing a member, and editing an activity's title, description, dive site or photos, SHALL leave each source as it is.

#### Scenario: Combine a duplicate
- **WHEN** an athlete imports a FIT dive and combines it with a logged dive
- **THEN** the imported member has the FIT file as its source, and the logged dive's source is unchanged

#### Scenario: Split
- **WHEN** an athlete splits a combined activity into its members
- **THEN** each member keeps its source

#### Scenario: Group a duplicate
- **WHEN** an athlete's legacy group holds a FIT dive as another recording of a logged dive
- **THEN** the recording has the FIT file as its source, and the logged dive's source is unchanged

#### Scenario: Ungroup
- **WHEN** an athlete splits a recording off a legacy group's main activity
- **THEN** the recording and the main activity keep their sources

### Requirement: Deleting an activity deletes its source
Deleting an activity SHALL delete its source. Deleting a main activity with its recordings SHALL delete each recording's source too; recordings split off instead SHALL keep theirs. Deleting one dive from a logbook SHALL leave the other dives' sources readable.

#### Scenario: Delete an import
- **WHEN** an athlete deletes an activity imported from a FIT file
- **THEN** its source is gone from the `self` space

#### Scenario: Delete one dive of a logbook
- **WHEN** an athlete deletes one of three dives imported from one UDDF logbook
- **THEN** the other two dives can still download the logbook

### Requirement: The athlete can download the original
On their own activity with a source, the athlete SHALL be able to download it. The file SHALL be byte for byte what they imported, under its original name, handed to the platform as exports are (share sheet, save dialog or browser download). For a logbook entry it SHALL be the whole logbook file.

#### Scenario: Download a FIT original
- **WHEN** an athlete downloads the original of an activity imported from `Morning_Ride.fit`
- **THEN** they get `Morning_Ride.fit` with the same bytes they imported

#### Scenario: Download a gzipped original
- **WHEN** an athlete downloads the original of an activity imported from `12345.fit.gz`
- **THEN** they get `12345.fit.gz`, still gzipped, with the same bytes

#### Scenario: Download a logbook dive's original
- **WHEN** an athlete downloads the original of one dive imported from `log.ssrf`
- **THEN** they get the whole `log.ssrf` file

### Requirement: Download waits until the source is sent
While an activity's source is still queued to be sent, the app SHALL say the original can be downloaded once it's sent, and produce no file. When the source can't be read (offline, an unreadable blob), the download SHALL fail with an error on the detail screen and hand off no file.

#### Scenario: Source still in the outbox
- **WHEN** an athlete tries to download the original of an activity they imported offline
- **THEN** the app says it can be downloaded once it's sent, and no file is handed off

#### Scenario: Offline
- **WHEN** an athlete downloads an original without a connection
- **THEN** the detail screen shows the download failed, and no file is handed off
