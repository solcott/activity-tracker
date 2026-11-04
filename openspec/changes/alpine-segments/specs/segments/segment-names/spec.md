# Spec Delta

## MODIFIED Requirements

### Requirement: Only regulars may propose
The AppView SHALL accept a proposal only if, when it's indexed, the segment is `active` and of kind `run`, and the proposer has efforts on it on at least a configured number of distinct local dates, 5 by default. Efforts of every visibility SHALL count, but only efforts within the sport group's speed cap. It SHALL ignore any other proposal, and SHALL NOT drop an accepted proposal when the proposer later loses efforts.

#### Scenario: Five days of efforts
- **WHEN** alice has efforts on a segment on 5 different dates, two of them from Only me activities, and proposes a name
- **THEN** the AppView accepts the proposal

#### Scenario: Five efforts in one day
- **WHEN** bob has 5 efforts on a segment, all from one ride, and proposes a name
- **THEN** the AppView ignores the proposal

#### Scenario: Proposed segment
- **WHEN** an eligible athlete proposes a name for a segment whose status is `proposed`
- **THEN** the AppView ignores the proposal

#### Scenario: Lift
- **WHEN** an athlete who has ridden an active lift on 20 different dates proposes a name for it
- **THEN** the AppView ignores the proposal, and `feed.getSegment` tells them they may not propose
