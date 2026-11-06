# Spec Delta

## Purpose

Lets an athlete download, as one archive, everything Activity Tracker can read of theirs: their records in their repo and in every athlete space, the blobs those records name, and a ready-to-open file per activity, so they keep a copy before leaving or for their own records.

## ADDED Requirements

### Requirement: An athlete exports only their own data
A data export SHALL hold only records in the signed-in athlete's own repo and athlete spaces, and blobs those records name. It SHALL NOT hold other athletes' records, data the AppView derives (segment efforts, leaderboard places, group pairs, segment statuses), or records of collections outside `app.activitytracker`.

#### Scenario: Comment on a follower's private activity
- **WHEN** alice exports her data after commenting on bob's Followers activity
- **THEN** the archive holds her comment record from her interactions space, and not bob's activity

#### Scenario: Segment efforts
- **WHEN** alice exports her data after riding three segments
- **THEN** the archive holds her activities and segment tracks, and no efforts or leaderboard places

#### Scenario: Bluesky records in the same repo
- **WHEN** alice's repo also holds `app.bsky.feed.post` and `app.bsky.graph.block` records
- **THEN** the archive holds none of them

### Requirement: The export covers the repo and every athlete space
The export SHALL read every `app.activitytracker` record collection from the athlete's repo, and every collection the athlete space type declares from each of their followers, mutuals, self and interactions spaces. A space that doesn't exist SHALL count as empty.

#### Scenario: Only me activity
- **WHEN** alice exports her data with an Only me run that has a full track and an original FIT file
- **THEN** the archive holds the run's activity, streams, track, source and devices records from her self space, and their blobs

#### Scenario: Private settings
- **WHEN** alice exports her data with privacy zones, Mutuals profile details, activity defaults and segment matching on
- **THEN** the archive holds her `actor.privacyZones`, her Mutuals `actor.privateDetails`, her `actor.activityDefaults` and her `actor.segmentPrefs`

#### Scenario: Interactions
- **WHEN** alice exports her data having liked, commented, blocked, muted and voted on segment names
- **THEN** the archive holds those like, comment, block, mute and vote records from her interactions space

#### Scenario: No mutuals space
- **WHEN** alice has never stored anything at Mutuals and exports her data
- **THEN** the export completes, with nothing under mutuals

### Requirement: Records are kept as stored
Each record SHALL be written as the JSON the PDS returned for it, under where it was read from (the repo or the named space), its collection and its rkey. Each blob a record names SHALL be written once, byte for byte, under where it was read from and its CID.

#### Scenario: Activity split across tiers
- **WHEN** alice exports a Public ride whose heart rate is Followers and whose map is Only me
- **THEN** the archive holds its activity record under repo, its details and streams under followers, and its details, streams and track under self, each as stored

#### Scenario: Blob named by two records
- **WHEN** two of alice's records name the same blob
- **THEN** the archive holds that blob once

### Requirement: Each activity gets a ready-to-open file
For each activity, the archive SHALL hold its original file, byte for byte under its original name, when it has one, else a FIT file rebuilt from the stored record, streams and full track as an activity export builds it, else no file. Each file's name SHALL start with the activity's start date, and SHALL be unique in the archive.

#### Scenario: Imported ride
- **WHEN** alice exports her data with a ride imported from `Morning_Ride.fit`
- **THEN** the archive holds that file byte for byte, named with the ride's date and its original name, and no rebuilt FIT for it

#### Scenario: Recorded run
- **WHEN** alice exports her data with a run she recorded in the app
- **THEN** the archive holds a FIT file of the run with its full track, untrimmed by her privacy zones

#### Scenario: Manual entry
- **WHEN** alice exports her data with a manual entry that has no track and no streams
- **THEN** the archive holds its records and no file for it

#### Scenario: Same title and date
- **WHEN** alice has two activities titled "Lunch run" on the same day
- **THEN** the archive holds two files with different names

### Requirement: The export doesn't need the AppView
The export SHALL read only from the athlete's PDS. It SHALL complete when the AppView can't be reached, and a rebuilt FIT SHALL then lack only what the PDS doesn't store.

#### Scenario: AppView down
- **WHEN** alice exports her data while the AppView doesn't answer
- **THEN** the export completes with all her records, blobs and activity files

### Requirement: The archive describes itself
The archive SHALL hold a manifest giving the account's DID and handle, its PDS, when the export ran, how many records, blobs and activity files it holds, which activity file belongs to which activity, and each item it couldn't read with why. It SHALL also hold a README saying what the archive holds and what it leaves out, and that the PDS can export the account itself.

#### Scenario: Manifest
- **WHEN** alice opens her export's manifest
- **THEN** it names her DID, `alice.test`, her PDS, the export time and the counts, and maps each activity's URI to its file

#### Scenario: README
- **WHEN** alice opens her export's README
- **THEN** it says segment efforts, leaderboards and other apps' records aren't included, and that her PDS can export her account

### Requirement: Unsent changes are reported, not waited for
An export SHALL include what the PDS holds when it reads each item. When changes on this device are still waiting to upload as the export starts, the Account page and the manifest SHALL say how many aren't included.

#### Scenario: Activity still in the outbox
- **WHEN** alice starts an export with 2 changes waiting to upload
- **THEN** the export runs, and the Account page and manifest say 2 changes on this device aren't included

### Requirement: A failed item doesn't fail the export
An item that can't be read after retries SHALL be left out and listed in the manifest with why, and the export SHALL go on. The Account page SHALL say how many items were left out. An export SHALL fail only when it can't read the athlete's lists of records at all or can't write the archive.

#### Scenario: Unreadable blob
- **WHEN** one of alice's stream blobs can't be fetched during her export
- **THEN** the export finishes, the manifest lists that blob as unreadable, and the Account page says 1 item was left out

#### Scenario: Offline from the start
- **WHEN** alice starts an export with no connection
- **THEN** the export says it couldn't reach her PDS, and she can try again

### Requirement: A PDS without Spaces exports the repo only
When the session can't use spaces (the PDS lacks Spaces, or the sign-in predates the space scope), the export SHALL hold what the repo holds and SHALL say, on the Account page and in the manifest, that private data couldn't be read.

#### Scenario: Old sign-in
- **WHEN** bob, whose sign-in has no space scope, exports his data
- **THEN** the archive holds his repo's records, and the Account page says his private data wasn't included and that signing in again would include it

### Requirement: The export keeps running away from the page
An export SHALL keep running when the athlete leaves the Account page. On Android it SHALL run as a foreground job with a progress notification, and on iOS in a background task. On desktop and the web it SHALL run while the app is open, and the web SHALL say the tab must stay open.

#### Scenario: Leaving the page
- **WHEN** alice starts an export and goes to her feed
- **THEN** the export goes on, and the Account page shows its progress when she returns

#### Scenario: Android notification
- **WHEN** alice's export is running on Android
- **THEN** a notification shows its progress

### Requirement: An interrupted export resumes
An export interrupted by the app's process ending SHALL resume, the next time the platform runs it or the account signs in on this device, from what it had already written, without reading those items again.

#### Scenario: Process killed
- **WHEN** alice's export is half done and Android ends the app's process
- **THEN** the export resumes later and reads only the items it hadn't written

### Requirement: One export per account at a time
An account SHALL have at most one export at a time. While one runs or waits to be saved, the Account page SHALL offer to cancel it instead of starting another. Cancelling SHALL stop it and delete what it wrote.

#### Scenario: Starting twice
- **WHEN** alice's export is running and she opens the Account page
- **THEN** it shows the export's progress and Cancel, not a second Export

#### Scenario: Cancel
- **WHEN** alice cancels her running export
- **THEN** it stops, its files are deleted, and the Account page offers Export again

### Requirement: The athlete is told when the export is ready
When an export finishes, the Account page SHALL offer to save the archive. On Android and iOS a notification SHALL also say it's ready, and opening it SHALL open the Account page.

#### Scenario: Ready on a phone
- **WHEN** alice's export finishes while the app is in the background on iOS
- **THEN** a notification says her export is ready, and tapping it opens the Account page with Save

### Requirement: The archive is handed to the platform
Saving SHALL open a save dialog on Android and desktop, the share sheet on iOS, and a download on the web, without holding the whole archive in memory. The file SHALL be a zip named after the app, the handle and the export's date. Dismissing the dialog SHALL keep the archive to save later.

#### Scenario: Save on desktop
- **WHEN** alice saves her finished export on desktop
- **THEN** a save dialog opens with `activity-tracker-alice.test-2026-11-05.zip` filled in, and the zip is written where she chooses

#### Scenario: Large archive on the web
- **WHEN** alice saves a 2 GB export on the web
- **THEN** the browser downloads the zip

#### Scenario: Dismissing the dialog
- **WHEN** alice dismisses the save dialog
- **THEN** the Account page still offers Save

### Requirement: The app doesn't keep the archive
The app SHALL delete its copy of an export once the athlete has saved it, when the export is 7 days old, when it's cancelled, and when the account signs out. On iOS, where the share sheet doesn't say whether the zip was kept, the app SHALL ask before deleting its copy, and keep it when the athlete says they didn't keep the zip.

#### Scenario: Saved
- **WHEN** alice saves her finished export
- **THEN** the app deletes its copy, and the Account page offers Export again

#### Scenario: Never saved
- **WHEN** alice's finished export is 7 days old and unsaved
- **THEN** the app deletes it, and the Account page offers Export again

#### Scenario: Shared on iOS
- **WHEN** alice shares her finished export from the share sheet on iOS
- **THEN** the app asks whether she kept it, deletes its copy if she did, and otherwise keeps it to save later

### Requirement: The export needs room on the device
Before starting, and as it goes, the export SHALL check that the device has room for it. When it runs out of room it SHALL stop, keep what it wrote, and say how much space it needs, so it can resume once there's room.

#### Scenario: Low storage
- **WHEN** alice's phone runs out of storage partway through her export
- **THEN** the export pauses, the Account page says it needs more free space, and it resumes when she retries with room
