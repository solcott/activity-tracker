# Spec Delta

## ADDED Requirements

### Requirement: Invite declines are not stored
The AppView SHALL NOT store `feed.activityInviteDecline` records from any space.

#### Scenario: Decline in self
- **WHEN** a synced `self` space's ops include a `feed.activityInviteDecline` write
- **THEN** the AppView stores nothing from it

## MODIFIED Requirements

### Requirement: Only activity data is indexed from spaces
From tier spaces (`followers`, `mutuals`, `self`), the AppView SHALL index only `feed.activity`, `feed.activityDetails`, `feed.activityStreams` and `feed.activityZones` records. From the `self` space it SHALL also index `feed.segmentTrack`, `actor.segmentPrefs`, `actor.groupPrefs`, `actor.leaderboardBrackets` and `feed.activityInvite` records.

#### Scenario: Zones indexed from a tier space
- **WHEN** a synced `mutuals` space's ops include a `feed.activityZones` write
- **THEN** the AppView indexes the zones at Mutuals

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

### Requirement: Non-public activities served by tier
The AppView SHALL serve an activity held in a tier space only to the athlete and to viewers the tier admits: effective followers for `followers`, mutuals by effective follows for `mutuals`, and only the athlete for `self`. This SHALL hold for the timeline, an athlete's activities (both orders), `getActivity`, activity counts and recordings. The one exception is the invite query, which serves a pending invitee the parts `feed/activity-invites` grants.

#### Scenario: Effective follower's timeline
- **WHEN** bob's follow of alice is effective and alice posts a Followers activity
- **THEN** it appears in bob's timeline and in alice's activities when bob views them

#### Scenario: Pending follower
- **WHEN** bob's follow of alice awaits approval
- **THEN** none of alice's Followers or Mutuals activities appear for bob, and `getActivity` for one answers not found

#### Scenario: Only me
- **WHEN** alice posts an Only-me activity
- **THEN** it appears in alice's own activities and timeline, and for every other viewer `getActivity` for it answers not found

#### Scenario: Activity count per viewer
- **WHEN** alice has 3 Public, 2 Followers and 1 Only-me activities, and bob is her effective follower but not her mutual
- **THEN** alice's profile shows bob 5 activities, shows a non-follower 3, and shows alice 6

#### Scenario: Unauthenticated viewer
- **WHEN** a request without a viewer asks for alice's activities
- **THEN** only her Public activities are returned

#### Scenario: Filters and paging still hold
- **WHEN** a follower pages through a filtered timeline that mixes Public and Followers activities
- **THEN** each page contains only activities the follower may see that match the filter, and the cursor continues the same sequence

#### Scenario: Invited to an Only-me activity
- **WHEN** alice invites bob to an Only-me activity
- **THEN** bob's invite shows its granted parts, and `getActivity` for it still answers not found for bob
