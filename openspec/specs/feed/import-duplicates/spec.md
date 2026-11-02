# feed/import-duplicates Specification

## Purpose

Finds the athlete's logged activities that an import repeats (another device's recording of the same activity, for any sport) and lets the athlete ignore the import, import it anyway, or combine it with the logged activity. Also lets them add a recording to an activity from its page.

## Requirements

### Requirement: Imports are checked for duplicates in every sport
Before an import is saved, the import screen SHALL search the athlete's logged activities, of any sport, for ones whose time overlaps the import's by at least half of the shorter one. It SHALL flag the import as a duplicate of the first such activity found.

#### Scenario: Bike computer after the watch
- **WHEN** an athlete who already has their watch's 9:00 to 11:00 ride imports their Edge 840's 8:58 to 11:01 FIT ride
- **THEN** the import screen says the ride is already in their log, naming the watch ride's title, start and device

#### Scenario: Not enough overlap
- **WHEN** an athlete imports a run from 12:00 to 13:00 and their log holds a ride from 11:00 to 12:20
- **THEN** the run isn't flagged

### Requirement: Zone-shifted imports still match
An import from a file without a UTC offset SHALL also match a logged activity of the same length (within 10%) that starts a whole number of hours away, up to 14, give or take 3 minutes. The notice SHALL say how many hours apart they are.

#### Scenario: Logbook read in the wrong zone
- **WHEN** an athlete imports a UDDF dive whose time is two hours off a dive they logged from FIT
- **THEN** the dive is flagged as a likely duplicate two hours apart

### Requirement: Duplicates in one file are found
An import of several activities, from one file or from several files and archives picked together, SHALL have each one checked against the ones before it in the batch, oldest first. Two that overlap as above SHALL be flagged as the same activity recorded twice.

#### Scenario: Two computers in one logbook
- **WHEN** an athlete imports a Subsurface logbook holding each dive from both their computers
- **THEN** each second copy is flagged as a duplicate of the first

#### Scenario: Watch and bike computer picked together
- **WHEN** an athlete picks their watch's FIT ride from 9:00 to 11:00 and their Edge 840's FIT ride from 8:58 to 11:01 in one pick
- **THEN** the later-starting copy is flagged as a duplicate of the earlier one, naming its file

### Requirement: The athlete chooses what to do with each duplicate
For each flagged import, the athlete SHALL choose one of three actions:
- **Ignore:** don't save it.
- **Import anyway:** save it as an activity of its own.
- **Combine:** save it and combine it with the activity it repeats.

The default action SHALL depend on the duplicate:
- **An exact copy:** Ignore.
- **Otherwise, in a batch, a duplicate whose sport differs from the activity it repeats:** Import anyway.
- **Otherwise:** Combine.

An import isn't saved until the search of the log has finished.

#### Scenario: Default
- **WHEN** an import is flagged as a duplicate of a logged activity of the same sport, and isn't an exact copy
- **THEN** Combine is selected, and Ignore and Import anyway can be chosen instead

#### Scenario: Ignore
- **WHEN** an athlete ignores a flagged ride and saves the rest of the import
- **THEN** the ride is not saved, and their log is unchanged

#### Scenario: Walk during a ski day in a batch
- **WHEN** a batch holds a ski day from 9:00 to 15:00 and a walk from 12:00 to 12:30 recorded on a phone
- **THEN** the walk is flagged as overlapping the ski day, with Import anyway selected, and saving with the defaults keeps them as two activities

#### Scenario: Different sport in a single import
- **WHEN** an athlete imports one run file that overlaps a walk in their log
- **THEN** the run is flagged with Combine selected, as before

### Requirement: Exact copies are recognized
A duplicate SHALL be an exact copy of the activity it repeats when all of these hold:
- **Start:** the two start within one second of each other.
- **Length:** their elapsed times differ by at most one second.
- **Distance:** their distances differ by at most 0.5%, or neither has one.
- **Device:** they name the same recording device, or either names none.

This SHALL apply to both logged activities and earlier activities in the same batch.

#### Scenario: Same upload in two exports
- **WHEN** an athlete picks their Strava and Garmin Connect exports together, and both hold the FIT file their watch recorded on Saturday's ride
- **THEN** the second copy is flagged as an exact copy of the first, with Ignore selected, and saving with the defaults saves the ride once

#### Scenario: Re-importing a logged activity
- **WHEN** an athlete imports an export holding a ride they already imported from the same file
- **THEN** the ride is flagged as an exact copy of the logged ride, with Ignore selected

#### Scenario: Two devices are not copies
- **WHEN** an athlete imports their Edge 840's ride from 8:58 to 11:01 and their log holds their watch's 9:00 to 11:00 ride
- **THEN** the ride is flagged as a duplicate but not an exact copy, with Combine selected

### Requirement: Exact copies are flagged as copies
An exact copy SHALL be flagged as one, apart from a recording of the same activity on another device. The athlete SHALL still be able to choose Import anyway or Combine for it.

#### Scenario: Keep a copy anyway
- **WHEN** an athlete chooses Import anyway for a ride flagged as an exact copy
- **THEN** the ride is saved as an activity of its own

### Requirement: Combine on import keeps both recordings
Combining an import SHALL save it as its own activity. If the logged activity is already a member of a combined activity, the import SHALL be added to that combined activity. Otherwise the import SHALL be combined with the logged activity, as in `feed/combined-activities`, starting from the logged activity's title, description and visibility.

#### Scenario: Ride from two devices
- **WHEN** an athlete imports their Edge 840 ride and combines it with their watch's recording
- **THEN** their feed shows one combined ride whose power comes from the Edge, and both recordings open on their own

#### Scenario: Third recording
- **WHEN** an athlete imports a third recording of a ride that is already combined
- **THEN** the combined ride lists three members

### Requirement: Combine within a file
Combining a duplicate that an earlier activity in the same batch repeats SHALL save both and combine them into one combined activity, starting from the earlier one's title. This SHALL apply in turn to every copy of that activity in the batch, whichever file each was read from.

#### Scenario: Logbook copies
- **WHEN** an athlete imports a logbook holding a dive from both computers and keeps Combine
- **THEN** the dive is saved once as a combined dive with two members

#### Scenario: Copies from two files
- **WHEN** an athlete picks a watch FIT and a bike computer FIT of the same ride and keeps Combine
- **THEN** the ride is saved as a combined ride with two members, each keeping its own file as its source

### Requirement: Add a recording from the activity page
On their own activity's page, an athlete SHALL be able to add a recording from a file. They pick a file, preview it, and on saving it is combined with that activity, without a duplicate search. A file whose activity starts more than 12 hours from the activity's start SHALL be refused, with the reason given.

#### Scenario: Phone recording after the watch died
- **WHEN** an athlete opens their watch ride that stopped at 10:40 and adds their phone's GPX from 10:38 to 12:00
- **THEN** a combined ride from 9:00 to 12:00 replaces the watch ride in their feed

#### Scenario: Wrong day
- **WHEN** an athlete adds a file from three days later to an activity
- **THEN** the app says the file starts too far from this activity, and saves nothing

### Requirement: Existing groups become combined activities
After sign-in, the athlete's client SHALL turn each group of theirs (a primary with `recordingOf` recordings) into a combined activity. The primary and its recordings become members, starting from the primary's title, description and visibility, and each recording's `recordingOf` is cleared. This SHALL run once per group, and a group that fails SHALL be retried at the next sign-in. Activities merged before this change SHALL stay as they are.

#### Scenario: Backup dive computer
- **WHEN** an athlete who grouped a backup computer's dive under their main computer's dive signs in
- **THEN** their feed shows one combined dive with both as members, and splitting it leaves two dives without `recordingOf`

### Requirement: New imports never group
Clients SHALL NOT write `recordingOf` on any activity, and SHALL NOT merge an import's data into a logged activity.

#### Scenario: No merge action
- **WHEN** an athlete views a flagged import's actions
- **THEN** they are Ignore, Import anyway and Combine only

### Requirement: The log search covers the whole batch
The search of the athlete's log SHALL cover every activity in the batch, from 14 hours before the earliest start to 14 hours after the latest end, however many years that spans and however many activities the log holds in it. The screen SHALL say if the search couldn't finish, and saving SHALL still be possible.

#### Scenario: Re-importing an export
- **WHEN** an athlete who imported their Strava export last month picks it again
- **THEN** every activity is flagged as already in their log, back to the export's oldest

#### Scenario: Search fails partway
- **WHEN** the AppView can't be reached while searching a ten-year batch
- **THEN** the screen says duplicates couldn't be fully checked, and the athlete can still save

#### Scenario: Single file when the search fails
- **WHEN** the athlete picks one file and their log can't be read
- **THEN** the preview says duplicates couldn't be fully checked, and the athlete can still save

### Requirement: Activities on the device are always checked
Every import SHALL be checked against the athlete's activities the device knows of, whether or not their log can be read: those waiting to upload, those in the cached feed, and those the device saved in the last 7 days. When the log can't be read, the screen SHALL say only these were checked.

#### Scenario: PDS down after an import
- **WHEN** the athlete's PDS can't be reached and they pick again files they imported on this device yesterday
- **THEN** those activities are flagged as already in their log, and the screen says only activities on this device were checked

#### Scenario: Waiting activities
- **WHEN** an import is still uploading and the athlete picks one of its files again while offline
- **THEN** that activity is flagged as already in their log

### Requirement: The log search can be retried
When the search of the athlete's log couldn't finish, the screen SHALL offer to search again, keeping the athlete's choices for activities whose matches don't change.

#### Scenario: Retry after the PDS comes back
- **WHEN** the search failed, the PDS comes back, and the athlete taps Retry
- **THEN** the whole batch is searched again, and the notice goes once it completes
