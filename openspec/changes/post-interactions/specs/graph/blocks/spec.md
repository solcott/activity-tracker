# Spec Delta

## MODIFIED Requirements

### Requirement: Blocked accounts' likes and comments are hidden
The AppView SHALL NOT list or count, for a viewer, a like or comment whose giver is in a block with the viewer. It SHALL NOT list or count a like or comment for anyone when its giver is in a block with the athlete whose activity or post it is on. A thread whose top-level comment is hidden this way SHALL follow `Unavailable top-level comment`.

#### Scenario: Blocked commenter on the blocker's activity
- **WHEN** bob commented on alice's Public activity and then alice blocks bob
- **THEN** bob's comment is neither listed nor counted for anyone

#### Scenario: Comments on a third athlete's activity
- **WHEN** alice blocks bob, and both commented on carol's Public activity
- **THEN** alice isn't shown bob's comment and bob isn't shown alice's, while carol and others see both, and each count matches what that viewer is listed

#### Scenario: Likes
- **WHEN** alice blocks bob and bob liked carol's activity
- **THEN** alice's like count and likes list on it leave bob out

#### Scenario: Blocked commenter on the blocker's post
- **WHEN** bob commented on alice's Public post and then alice blocks bob
- **THEN** bob's comment on the post is neither listed nor counted for anyone

#### Scenario: Likes on a third athlete's post
- **WHEN** alice blocks bob and bob liked carol's post
- **THEN** alice's like count and likes list on carol's post leave bob out
