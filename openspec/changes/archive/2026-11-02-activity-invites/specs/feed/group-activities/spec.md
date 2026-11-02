# Spec Delta

## ADDED Requirements

### Requirement: An accepted invite pairs the copy with its original
An invitee's copy accepted from an invite (`feed/activity-invites`) SHALL be paired with the original activity, whatever either athlete's grouping choice, sport group, tracks or timing. Such an invite pair SHALL be listed and shown like any other pair, under the same viewer rules, except that its two athletes SHALL always see each other on their own activities, whatever either side's tiers. Blocks and mutes still apply.

#### Scenario: Both have grouping off
- **WHEN** Alice and Bob both have grouping Off and Bob accepts Alice's invite
- **THEN** once the AppView indexes Bob's copy, Alice's ride and Bob's copy list each other, to viewers who may see each

#### Scenario: Copy viewer rules
- **WHEN** Bob's copy is Only me and Carol opens Alice's ride
- **THEN** Carol sees no partner on it, and Alice still sees Bob's copy as her ride's partner

#### Scenario: The members see each other past narrower tiers
- **WHEN** Alice's ride is Followers with an Only-me Map, and Bob, a follower, accepts her invite at Only me
- **THEN** Alice's ride lists Bob's copy to Alice, Bob's copy lists Alice's ride to Bob, and neither lists the other to anyone else

### Requirement: Leaving an invite pair
The inviter SHALL be able to remove an invitee who joined, which deletes the invite and drops the pair but keeps the copy. The invitee SHALL be able to leave the group on their copy, which removes its `copyOf` and drops the pair. Neither SHALL be reversible except by a new invite and accept.

#### Scenario: Inviter removes
- **WHEN** Alice removes Bob, who joined her ride
- **THEN** her ride no longer lists Bob, and Bob's copy stays as an activity listing no partner

#### Scenario: Invitee leaves
- **WHEN** Bob leaves the group on his copy
- **THEN** his copy no longer names Alice's ride, and neither lists the other

## MODIFIED Requirements

### Requirement: Who may be paired
Two athletes' activities SHALL be paired by matching only when both athletes' choices are Connected or Anyone and neither has blocked the other. When either athlete chose Connected, one of them SHALL also effectively follow the other. An athlete SHALL NOT be paired with themselves. An accepted invite pairs activities by its own rules instead.

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

### Requirement: Pairing follows changes
A matched pair SHALL stop being served as soon as the AppView indexes any change that breaks the pairing rule: either athlete choosing Off, a follow ending under Connected, a block, either activity or its track being deleted, or either activity leaving its group. An invite pair SHALL stop being served on a block either way, either activity's deletion, the invite's deletion, the copy losing its `copyOf`, or the original leaving its group, and SHALL NOT stop for a follow ending. A change in the viewer's access SHALL take effect on the next view.

#### Scenario: Unfollow under Connected
- **WHEN** Alice chose Connected and stops following Bob, the only follow between them
- **THEN** their activities stop listing each other

#### Scenario: Visibility narrowed
- **WHEN** Bob narrows a paired ride from Public to Only me
- **THEN** no one but Bob sees him listed on Alice's activity

#### Scenario: Unfollow after accepting
- **WHEN** Bob accepted Alice's invite and later unfollows her
- **THEN** Alice's ride and Bob's copy still list each other to viewers who may see each

#### Scenario: Block after accepting
- **WHEN** Bob accepted Alice's invite and Alice later blocks Bob
- **THEN** neither activity lists the other

#### Scenario: Original leaves its group
- **WHEN** Alice leaves the group on a ride Bob joined by invite
- **THEN** neither lists the other until Alice rejoins

### Requirement: Turning grouping off drops pairs
When an athlete's grouping choice becomes Off, their `self` space is deleted, or their account is deleted, the AppView SHALL drop every matched pair of that athlete's activities. Choosing Off SHALL NOT drop invite pairs. Deleting the inviter's `self` space, which holds the invites, or either account SHALL drop the invite pairs it held.

#### Scenario: Choose Off
- **WHEN** an athlete with ten paired activities chooses Off
- **THEN** once the AppView indexes the choice, none of the ten lists a partner, and their partners' activities no longer list them

#### Scenario: Choose Anyone again
- **WHEN** that athlete later chooses Anyone again
- **THEN** their activities can be paired again, with the earlier leave choices forgotten if their segment tracks were deleted

#### Scenario: Invite pair survives Off
- **WHEN** Bob joined Alice's ride by invite and Alice then chooses Off
- **THEN** her ride and Bob's copy still list each other
