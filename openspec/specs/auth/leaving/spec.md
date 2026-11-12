# auth/leaving Specification

## Purpose

Lets an athlete remove everything Activity Tracker keeps in their atproto account while keeping the account itself, and keeps their other signed-in devices from putting it back.

## Requirements

### Requirement: Leave lives on the Account page
The Account page SHALL offer Leave Activity Tracker, set apart from Sign out and from the pointer to the PDS for deleting the account. It SHALL say that leaving removes the app's data but keeps the atproto account.

#### Scenario: Finding Leave
- **WHEN** alice opens the Account page
- **THEN** she sees Leave Activity Tracker, which says her `alice.test` account itself stays

### Requirement: Leaving says what goes and what stays
Before confirming, the leave screen SHALL list what will be deleted and what stays. Deleted: every activity of every visibility with its details, streams and media, gear, profile and private details, privacy zones, follows and approvals, likes, comments, blocks and mutes made in the app, name proposals and votes, stars, segment tracks and the app's settings. Stays: the atproto account, its handle, and its data from other apps, such as Bluesky posts and Bluesky blocks.

#### Scenario: Summary shown
- **WHEN** alice chooses Leave Activity Tracker
- **THEN** the screen lists her activities, profile details, gear, follows and comments among what will be deleted, and her account, handle and Bluesky data among what stays

### Requirement: Leaving says which shared places stay
The leave screen SHALL say how many of the athlete's segments and dive sites are `active` and so stay for others: republished by Activity Tracker without the athlete's name, keeping their names. It SHALL say that the athlete's other segments and sites are deleted. It SHALL offer no way to opt out.

#### Scenario: Segments others ride
- **WHEN** alice, who created 3 active segments, 2 proposed ones and 1 active dive site, opens the leave screen
- **THEN** it says 3 segments and 1 dive site others use will stay without her name, and the other 2 segments will be deleted

### Requirement: Leaving offers an export first
With no export, the leave screen SHALL offer to start one, as the Account page's Export your data does, and SHALL let the athlete go on without exporting. While an export runs, leaving SHALL NOT start: the screen SHALL say to wait for it or cancel it. When an export waits to be saved, the screen SHALL offer Save first and say that leaving deletes it.

#### Scenario: Exporting first
- **WHEN** alice chooses to export from the leave screen
- **THEN** the export starts, and she can come back to the leave screen afterwards

#### Scenario: Export running
- **WHEN** alice opens the leave screen while her export is running
- **THEN** leaving is unavailable, and the screen says to wait for the export or cancel it

#### Scenario: Export not yet saved
- **WHEN** alice's export is ready and unsaved when she opens the leave screen
- **THEN** the screen offers Save, and says leaving deletes the export

### Requirement: Leaving says what this device loses
When this device has changes that haven't uploaded (waiting, uploading or refused), an import saving, a recording in progress, or an export waiting to be saved for the account, the leave screen SHALL say how many changes and which of these will be discarded, never uploaded or kept.

#### Scenario: Uploads waiting
- **WHEN** bob has 2 activities waiting to upload and opens the leave screen
- **THEN** it says the 2 changes will be discarded and never uploaded

### Requirement: Leaving is confirmed by typing the handle
Leaving SHALL start only after the athlete types their handle and confirms. Until the handle matches, the confirm action SHALL be disabled, and cancelling SHALL change nothing.

#### Scenario: Wrong handle
- **WHEN** alice types `alice.tset`
- **THEN** the leave action stays disabled

#### Scenario: Cancelled
- **WHEN** alice cancels the leave screen
- **THEN** nothing is deleted or discarded and she stays signed in

### Requirement: Leaving needs a current grant and a reachable PDS
Leaving SHALL refuse to start when the session's grant lacks the permission to delete spaces, and SHALL point to Sign in again. It SHALL also refuse to start when the athlete's PDS can't be reached, so nothing is discarded before the leave can run.

#### Scenario: Grant from before space deletion
- **WHEN** bob, whose sign-in predates the space-deletion permission, chooses Leave Activity Tracker
- **THEN** he is told he must sign in again first, and is offered Sign in again

#### Scenario: PDS unreachable
- **WHEN** alice confirms leaving and her PDS can't be reached
- **THEN** she is told her PDS couldn't be reached, and nothing is discarded

### Requirement: Leaving removes the app's data in order
Once confirmed, leaving SHALL first discard this device's queued writes for the account and stop its import and recording. It SHALL then delete each of the athlete's spaces that exists (`self` first, then the tiers and `interactions`), then delete every record in the repo in an `app.activitytracker.*` collection, then clear the account's data on this device and sign out.

#### Scenario: Full leave
- **WHEN** alice confirms leaving
- **THEN** her athlete spaces no longer exist, her repo holds no `app.activitytracker.*` record, her Bluesky records are untouched, and she is signed out

#### Scenario: No space ever created
- **WHEN** an athlete who only ever wrote public activities leaves
- **THEN** leaving finds no spaces to delete and goes on to the repo

### Requirement: Leaving deletes in paced batches
Leaving SHALL delete repo records in batches, waiting when the PDS asks it to slow down as the outbox does, and SHALL show progress until it's done.

#### Scenario: Rate limited
- **WHEN** the PDS answers a batch of deletes with a rate limit partway through alice's 2,000 records
- **THEN** leaving waits as asked, then carries on, and the screen shows it still working

### Requirement: An interrupted leave resumes
Leaving SHALL be recorded on the device before anything is discarded. Until it finishes, starting the app or signing in again as that account SHALL resume the leave instead of opening the app. Resuming SHALL delete whatever remains, so repeating a step is harmless. A refused request that isn't a rate limit SHALL stop the leave with the error and a Retry, and SHALL never resume normal writes.

#### Scenario: App killed mid-leave
- **WHEN** the app is closed after alice's spaces were deleted and before her repo records were
- **THEN** the next start shows the leave in progress and deletes the remaining repo records

#### Scenario: Refused delete
- **WHEN** the PDS refuses a delete with an error other than a rate limit
- **THEN** the leave stops showing that error and offers Retry, and nothing is written for the account

### Requirement: After leaving, the login screen says so
Once leaving finishes, the app SHALL show the login screen with a note that the athlete has left Activity Tracker and that their atproto account is unchanged. Signing in again SHALL start the account afresh.

#### Scenario: Left
- **WHEN** alice's leave finishes
- **THEN** the login screen says she has left Activity Tracker and her `alice.test` account is unchanged

### Requirement: A device never recreates a space it has seen
A device that has used one of the account's spaces SHALL remember it. When a write needs a space this device has used and it no longer exists, the device SHALL NOT create it again, except after the athlete chooses Rejoin.

#### Scenario: Space gone under a queued write
- **WHEN** alice's desktop, which has written to her Followers space before, drains a queued write and finds the space gone
- **THEN** the desktop doesn't create the space

### Requirement: Another device detects a leave
A device that has used the account's `self` space SHALL check that it still exists when the session starts and before draining the outbox. When it's gone, the device SHALL stop draining and send nothing more for the account.

#### Scenario: Desktop after a leave on the phone
- **WHEN** alice left on her phone, and her desktop, still signed in, starts
- **THEN** the desktop finds `self` gone and uploads nothing

#### Scenario: New device
- **WHEN** alice signs in on a device that has never used her spaces
- **THEN** the device doesn't treat her account as having left, and creates spaces as it needs them

### Requirement: The other device signs out or rejoins
On detecting a leave with nothing pending, the device SHALL sign out and show the left note. With pending work it SHALL say how many changes are pending and offer Discard and sign out, or Rejoin. Rejoin SHALL recreate the spaces it needs and upload the pending work.

#### Scenario: Nothing pending
- **WHEN** alice's desktop detects the leave with nothing queued
- **THEN** it signs out and the login screen says she has left Activity Tracker

#### Scenario: Pending ride
- **WHEN** bob's tablet detects the leave with 1 recorded ride waiting
- **THEN** it says 1 change is pending, and offers Discard and sign out, or Rejoin

#### Scenario: Rejoin
- **WHEN** bob chooses Rejoin
- **THEN** the tablet recreates the spaces the ride needs and uploads it

### Requirement: The left-elsewhere choice is native on iOS
On iOS the screen shown after leaving on another device, with work still on this one, SHALL be drawn natively in place of the app: its
title, what this device still holds, the choice, a failure, and Rejoin and Discard and sign out, both off while either runs, with
progress while it is still reading what the device holds.

#### Scenario: Pending ride on an iPhone
- **WHEN** bob's iPhone detects the leave with 1 recorded ride waiting
- **THEN** the native screen says 1 change is pending and offers Rejoin and Discard and sign out

#### Scenario: Rejoining
- **WHEN** bob taps Rejoin on the native screen
- **THEN** both buttons are off and progress shows until the app comes back, or the failure shows with both buttons on again
