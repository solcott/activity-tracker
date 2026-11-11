# Spec Delta

## ADDED Requirements

### Requirement: The upload queue is native on iOS
On iOS the upload queue SHALL be drawn with the system's own list and navigation bar, and SHALL show what the shared queue shows, in the same order and words: why the last action failed, the pauses, the time left, the item uploading now, the athlete's own items, each import's group, the finished items, the other refused changes, and the empty queue's message. Its loading SHALL follow `ui/content-loading`.

#### Scenario: Time left
- **WHEN** an import's waiting activities are estimated to take 26 hours
- **THEN** the native queue reads "About 1 day left"

#### Scenario: Nothing to upload
- **WHEN** the queue holds nothing
- **THEN** the native queue reads "Nothing to upload. Everything you saved has uploaded."

### Requirement: Clear and the pauses on iOS
On iOS, Clear SHALL be in the bar once the queue has loaded, unavailable while nothing is finished or finished items are being cleared. The queue SHALL offer Resume while the athlete paused imports, and Pause imports while an import has activities to upload, each unavailable while the pause is being changed. It SHALL say until what local time the server paused uploads, with "tomorrow at" when the pause ends after midnight.

#### Scenario: Both pauses
- **WHEN** the athlete paused imports and the server paused uploads until 3:40 PM today
- **THEN** the native queue reads "Imports paused by you" with Resume, and "Uploads paused by the server until 3:40 PM"

#### Scenario: Nothing finished
- **WHEN** the queue holds only waiting items
- **THEN** Clear can't be tapped

### Requirement: Items on iOS
On iOS each item SHALL show its name (its title, or its sport and day, or "Updating" it for an edit) and its state. It SHALL offer Send again where it is refused and Cancel where it can be cancelled, each unavailable while that item is busy and read to VoiceOver with the item's name. An uploaded item SHALL open its activity when tapped.

#### Scenario: An item uploading
- **WHEN** "Evening Run" has had 2 of its 5 writes sent
- **THEN** it is listed under Uploading now as "Uploading, 2 of 5", with no Cancel

#### Scenario: Opening an uploaded activity
- **WHEN** the athlete taps the uploaded "Hill Repeats"
- **THEN** its activity opens, as from the shared queue

### Requirement: Imports on iOS
On iOS each import SHALL show its source, when it was saved, that it is saving or stopped, how many of its activities are uploaded of those saved, and how many are refused and skipped, with Cancel rest while anything of it can be cancelled. It SHALL list its first 20 waiting activities until Show all, then its finished ones.

#### Scenario: Import part way
- **WHEN** a Strava import has uploaded 412 of 1,362 activities, with 1 refused, 3 skipped and 26 waiting
- **THEN** its group reads "Strava export", "412 of 1,362 uploaded" and "1 refused · 3 skipped", lists 20 of its waiting activities and offers "Show all (26)"

### Requirement: Cancelling asks in the system's alert on iOS
On iOS, Cancel and Cancel rest SHALL ask first in the system's alert, with the shared queue's words: the cancel's question with Cancel upload and Keep, and the cancel-rest question with its message for an import still saving or not. Confirming SHALL cancel; Keep, or closing the alert any other way, SHALL dismiss the question and cancel nothing.

#### Scenario: Cancel rest of an import still saving
- **WHEN** the athlete taps Cancel rest on an import that is still saving
- **THEN** the alert asks "Cancel the rest of this import?" and says activities not saved yet won't be saved

#### Scenario: Keep
- **WHEN** the athlete taps Keep in the cancel alert
- **THEN** the alert closes and the item stays waiting
