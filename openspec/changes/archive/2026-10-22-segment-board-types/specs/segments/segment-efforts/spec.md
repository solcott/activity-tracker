# Spec Delta

## MODIFIED Requirements

### Requirement: An athlete's leaderboard entry carries a top-10 rank
In `feed.getActivitySegments`, the effort that is the athlete's entry on the segment's all-time, everyone board of the effort's own type, as `segments/segment-leaderboards` builds it for that viewer, SHALL carry its rank on that board when the rank is 10 or better. That entry is the athlete's fastest ranked effort the viewer may see, so it SHALL carry the rank even when a faster effort of theirs, marked as their PR, isn't ranked. No other effort SHALL carry a rank.

#### Scenario: Third place
- **WHEN** a viewer opens a Public ride whose effort is 3rd on the board they see
- **THEN** the effort carries rank 3

#### Scenario: Ranked among its own type
- **WHEN** a handcycle effort is 6th among all efforts on a ride segment but 1st among handcycles
- **THEN** it carries rank 1

#### Scenario: Outside the top 10
- **WHEN** the ride's effort is 11th on the viewer's board
- **THEN** it carries no rank

#### Scenario: Faster effort excluded
- **WHEN** an athlete's fastest effort is on an activity excluded from leaderboards, and their next fastest is 1st on the board
- **THEN** the next fastest carries rank 1, and the excluded one, still marked PR, carries none

#### Scenario: Proposed segment
- **WHEN** the ride's effort is on a proposed segment
- **THEN** it carries no rank
