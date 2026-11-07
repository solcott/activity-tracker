# Spec Delta

## ADDED Requirements

### Requirement: Blocked accounts' reposts are hidden
The AppView SHALL NOT show, count or list for a viewer a repost whose reposter is in a block with the viewer, or a repost of a post whose author is in a block with the viewer. A repost SHALL NOT be shown, counted or listed for anyone when its reposter is in a block with the post's author.

#### Scenario: Blocked reposter
- **WHEN** carol blocks bob, and bob reposts alice's Public post
- **THEN** carol's timeline doesn't show it through bob's repost, and bob is left out of her repost count and reposters list

#### Scenario: Blocked author
- **WHEN** carol blocks alice, and bob, whom carol follows, reposts alice's Public post
- **THEN** carol's timeline doesn't show the post

#### Scenario: Reposter blocked by the author
- **WHEN** bob reposted alice's Public post and then alice blocks bob
- **THEN** bob's repost is neither shown, counted nor listed for anyone
