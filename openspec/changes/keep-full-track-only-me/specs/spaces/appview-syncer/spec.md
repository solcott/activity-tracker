# Spec Delta

## MODIFIED Requirements

### Requirement: Space-held media and charts served by tier
Photos of a non-public activity, and charts stored in a tier space, SHALL be served only to viewers who may see them. The AppView SHALL fetch them from the space with its own credential rather than redirecting the viewer to the PDS.

#### Scenario: Follower views a photo
- **WHEN** an effective follower opens a Followers activity with a photo
- **THEN** the photo loads

#### Scenario: Non-follower requests a photo URL
- **WHEN** a viewer who may not see that activity requests its photo's URL
- **THEN** the AppView doesn't serve the photo

## ADDED Requirements

### Requirement: Full tracks are not indexed
The AppView SHALL NOT store or serve any `feed.activityTrack` record or its blob, though it syncs the `self` space that holds them.

#### Scenario: Track synced from self
- **WHEN** the AppView syncs an athlete's `self` space holding a `feed.activityTrack`
- **THEN** it stores nothing from that record, and no query or media route returns it
