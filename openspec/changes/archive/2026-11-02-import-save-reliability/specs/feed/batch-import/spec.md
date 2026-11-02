## MODIFIED Requirements

### Requirement: Large archives import without holding them in memory
An archive SHALL be read without loading it whole, and the batch SHALL keep only each activity's summary until the athlete previews or saves it. Saving SHALL hold one file's activities at a time and SHALL NOT keep an activity's route, streams or original file once it is queued. A batch of thousands of activities from an archive of several gigabytes SHALL be reviewable and savable on a phone and in the browser.

#### Scenario: Full account export on a phone
- **WHEN** an athlete picks a 3 GB Strava export holding 2,000 activities on an Android phone
- **THEN** the batch shows all 2,000 rows without the app running out of memory

#### Scenario: Saving a full account export on a phone
- **WHEN** the athlete saves all 2,000 activities of that export on an Android phone
- **THEN** all 2,000 are queued without the app running out of memory

## ADDED Requirements

### Requirement: A failed save says what was saved
When saving a batch fails partway, the screen SHALL show the failure next to Save, with how many activities were saved. The activities already saved SHALL leave the selection, and those not yet saved SHALL stay selected.

#### Scenario: Save fails after 300 of 1,000
- **WHEN** saving a batch of 1,000 fails after 300 were queued
- **THEN** the screen says 300 of 1,000 were saved and why the rest weren't, next to Save, and 700 stay selected

### Requirement: Saving again continues the import
Saving again after a failed save SHALL save only the activities still selected, as part of the same import in the upload queue, so no activity is queued twice.

#### Scenario: Retry after a failure
- **WHEN** the athlete taps Save again after 300 of 1,000 were saved
- **THEN** the other 700 are saved, and the upload queue lists one import of 1,000

### Requirement: Saving outlives the screen and the app
A batch save SHALL continue after the athlete leaves the import screen or the app, and SHALL resume on its own after the app is frozen or killed, until every selected activity is saved or skipped, the athlete cancels the rest, or they sign out. No activity SHALL be queued twice by resuming. Opening import while a save runs SHALL show its progress.

#### Scenario: Back during a save
- **WHEN** the athlete goes Back while a batch of 500 is saving, and opens import again
- **THEN** the save has gone on, and the screen shows its progress

#### Scenario: App killed mid-save on Android
- **WHEN** Android kills the app after 120 of a batch's 400 activities were saved
- **THEN** the save resumes in the background without the athlete opening the app, saves the other 280, and the upload queue lists one import of 400

#### Scenario: App swiped away on iOS
- **WHEN** the athlete swipes the app away after 120 of 400 were saved on iOS
- **THEN** the save resumes when iOS next runs the app's background task, or when the athlete next opens the app, and saves the other 280

#### Scenario: Desktop app quit
- **WHEN** the athlete quits the desktop app mid-save and opens it again
- **THEN** the save resumes and saves the rest

#### Scenario: Sign out
- **WHEN** the athlete signs out while a batch is saving
- **THEN** the save stops, and its unsaved activities are dropped

### Requirement: Saving shows its progress in the background
On Android, while a batch is being saved, the app SHALL show an ongoing notification with how many of the batch's activities are saved out of all selected, which goes when the save ends.

#### Scenario: Leave during a save
- **WHEN** the athlete leaves the app while a batch of 500 is being saved on Android
- **THEN** a notification shows the save's progress until all 500 are saved, then goes

### Requirement: An import that can't resume is reported
When a save can't resume because a picked file can no longer be opened, or because a web tab closed mid-save, the import SHALL say how many activities were saved out of how many were selected, and that picking the files again imports the rest. Picking the same files again SHALL flag the saved ones as already in the athlete's log.

#### Scenario: Web tab closed mid-save
- **WHEN** the browser tab closes after 120 of 400 activities were saved, and the athlete opens the app again
- **THEN** the import says it stopped after 120 of 400 and to pick the files again, and picking them flags those 120 as duplicates

#### Scenario: File access revoked
- **WHEN** a picked file was deleted before an interrupted save could resume
- **THEN** that file's unsaved activities are reported as skipped, and the save goes on with the rest

### Requirement: Picked files are held only while saving
Access the app keeps to the athlete's picked files so a save can resume, and any copies made of them, SHALL be released when the save ends, is cancelled, or the athlete signs out.

#### Scenario: Save finished
- **WHEN** a save of 400 activities from 400 picked files finishes on Android
- **THEN** the app keeps no access grant to those files and no copy of them
