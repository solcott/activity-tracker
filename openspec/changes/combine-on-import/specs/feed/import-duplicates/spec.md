# Spec Delta

## Purpose

Finds the athlete's logged activities that an import repeats (another device's recording of the same activity, for any sport) and lets the athlete ignore the import, import it anyway, or combine it with the logged activity. Also lets them add a recording to an activity from its page.

## ADDED Requirements

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
A file holding several activities SHALL have each one checked against the ones before it in the file. Two that overlap as above SHALL be flagged as the same activity recorded twice.

#### Scenario: Two computers in one logbook
- **WHEN** an athlete imports a Subsurface logbook holding each dive from both their computers
- **THEN** each second copy is flagged as a duplicate of the first

### Requirement: The athlete chooses what to do with each duplicate
For each flagged import, the athlete SHALL choose one of three actions:
- **Ignore:** don't save it.
- **Import anyway:** save it as an activity of its own.
- **Combine:** save it and combine it with the activity it repeats.

Combine SHALL be the default. An import isn't saved until the search of the log has finished.

#### Scenario: Default
- **WHEN** an import is flagged as a duplicate
- **THEN** Combine is selected, and Ignore and Import anyway can be chosen instead

#### Scenario: Ignore
- **WHEN** an athlete ignores a flagged ride and saves the rest of the import
- **THEN** the ride is not saved, and their log is unchanged

### Requirement: Combine on import keeps both recordings
Combining an import SHALL save it as its own activity. If the logged activity is already a member of a combined activity, the import SHALL be added to that combined activity. Otherwise the import SHALL be combined with the logged activity, as in `feed/combined-activities`, starting from the logged activity's title, description and visibility.

#### Scenario: Ride from two devices
- **WHEN** an athlete imports their Edge 840 ride and combines it with their watch's recording
- **THEN** their feed shows one combined ride whose power comes from the Edge, and both recordings open on their own

#### Scenario: Third recording
- **WHEN** an athlete imports a third recording of a ride that is already combined
- **THEN** the combined ride lists three members

### Requirement: Combine within a file
Combining a duplicate that an earlier activity in the same file repeats SHALL save both and combine them into one combined activity, starting from the earlier one's title. This SHALL apply in turn to every copy of that activity in the file.

#### Scenario: Logbook copies
- **WHEN** an athlete imports a logbook holding a dive from both computers and keeps Combine
- **THEN** the dive is saved once as a combined dive with two members

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
