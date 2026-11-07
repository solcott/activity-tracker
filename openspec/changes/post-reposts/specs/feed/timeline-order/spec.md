# Spec Delta

## ADDED Requirements

### Requirement: Repost position
A repost's feed position SHALL be the earlier of when its record says it was created and when the AppView indexed it. Rewriting or moving a repost SHALL NOT change its position.

#### Scenario: Repost of an older post
- **WHEN** alice shared a post on Monday and bob reposts it on Wednesday
- **THEN** in bob's followers' timelines the post is placed at Wednesday's repost

### Requirement: A post appears once in a timeline
A post SHALL appear at most once in a viewer's timeline, across every page. Its item SHALL be placed at the latest of the post's own position, when the viewer's timeline includes the post itself, and the positions of the reposts of it in the viewer's timeline. A tie SHALL go to the post's own position.

#### Scenario: Original and a later repost
- **WHEN** carol follows alice and bob, alice shares a Public post on Monday, and bob reposts it on Wednesday
- **THEN** carol's timeline shows the post once, at Wednesday, reposted by bob

#### Scenario: Repost undone
- **WHEN** bob then undoes his repost
- **THEN** carol's timeline shows the post once, at Monday, with no "Reposted by"

#### Scenario: Paging
- **WHEN** carol pages through a timeline in which a post was reposted by several athletes she follows
- **THEN** the post appears on exactly one page
