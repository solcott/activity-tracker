# Spec Delta

## ADDED Requirements

### Requirement: Post position
A post's feed position SHALL be the earlier of when its record says it was created and when the AppView indexed it. Rewriting a post, or changing its audience, SHALL NOT change its position.

#### Scenario: Shared proposal
- **WHEN** alice shares a proposal at noon and a follower refreshes at 12:05
- **THEN** the post is placed at noon, above activities posted before it

#### Scenario: Created time in the future
- **WHEN** a post's record says it was created after the AppView indexed it
- **THEN** it is placed no later than when it was indexed
