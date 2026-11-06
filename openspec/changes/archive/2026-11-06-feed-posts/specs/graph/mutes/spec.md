# Spec Delta

## ADDED Requirements

### Requirement: Muted posts leave the muter's timeline
A muted athlete's posts SHALL leave the muter's timeline, and SHALL still be listed on the muted athlete's profile.

#### Scenario: Muted author
- **WHEN** bob mutes alice and alice shares a proposal with Followers
- **THEN** her post isn't in bob's timeline, and bob still sees it under Posts on her profile
