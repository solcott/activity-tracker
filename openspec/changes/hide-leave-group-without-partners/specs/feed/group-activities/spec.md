# Spec Delta

## MODIFIED Requirements

### Requirement: Leaving the group on an activity
The owner of a paired activity SHALL be able to leave the group on that activity, and later rejoin. Leaving SHALL remove the activity from every pair, so no page lists it as a partner, and SHALL keep it from being paired until the owner rejoins. Rejoining SHALL let it be paired again by the same rules. Leaving SHALL NOT affect the owner's other activities.

#### Scenario: Leave
- **WHEN** Bob leaves the group on a ride paired with Alice's
- **THEN** Alice's activity no longer lists Bob, and Bob's ride lists no one

#### Scenario: Rejoin
- **WHEN** Bob rejoins the group on that ride
- **THEN** once the AppView matches it again, Alice's and Bob's rides list each other

#### Scenario: Other activities
- **WHEN** Bob leaves the group on one ride
- **THEN** his other rides with Alice stay paired

### Requirement: The owner's page offers leave and rejoin
On the owner's own page of an activity whose segment track is stored, while the owner's grouping choice isn't Off, the page SHALL offer Leave group when the activity hasn't left its group and its view lists at least one partner, and Rejoin group when it has left, whether or not it lists a partner. The choice SHALL be queued like the owner's other writes and shown at once. The page SHALL NOT offer either to other viewers, or while the track's own write is still queued.

#### Scenario: Leave from the page
- **WHEN** Bob taps Leave group on his paired ride
- **THEN** the page offers Rejoin group, and once the AppView catches up, it lists no partner

#### Scenario: No partners
- **WHEN** Bob opens his own ride that lists no partner and hasn't left its group
- **THEN** the page offers neither Leave group nor Rejoin group

#### Scenario: Left with no partners listed
- **WHEN** Bob opens his own ride that left its group and now lists no partner
- **THEN** the page offers Rejoin group

#### Scenario: Another viewer
- **WHEN** Alice opens Bob's ride
- **THEN** she sees no Leave group or Rejoin group

#### Scenario: Track not sent yet
- **WHEN** Bob opens a ride he just published offline, whose segment track is still queued
- **THEN** the page offers neither Leave group nor Rejoin group
