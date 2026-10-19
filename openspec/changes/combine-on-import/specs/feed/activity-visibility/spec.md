# Spec Delta

## MODIFIED Requirements

### Requirement: Recordings share their primary's visibility
An activity that is another recording of a primary (`recordingOf`, a legacy group not yet migrated to a combined activity) SHALL have the primary's visibility, and changing a primary's visibility SHALL change its recordings' to match. A `recordingOf` link between activities of different visibilities SHALL be ignored. Clients SHALL NOT create new `recordingOf` links.

#### Scenario: Legacy group follows its primary
- **WHEN** an athlete narrows a not-yet-migrated primary with one recording from Public to Followers
- **THEN** the recording becomes Followers

#### Scenario: Group into a Followers activity
- **WHEN** a not-yet-migrated legacy group links a recording to the athlete's Followers activity
- **THEN** the recording becomes Followers

#### Scenario: Mismatched link
- **WHEN** a Public activity's `recordingOf` names the athlete's Only-me activity
- **THEN** feeds treat the Public activity as an activity in its own right, and show nothing about the Only-me one
