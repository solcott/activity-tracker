# Spec Delta

## MODIFIED Requirements

### Requirement: Date filter matches on start time
`since` (inclusive) and `until` (exclusive) SHALL be compared with each activity's start time, not when it was posted or indexed. Either bound MAY be given alone. Results SHALL stay in the order their unfiltered query uses (`feed/timeline-order`): the timeline by feed position, an athlete's activities by start.

#### Scenario: Activity backfilled after the range
- **WHEN** an activity that started inside [since, until) was posted after `until`
- **THEN** it is included in the results

#### Scenario: Only a lower bound
- **WHEN** a client passes `since` without `until`
- **THEN** every activity that started at or after `since` is returned
