# Spec Delta

## MODIFIED Requirements

### Requirement: The athlete sees what couldn't be published
The feed SHALL show how many changes couldn't be published, apart from what is still uploading, and SHALL open the upload queue, which lists them with its refused items. Each item SHALL name the change, by the activity's name for an activity and by its kind (a like, a comment, a block…) otherwise, and SHALL say the server refused it, without raw error text.

#### Scenario: One refused activity
- **WHEN** the athlete's activity "Morning ride" was rejected
- **THEN** the feed says one change couldn't be published, and the upload queue shows "Morning ride" as refused by the server, with no error code or message from the PDS

#### Scenario: Nothing rejected
- **WHEN** no queued write is rejected
- **THEN** the feed shows no "couldn't be published" row

### Requirement: Uploads continue in the background on phones
On Android and iOS, when the app leaves the foreground with writes still queued that may be sent, the client SHALL ask the system to run the upload in the background with a network connection. A paused upload SHALL ask to run no earlier than the pause ends. Each background run SHALL send writes as the app would, stop when the system asks, and ask for another run while such writes remain. Writes held only by the athlete's pause of imports SHALL NOT ask for a run. On desktop and web, uploads SHALL continue only while the app is open. Only one upload SHALL run at a time per account.

#### Scenario: Android app closed during an import
- **WHEN** the athlete saves an import of 1,000 activities on Android and closes the app
- **THEN** the upload carries on in the background, pausing for each rate limit, until every write is sent

#### Scenario: Signed out
- **WHEN** the athlete signs out with writes still queued
- **THEN** no background upload runs for that account

#### Scenario: Imports paused by the athlete
- **WHEN** the athlete pauses imports and closes the app with only import writes queued
- **THEN** no background upload runs until they resume

### Requirement: The athlete sees when uploads are paused
While the upload waits out a rate limit, the feed SHALL say, beside what is still uploading, that the server paused the upload and the local time it will go on. While the athlete has paused imports, it SHALL say so.

#### Scenario: Paused import
- **WHEN** 3,000 imported activities are queued and the server paused the upload until 3:40 PM
- **THEN** the feed says 3,000 imported activities are uploading, paused by the server until 3:40 PM

#### Scenario: Not paused
- **WHEN** changes are uploading and no pause is in effect
- **THEN** the feed shows only what is uploading

#### Scenario: Paused by the athlete
- **WHEN** the athlete paused imports with 900 imported activities waiting
- **THEN** the feed says 900 imported activities are waiting, paused

## ADDED Requirements

### Requirement: The feed counts activities uploading
The feed's uploading row SHALL count activities, not writes: the athlete's own activities and edits apart from imported ones, plus how many other changes are uploading. Tapping it SHALL open the upload queue.

#### Scenario: Recording during an import
- **WHEN** one recorded run and 950 imported activities are still uploading, along with 3 likes
- **THEN** the feed says 1 activity and 950 imported are uploading, and 3 other changes

### Requirement: Cancelled writes are never sent
Writes the athlete cancels SHALL be removed from the device before any of them is sent. Cancelling SHALL be refused for an activity once any of its writes has been sent or is being sent.

#### Scenario: Cancel just before sending
- **WHEN** the athlete cancels a waiting activity at the moment the upload reaches it
- **THEN** either no write of it is sent and it's cancelled, or it's sending and the cancel is refused, never part of it sent

### Requirement: The feed loads without waiting for imports
Before its first load and on each refresh, the feed SHALL send only the athlete's queued writes that aren't held behind an import. It SHALL NOT wait for an import's writes before showing the timeline.

#### Scenario: Open the app mid-import
- **WHEN** the athlete opens the feed with 5,000 import writes queued
- **THEN** the timeline loads without waiting for them to upload
