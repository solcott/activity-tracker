# Spec Delta

## MODIFIED Requirements

### Requirement: Saving a batch
Saving SHALL publish every selected activity, oldest first, and handle each duplicate as the athlete chose. Every activity the save publishes, combined ones within the batch included, SHALL name one batch identifier, new for each save. A copy combined into an activity already logged SHALL NOT give that activity the identifier. An activity whose data can't be read again at save SHALL be skipped and reported, and the rest SHALL still be saved. After saving, the screen SHALL say how many were saved and skipped.

#### Scenario: Batch saved
- **WHEN** an athlete saves a batch of 40 selected activities
- **THEN** 40 activities are queued for their log and the screen says 40 were saved

#### Scenario: Source vanished before save
- **WHEN** a picked file can no longer be read when the athlete saves
- **THEN** its activities are reported as skipped, and the others are saved

#### Scenario: Activities share the batch identifier
- **WHEN** an athlete saves a batch with 38 activities on their own and two copies combined with each other
- **THEN** all 38 and the combined activity name the same batch identifier, and a later batch names a different one

#### Scenario: Combined into a logged activity
- **WHEN** a batch combines a copy into a ride the athlete logged before
- **THEN** that ride doesn't name the batch's identifier
