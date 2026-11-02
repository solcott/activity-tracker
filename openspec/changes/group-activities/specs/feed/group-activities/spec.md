# Spec Delta

## Purpose

Links activities that athletes did together, the way Strava shows group activities. Only athletes who opt in are linked, each can leave the group on any one activity, and a partner is shown only to viewers who may see that partner's activity, its start time and its map.

## ADDED Requirements

### Requirement: Grouping is off until the athlete opts in
The grouping setting SHALL be one `actor.groupPrefs` record in the athlete's `self` space, never in the repo or any other space. It SHALL hold one of Off, Connected or Anyone. An athlete with no such record, or one holding a value the reader doesn't know, SHALL be treated as Off.

#### Scenario: Never chosen
- **WHEN** an athlete who has never chosen records a ride alongside an athlete who opted in to Anyone
- **THEN** neither activity is grouped with the other

#### Scenario: Stored privately
- **WHEN** an athlete chooses Anyone
- **THEN** their `self` space holds an `actor.groupPrefs` saying Anyone, and their repo holds no record of the choice

### Requirement: The athlete is asked once
The app SHALL ask an athlete who has never chosen and has published a GPS activity which grouping choice they want. The ask SHALL explain that a copy of their routes with privacy zones removed is shared with the AppView to find who they were with, and who will see the link. Declining SHALL store Off, and no device SHALL ask again. Settings SHALL let the athlete change the choice at any time.

#### Scenario: Decline
- **WHEN** an athlete is asked and declines
- **THEN** their `actor.groupPrefs` says Off, and no device asks them again

#### Scenario: Change later
- **WHEN** an athlete who declined chooses Connected in Settings
- **THEN** their `actor.groupPrefs` says Connected

### Requirement: Saving the grouping choice is a direct call
Saving the grouping choice SHALL write to the PDS at once and report a failure, not queue the save. A failed save SHALL leave the earlier choice in force. A session without the `space:` scope for `actor.groupPrefs` and `feed.segmentTrack` SHALL NOT offer the choice, and SHALL tell the athlete to sign in again to use it.

#### Scenario: Offline save
- **WHEN** an athlete chooses Anyone while offline
- **THEN** the app reports that it couldn't save, and the earlier choice stays

#### Scenario: Old scope
- **WHEN** a session's `space:` scope doesn't include `actor.groupPrefs`
- **THEN** Settings doesn't offer the choice and says to sign in again

### Requirement: Who may be paired
Two athletes' activities SHALL be paired only when both athletes' choices are Connected or Anyone and neither has blocked the other. When either athlete chose Connected, one of them SHALL also effectively follow the other. An athlete SHALL NOT be paired with themselves.

#### Scenario: Both Anyone
- **WHEN** two athletes who don't follow each other both chose Anyone and ride together
- **THEN** their rides are paired

#### Scenario: Connected without a follow
- **WHEN** an athlete who chose Connected rides with an athlete who chose Anyone, and neither follows the other
- **THEN** their rides aren't paired

#### Scenario: Connected with a one-way follow
- **WHEN** an athlete who chose Connected rides with someone who follows them, and both have opted in
- **THEN** their rides are paired

#### Scenario: Pending follow request
- **WHEN** the only follow between two Connected athletes is a request their approve-followers profile hasn't approved
- **THEN** their rides aren't paired

#### Scenario: Block
- **WHEN** two opted-in athletes ride together and one has blocked the other
- **THEN** their rides aren't paired

### Requirement: Same sport group
Two activities SHALL be paired only when both belong to the same segment sport group. Activity types in no sport group (indoor, virtual, lift-served skiing, whitewater) SHALL NOT be paired.

#### Scenario: Ride and mountain bike ride
- **WHEN** one athlete records a ride as Ride and their partner records the same ride as a mountain bike ride
- **THEN** the two activities are paired

#### Scenario: Ride and run
- **WHEN** a runner and a cyclist cover the same route at the same time
- **THEN** their activities aren't paired

#### Scenario: Indoor
- **WHEN** two opted-in athletes ride indoor trainers at the same time
- **THEN** their activities aren't paired

### Requirement: Together in time and place
Two activities SHALL be paired only when their time overlap is at least 5 minutes and at least half the shorter activity's elapsed time, and their positions at the same moments stay within about 100 m for most of that overlap. Only shown track parts SHALL count: parts cut by either athlete's privacy zones SHALL be neither near nor far.

#### Scenario: Rode together
- **WHEN** two athletes start at the same trailhead within a minute of each other and ride the same loop side by side
- **THEN** their rides are paired

#### Scenario: Same route, different time
- **WHEN** two athletes ride the same loop two hours apart
- **THEN** their rides aren't paired

#### Scenario: Same time, different route
- **WHEN** two athletes ride at the same time on routes several kilometres apart
- **THEN** their rides aren't paired

#### Scenario: Split up partway
- **WHEN** two athletes ride together for the first 10 minutes of a 2-hour ride, then split
- **THEN** their rides aren't paired

#### Scenario: Shared start inside a privacy zone
- **WHEN** two athletes leave from a house inside one athlete's privacy zone and ride together after leaving it
- **THEN** their rides are paired by the shown parts alone

### Requirement: Pairs are direct
Each activity SHALL list only the activities it is paired with itself. An activity paired with a second, which is paired with a third, SHALL NOT be listed with the third unless those two are paired too.

#### Scenario: Not chained
- **WHEN** Alice is paired with Bob and Bob with Carol, but Alice and Carol aren't paired because Alice chose Connected and doesn't follow Carol
- **THEN** Alice's activity lists Bob and not Carol

### Requirement: Leaving the group on an activity
The owner of a paired or pairable activity SHALL be able to leave the group on that activity, and later rejoin. Leaving SHALL remove the activity from every pair, so no page lists it as a partner, and SHALL keep it from being paired until the owner rejoins. Rejoining SHALL let it be paired again by the same rules. Leaving SHALL NOT affect the owner's other activities.

#### Scenario: Leave
- **WHEN** Bob leaves the group on a ride paired with Alice's
- **THEN** Alice's activity no longer lists Bob, and Bob's ride lists no one

#### Scenario: Rejoin
- **WHEN** Bob rejoins the group on that ride
- **THEN** once the AppView matches it again, Alice's and Bob's rides list each other

#### Scenario: Other activities
- **WHEN** Bob leaves the group on one ride
- **THEN** his other rides with Alice stay paired

### Requirement: The leave choice is private
Whether an activity left its group SHALL be stored only in the athlete's `self` space and SHALL NOT be served to anyone but its owner's own devices. Other viewers SHALL see only that the activity lists no partner.

#### Scenario: Partner's view
- **WHEN** Bob leaves the group on a ride paired with Alice's
- **THEN** nothing Alice is served says that Bob left

### Requirement: A viewer sees only partners they may see
An activity's served view SHALL list a partner only when the viewer may see the partner's activity at the narrowest of its visibility and its Start time and Map category visibilities. It SHALL NOT list a partner when the viewer and the partner have blocked each other in either direction, or when the viewer has muted the partner. The activity's owner is a viewer like any other.

#### Scenario: Followers-only partner
- **WHEN** Bob's paired ride is Followers-only and Carol, who doesn't follow Bob, opens Alice's activity
- **THEN** Carol sees no partner on it

#### Scenario: Map narrower than the activity
- **WHEN** Bob's paired ride is Public with its Map set to Only me
- **THEN** no viewer but Bob sees Bob as a partner on Alice's activity

#### Scenario: Owner can't see the partner
- **WHEN** Alice's ride is paired with Bob's Mutuals ride and Alice and Bob aren't mutuals
- **THEN** Alice's own page doesn't list Bob

#### Scenario: Muted partner
- **WHEN** Carol has muted Bob and opens Alice's activity paired with Bob's
- **THEN** Carol doesn't see Bob listed

### Requirement: Pairing follows changes
A pair SHALL stop being served as soon as the AppView indexes any change that breaks the pairing rule: either athlete choosing Off, a follow ending under Connected, a block, either activity or its track being deleted, or either activity leaving its group. A change in the viewer's access SHALL take effect on the next view.

#### Scenario: Unfollow under Connected
- **WHEN** Alice chose Connected and stops following Bob, the only follow between them
- **THEN** their activities stop listing each other

#### Scenario: Visibility narrowed
- **WHEN** Bob narrows a paired ride from Public to Only me
- **THEN** no one but Bob sees him listed on Alice's activity

### Requirement: Turning grouping off drops pairs
When an athlete's grouping choice becomes Off, their `self` space is deleted, or their account is deleted, the AppView SHALL drop every pair of that athlete's activities.

#### Scenario: Choose Off
- **WHEN** an athlete with ten paired activities chooses Off
- **THEN** once the AppView indexes the choice, none of the ten lists a partner, and their partners' activities no longer list them

#### Scenario: Choose Anyone again
- **WHEN** that athlete later chooses Anyone again
- **THEN** their activities can be paired again, with the earlier leave choices forgotten if their segment tracks were deleted

### Requirement: The activity page shows partners
The activity page SHALL show a "Together with" section when the view lists at least one partner. It SHALL show each partner's avatar and name, and tapping one SHALL open that partner's activity. A page with no partners SHALL show no section.

#### Scenario: Two partners
- **WHEN** a viewer opens a ride listing Bob and Carol
- **THEN** the page shows "Together with" with Bob and Carol, and tapping Bob opens Bob's ride

#### Scenario: No partners
- **WHEN** a viewer opens an activity listing no partner
- **THEN** the page shows no Together-with section

### Requirement: The owner's page offers leave and rejoin
On the owner's own page of an activity whose segment track is stored, while the owner's grouping choice isn't Off, the page SHALL offer Leave group when the activity hasn't left its group and Rejoin group when it has. The choice SHALL be queued like the owner's other writes and shown at once. The page SHALL NOT offer either to other viewers, or while the track's own write is still queued.

#### Scenario: Leave from the page
- **WHEN** Bob taps Leave group on his paired ride
- **THEN** the page offers Rejoin group, and once the AppView catches up, it lists no partner

#### Scenario: Another viewer
- **WHEN** Alice opens Bob's ride
- **THEN** she sees no Leave group or Rejoin group

#### Scenario: Track not sent yet
- **WHEN** Bob opens a ride he just published offline, whose segment track is still queued
- **THEN** the page offers neither Leave group nor Rejoin group

### Requirement: The feed card names partners
An activity card SHALL show the partners the viewer may see as one short line: "with Bob" for one, "with Bob and Carol" for two, and "with Bob and N others" for more. A card with no partners SHALL show no line.

#### Scenario: Three partners
- **WHEN** a card's activity lists Bob, Carol and Dan
- **THEN** the card reads "with Bob and 2 others"
