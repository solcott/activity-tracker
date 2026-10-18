# feed/activity-zones Specification

## Purpose

Defines where an activity's heart rate and power zone snapshots are stored, who may see them, and how they follow changes to the activity's charts visibility and the athlete's training visibility, so that zones never reveal thresholds wider than the training they come from.

## Requirements

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

### Requirement: Publishing uses the current training visibility
When an activity's zones are published, their place SHALL be computed from the athlete's training visibility as saved at that moment, not as it was when the session started.

#### Scenario: Training narrowed on another device
- **WHEN** an athlete narrows training to Only me on one device, then publishes an activity with zones on another device that was already signed in
- **THEN** the zones record is stored in the `self` space

### Requirement: Zones shown only to viewers their place admits
`feed.getActivityStreams` SHALL include an activity's zone snapshots only for viewers who may see the zones record at its visibility, in addition to the charts and the activity. Whether the athlete's training details are public SHALL NOT matter.

#### Scenario: Mutual sees Mutuals zones
- **WHEN** a mutual of the athlete fetches the streams of a Public activity whose zones are in the `mutuals` space
- **THEN** the response includes the zone snapshots

#### Scenario: Follower doesn't see Mutuals zones
- **WHEN** a one-way follower of the athlete fetches the streams of that same activity
- **THEN** the response includes the streams and no zone snapshots

#### Scenario: Athlete's own zones
- **WHEN** the athlete fetches their own activity's streams with zones in the `self` space
- **THEN** the response includes the zone snapshots

#### Scenario: Zones fields in a streams record ignored
- **WHEN** a `feed.activityStreams` record carries zone fields
- **THEN** no viewer is given zones from it

### Requirement: Zones in two places shown at the narrower
While an activity's zones are stored in two places mid-move, each viewer SHALL be given them at most once, and only if they may see the narrower copy.

#### Scenario: Mid-move
- **WHEN** an activity's zones are in both the repo and the `followers` space
- **THEN** a viewer who isn't an effective follower gets no zone snapshots

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

### Requirement: Narrowing training narrows zones first
When an athlete narrows their training visibility, every zones record stored wider than the new visibility SHALL be moved to the new visibility before the training details are saved at it. If the moves don't all finish, the training save SHALL fail, and no zones SHALL be left wider than the training's saved visibility.

#### Scenario: Followers to Only me
- **WHEN** an athlete with Followers training and zones on past activities in their `followers` space and repo narrows training to Only me
- **THEN** those zones are in the `self` space, and only then is training saved as Only me

#### Scenario: Interrupted backfill
- **WHEN** the moves stop part-way, for example when the PDS rate-limits the athlete
- **THEN** the training save reports a failure, training keeps its previous visibility, and the remaining moves finish later without the athlete re-saving

#### Scenario: Retry
- **WHEN** the athlete saves the narrower training again after an interrupted backfill
- **THEN** only zones still stored wider than the new visibility are moved, and the training is saved once none remain

### Requirement: Widening or clearing training leaves zones in place
Widening the training visibility or clearing the training details SHALL NOT move or delete existing zones records. Activities published afterwards SHALL be placed by the new training visibility.

#### Scenario: Training widened
- **WHEN** an athlete widens training from Only me to Public
- **THEN** zones already in the `self` space stay there, and the next published activity with Public charts puts its zones in the repo

#### Scenario: Training cleared
- **WHEN** an athlete clears their training details
- **THEN** existing zones records stay where they are, and their charts still show time in zone to viewers who may see them

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

### Requirement: Zones record carries training load
An activity's training load (FTP, intensity factor, training stress score, aerobic and anaerobic training effect) SHALL be stored on its `feed.activityZones` record, in the same place as its zone snapshots. An activity with training load and no zone snapshots SHALL still have a zones record, holding only the training load.

#### Scenario: Ride with zones and TSS
- **WHEN** an athlete publishes an imported ride with zone snapshots and a TSS
- **THEN** its zones record holds both

#### Scenario: Training load without zones
- **WHEN** an athlete with no thresholds set publishes an imported ride whose file has an FTP and TSS
- **THEN** a zones record with only the training load is stored at the narrower of the ride's charts visibility and the athlete's training visibility

### Requirement: Training load moves with the zones
Training load SHALL move, narrow and widen exactly as the zones record it is on does.

#### Scenario: Training narrowed
- **WHEN** an athlete narrows training from Public to Only me
- **THEN** each activity's training load moves to their `self` space with its zones
