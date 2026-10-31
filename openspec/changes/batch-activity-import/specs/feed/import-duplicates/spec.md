# Spec Delta

## MODIFIED Requirements

### Requirement: Duplicates in one file are found
An import of several activities, from one file or from several files and archives picked together, SHALL have each one checked against the ones before it in the batch, oldest first. Two that overlap as above SHALL be flagged as the same activity recorded twice.

#### Scenario: Two computers in one logbook
- **WHEN** an athlete imports a Subsurface logbook holding each dive from both their computers
- **THEN** each second copy is flagged as a duplicate of the first

#### Scenario: Watch and bike computer picked together
- **WHEN** an athlete picks their watch's FIT ride from 9:00 to 11:00 and their Edge 840's FIT ride from 8:58 to 11:01 in one pick
- **THEN** the later-starting copy is flagged as a duplicate of the earlier one, naming its file

### Requirement: Combine within a file
Combining a duplicate that an earlier activity in the same batch repeats SHALL save both and combine them into one combined activity, starting from the earlier one's title. This SHALL apply in turn to every copy of that activity in the batch, whichever file each was read from.

#### Scenario: Logbook copies
- **WHEN** an athlete imports a logbook holding a dive from both computers and keeps Combine
- **THEN** the dive is saved once as a combined dive with two members

#### Scenario: Copies from two files
- **WHEN** an athlete picks a watch FIT and a bike computer FIT of the same ride and keeps Combine
- **THEN** the ride is saved as a combined ride with two members, each keeping its own file as its source

## ADDED Requirements

### Requirement: The log search covers the whole batch
The search of the athlete's log SHALL cover every activity in the batch, from 14 hours before the earliest start to 14 hours after the latest end, however many years that spans and however many activities the log holds in it. The screen SHALL say if the search couldn't finish, and saving SHALL still be possible.

#### Scenario: Re-importing an export
- **WHEN** an athlete who imported their Strava export last month picks it again
- **THEN** every activity is flagged as already in their log, back to the export's oldest

#### Scenario: Search fails partway
- **WHEN** the AppView can't be reached while searching a ten-year batch
- **THEN** the screen says duplicates couldn't be fully checked, and the athlete can still save
