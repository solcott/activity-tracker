# Spec Delta

## MODIFIED Requirements

### Requirement: Zones stored at the narrower of charts and training
An activity's zone snapshots SHALL be a `feed.activityZones` record, with the activity's rkey, stored at the narrowest of the activity's Heart rate visibility, its Power visibility and the athlete's training visibility. When all three are Public it SHALL be in the athlete's repo. Otherwise it SHALL be in the athlete's tier space for that visibility, and SHALL NOT be in the repo. Where it is stored SHALL be its only visibility.

#### Scenario: Public charts, Mutuals training
- **WHEN** an athlete whose training is Mutuals publishes an activity with zone snapshots, Public Heart rate and Public Power
- **THEN** the zones record is in their `mutuals` space, and their repo has no `feed.activityZones` record with the activity's rkey

#### Scenario: Followers charts, Public training
- **WHEN** an athlete whose training is Public publishes an activity with zone snapshots, Public Heart rate and Followers Power
- **THEN** the zones record is in their `followers` space

#### Scenario: Everything Public
- **WHEN** an athlete whose training is Public publishes an activity with zone snapshots, Public Heart rate and Public Power
- **THEN** the zones record is in their repo

#### Scenario: Streams carry no zones
- **WHEN** an athlete publishes an activity with zone snapshots
- **THEN** none of its `feed.activityStreams` records has zone fields

### Requirement: Charts moves move zones
When an activity's Heart rate or Power visibility changes, its zones SHALL be moved to the narrowest of the new Heart rate visibility, the new Power visibility and the athlete's training visibility, writing the new place before removing the old one. Deleting an activity SHALL delete its zones from every place.

#### Scenario: Charts narrowed
- **WHEN** an athlete with Public training and Public Power changes an activity's Heart rate from Public to Only me
- **THEN** its zones end up in the `self` space and nowhere else

#### Scenario: Charts widened, capped by training
- **WHEN** an athlete with Followers training changes an activity's Heart rate and Power from Only me to Public
- **THEN** its zones end up in the `followers` space

#### Scenario: Other categories don't move zones
- **WHEN** an athlete changes an activity's Calories from Public to Only me
- **THEN** its zones stay where they are

#### Scenario: Activity deleted
- **WHEN** an athlete deletes an activity with zones
- **THEN** no `feed.activityZones` record with its rkey remains in their repo or tier spaces

### Requirement: Session-start repair
When a signed-in session that can use spaces starts, the client SHALL move any of the athlete's zones records stored wider than the narrowest of its activity's Heart rate visibility, its Power visibility and the current training visibility. It SHALL delete zones copies left in a second place by an interrupted move, and SHALL NOT widen zones. For an activity from before detail categories, the visibility of its single streams record SHALL stand for both Heart rate and Power.

#### Scenario: Zones left wide by another device
- **WHEN** another device published zones at Followers just after the athlete narrowed training to Only me
- **THEN** the athlete's next session start moves them to the `self` space

#### Scenario: Leftover copy
- **WHEN** an interrupted move left an activity's zones in both the `mutuals` and `self` spaces, and `self` is the target
- **THEN** the session start deletes the `mutuals` copy

#### Scenario: Zones narrower than allowed
- **WHEN** an activity's zones are in the `self` space and its Heart rate, Power and training are all Public
- **THEN** the session start leaves them in the `self` space

#### Scenario: Old activity
- **WHEN** an activity from before detail categories has its streams in the `followers` space and its zones in the repo, and training is Public
- **THEN** the session start moves its zones to the `followers` space
