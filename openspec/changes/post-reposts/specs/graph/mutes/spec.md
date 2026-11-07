# Spec Delta

## ADDED Requirements

### Requirement: Muted athletes' reposts leave the muter's timeline
A repost by an athlete the viewer mutes, and a repost of a post by an athlete the viewer mutes, SHALL NOT be in the viewer's timeline. A mute SHALL NOT change any repost count or reposters list.

#### Scenario: Muted reposter
- **WHEN** carol mutes bob, and bob reposts alice's Public post
- **THEN** carol's timeline doesn't show it through bob's repost, while the post's repost count still includes bob

#### Scenario: Muted author
- **WHEN** carol mutes alice, and bob, whom carol follows, reposts alice's Public post
- **THEN** carol's timeline doesn't show the post
