## MODIFIED Requirements

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

## ADDED Requirements

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
