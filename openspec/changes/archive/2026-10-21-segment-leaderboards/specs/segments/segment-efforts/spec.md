# Spec Delta

## ADDED Requirements

### Requirement: An athlete's leaderboard entry carries a top-10 rank
In `feed.getActivitySegments`, the effort that is the athlete's entry on the segment's all-time, everyone board for the effort's e-bike kind, as `segments/segment-leaderboards` builds it for that viewer, SHALL carry its rank on that board when the rank is 10 or better. That entry is the athlete's fastest ranked effort the viewer may see, so it SHALL carry the rank even when a faster effort of theirs, marked as their PR, isn't ranked. No other effort SHALL carry a rank.

#### Scenario: Third place
- **WHEN** a viewer opens a Public ride whose effort is 3rd on the board they see
- **THEN** the effort carries rank 3

#### Scenario: Outside the top 10
- **WHEN** the ride's effort is 11th on the viewer's board
- **THEN** it carries no rank

#### Scenario: Faster effort excluded
- **WHEN** an athlete's fastest effort is on an activity excluded from leaderboards, and their next fastest is 1st on the board
- **THEN** the next fastest carries rank 1, and the excluded one, still marked PR, carries none

#### Scenario: Proposed segment
- **WHEN** the ride's effort is on a proposed segment
- **THEN** it carries no rank

### Requirement: The athlete learns why an effort isn't ranked
When the viewer is the effort's own athlete and the segment is `active`, an effort that leaderboards don't rank SHALL say why: the athlete hides from leaderboards, the activity is excluded, or the effort is faster than plausible, in that order of precedence. No other viewer SHALL be told.

#### Scenario: Implausible effort
- **WHEN** an athlete opens their run with an effort faster than the run cap
- **THEN** the effort says it's faster than plausible

#### Scenario: Other viewer
- **WHEN** a follower opens the same run
- **THEN** the effort carries neither a rank nor a reason

### Requirement: The activity page shows ranks
The activity page SHALL show an effort's rank, when it has one, beside its "PR" mark, and SHALL show the athlete their own effort's reason for not being ranked.

#### Scenario: Top-10 badge
- **WHEN** a viewer opens a ride whose effort carries rank 2
- **THEN** the effort is shown with "2nd"

#### Scenario: Excluded activity
- **WHEN** an athlete opens their own ride excluded from leaderboards
- **THEN** its efforts say they aren't on leaderboards because the ride is excluded
