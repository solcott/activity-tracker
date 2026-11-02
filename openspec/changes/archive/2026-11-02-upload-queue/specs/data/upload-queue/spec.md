# Spec Delta

## Purpose

Shows the athlete what they've saved that is still uploading to their PDS and what has finished, one item per activity, with imports grouped, and lets them cancel what hasn't started and pause imports.

## ADDED Requirements

### Requirement: Saved and edited activities are listed
Each activity the athlete saves, imports or edits SHALL be listed in the upload queue from when it's saved until it's cleared. Deleting an activity, liking, commenting, blocking, muting and changing an activity's visibility SHALL NOT add an item.

#### Scenario: Recording saved
- **WHEN** the athlete saves a recorded run
- **THEN** the upload queue lists the run

#### Scenario: Like
- **WHEN** the athlete likes a friend's ride
- **THEN** the upload queue lists no item for the like

### Requirement: An activity is one item
An activity SHALL be listed once while it uploads, however many writes it takes. An edit to an activity that is still waiting or uploading SHALL update that item's title and type, not add another. An edit to an activity already uploaded SHALL be listed as a new item that says it updates the activity.

#### Scenario: Edit while waiting
- **WHEN** the athlete renames an imported activity that is still waiting to upload
- **THEN** the queue lists it once, under its new name

#### Scenario: Edit after upload
- **WHEN** the athlete renames "Morning Ride" after it uploaded
- **THEN** the queue lists "Morning Ride" as uploaded and a second item updating "Morning Ride"

### Requirement: Item states
Each item SHALL show one state:
- **Waiting**: none of its writes is sent.
- **Uploading**: some are sent, or one is being sent.
- **Uploaded**: all are sent.
- **Refused**: one was refused by the server.
- **Skipped**: an imported activity couldn't be read again at save.

#### Scenario: Upload finishes
- **WHEN** the last write of a waiting activity is sent
- **THEN** its item shows as uploaded

#### Scenario: Server refuses
- **WHEN** the server refuses an imported activity's record
- **THEN** its item shows as refused, and the import's other activities keep uploading

### Requirement: Uploading progress
An uploading item SHALL show how many of its writes are sent out of all its writes sent or still queued.

#### Scenario: Activity part way
- **WHEN** an activity has had 2 of its 5 writes sent
- **THEN** its item shows 2 of 5

### Requirement: Imports are grouped
The activities of one saved import SHALL be listed together under it, with:
- what was imported
- when it was saved
- how many of its activities are uploaded out of all it saved
- how many are refused and skipped

The athlete's other items SHALL be listed apart from imports.

#### Scenario: Import part way
- **WHEN** 412 of an import's 1,362 saved activities are uploaded and 3 were skipped at save
- **THEN** its group shows 412 of 1,362 uploaded and 3 skipped

### Requirement: Order of the queue
The item uploading now SHALL be listed first. Then come the athlete's own waiting items, then each import's waiting items, each in the order they'll be sent, and then finished items, newest first.

#### Scenario: Recording during an import
- **WHEN** an import is uploading and the athlete saves a recording
- **THEN** the recording is listed ahead of the import's waiting activities

### Requirement: Finished items stay for 7 days
Uploaded and skipped items SHALL stay listed for 7 days after they finished, and SHALL then be removed. The athlete SHALL be able to clear finished items sooner. An import SHALL stay listed while any of its items is.

#### Scenario: Import done overnight
- **WHEN** an import finishes uploading at 2 AM and the athlete opens the queue at 8 AM
- **THEN** the import is listed with every activity uploaded and its skipped ones

#### Scenario: Week later
- **WHEN** 7 days have passed since an item uploaded
- **THEN** the queue no longer lists it

### Requirement: Cancelling a waiting item
The athlete SHALL be able to cancel a waiting or refused item. Cancelling SHALL drop all its queued writes from the device, so none is sent. A new activity cancelled SHALL disappear from the athlete's lists. A cancelled edit SHALL leave the activity as last uploaded. An uploading or uploaded item SHALL NOT be cancellable.

#### Scenario: Wrong activity imported
- **WHEN** the athlete cancels a waiting imported activity
- **THEN** none of its writes is sent, and it no longer appears in the feed or on their profile

#### Scenario: Activity already uploading
- **WHEN** an activity has had some of its writes sent
- **THEN** the queue offers no way to cancel it

### Requirement: Cancelling the rest of an import
The athlete SHALL be able to cancel an import's waiting and refused items at once. Its uploading and uploaded activities SHALL be kept.

#### Scenario: Stop a large import
- **WHEN** the athlete cancels the rest of an import with 412 uploaded, 1 uploading and 949 waiting
- **THEN** the 949 are dropped, and the 413 others are kept and finish uploading

### Requirement: Pausing imports
The athlete SHALL be able to pause and resume imports. While paused, no import's writes SHALL be sent. The athlete's own activities, edits and other changes SHALL still be sent. The pause SHALL last until the athlete resumes, also after the app restarts.

#### Scenario: Recording while imports are paused
- **WHEN** imports are paused and the athlete saves a recording
- **THEN** the recording uploads, and the imports' activities stay waiting

#### Scenario: App restarted
- **WHEN** the athlete pauses imports and restarts the app
- **THEN** imports stay paused until the athlete resumes them

### Requirement: Paused states are told apart
The queue SHALL say when imports are paused by the athlete, and when uploads are paused by the server and until what local time. When both apply, it SHALL say both. Resuming SHALL send nothing until the server's pause ends.

#### Scenario: Both pauses
- **WHEN** the athlete paused imports and the server paused uploads until 3:40 PM
- **THEN** the queue says imports are paused by the athlete and uploads by the server until 3:40 PM

### Requirement: Time left for imports
While imports are uploading and not paused by the athlete, the queue SHALL estimate the time left for their waiting activities. It SHALL base the estimate on the limits the server stated and the pace seen so far. It SHALL show no estimate when it has neither.

#### Scenario: Hourly limit known
- **WHEN** the server states an hourly limit and an import's remaining writes need more than a day of that limit
- **THEN** the queue shows a time left of more than a day

#### Scenario: Nothing to estimate from
- **WHEN** the server stated no limit and nothing has been sent yet
- **THEN** the queue shows no time left

### Requirement: Other changes are counted
Changes that aren't activities (likes, comments, blocks…), and activity deletes, SHALL be counted in the queue while they upload but not listed. Those the server refused SHALL be listed with the refused items, named by their kind.

#### Scenario: Refused like
- **WHEN** the server refuses a like
- **THEN** the queue lists a refused like, which the athlete can retry or discard

### Requirement: Where uploads continue
On desktop and web, the queue SHALL say that uploads continue only while the app is open.

#### Scenario: Web import
- **WHEN** the athlete opens the queue in the browser during an import
- **THEN** it says uploads continue only while the app is open

### Requirement: Closing during an import
On desktop and web, closing the app while an import has activities waiting and imports are not paused by the athlete SHALL ask the athlete to confirm.

#### Scenario: Close the tab
- **WHEN** the athlete closes the browser tab while an import uploads
- **THEN** the browser asks before closing

#### Scenario: Paused import
- **WHEN** the athlete paused imports and closes the tab
- **THEN** the tab closes without asking

### Requirement: Progress notification on Android
On Android, while imports upload in the background, the app SHALL show a notification with how many of their activities are uploaded out of all saved. It SHALL do so only when the athlete allows notifications, and SHALL remove it when imports finish or are paused.

#### Scenario: Background import
- **WHEN** an import of 1,362 activities uploads with the app closed and notifications allowed
- **THEN** a notification shows the import's progress, updated as activities upload
