# Spec Delta

## MODIFIED Requirements

### Requirement: Non-public charts stored by visibility
An activity's charts (`feed.activityStreams`) whose visibility isn't Public SHALL be stored in the athlete's tier space for that visibility, not in the repo. Moving charts SHALL write them to the new place before removing them from the old one. Where a streams record is stored SHALL be its only visibility: one in the repo SHALL be Public, and one in a tier space SHALL be that tier. The record SHALL NOT carry a visibility of its own, and any it carries SHALL be ignored.

#### Scenario: Followers charts
- **WHEN** an athlete publishes charts with visibility Followers
- **THEN** the streams record and its data are in the `followers` space, and the repo has no streams record with the activity's rkey

#### Scenario: Record carries no visibility
- **WHEN** an athlete publishes charts with any visibility
- **THEN** the streams record has no `visibility` field

#### Scenario: Repo charts are Public whatever they claim
- **WHEN** a streams record in an athlete's repo has a `visibility` field saying Only me
- **THEN** any viewer who may see its activity gets its charts, and `getActivityStreams` reports them as Public

#### Scenario: Space charts take their tier
- **WHEN** a streams record in an athlete's `mutuals` space has a `visibility` field saying Public
- **THEN** only the athlete and their mutuals get its charts, and `getActivityStreams` reports them as Mutuals

## REMOVED Requirements

### Requirement: Existing activities stay Public
**Reason**: No activity or streams record was ever published outside local development before visibility existed, so there's nothing to keep.
**Migration**: None. Records in a local dev stack from before visibility can be re-published, or the stack reset.
