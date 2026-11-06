# Spec Delta

## MODIFIED Requirements

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity`, `feed.activityDetails`, `feed.activityStreams` and `feed.activityZones` records. From the `followers` and `mutuals` spaces it SHALL also index `feed.post` records. From the `self` space it SHALL also index `feed.segmentTrack`, `actor.segmentPrefs`, `actor.groupPrefs`, `actor.leaderboardBrackets` and `feed.activityInvite` records.

#### Scenario: Zones indexed from a tier space
- **WHEN** a synced `mutuals` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes the zones at Mutuals

#### Scenario: Post indexed from a tier space
- **WHEN** a synced `followers` space's ops include a `feed.post` write
- **THEN** the AppView indexes the post at Followers

#### Scenario: Post in self ignored
- **WHEN** a synced `self` or `interactions` space's ops include a `feed.post` write
- **THEN** the AppView indexes no post from it

#### Scenario: Like in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.like` write
- **THEN** the AppView indexes no like from it

#### Scenario: Comment in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.comment` write
- **THEN** the AppView indexes no comment from it

#### Scenario: Comment hide in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `feed.commentHide` write
- **THEN** the AppView indexes no hide from it

#### Scenario: Block in a tier space ignored
- **WHEN** a synced `followers` space's ops include a `graph.block` or `graph.mute` write
- **THEN** the AppView indexes no block or mute from it

#### Scenario: Segment track indexed from self
- **WHEN** a synced `self` space's ops include a `feed.segmentTrack` write
- **THEN** the AppView indexes it as that athlete's segment track for the activity with its rkey

#### Scenario: Segment track outside self ignored
- **WHEN** a synced `followers` or `interactions` space's ops include a `feed.segmentTrack` or `actor.segmentPrefs` write
- **THEN** the AppView indexes nothing from it

#### Scenario: Invite indexed from self
- **WHEN** a synced `self` space's ops include a `feed.activityInvite` write
- **THEN** the AppView indexes it as that athlete's invite to the activity and invitee it names

#### Scenario: Invite outside self ignored
- **WHEN** a synced `followers` space's ops include a `feed.activityInvite` write
- **THEN** the AppView indexes nothing from it
