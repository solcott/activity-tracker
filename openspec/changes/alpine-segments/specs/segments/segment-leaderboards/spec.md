# Spec Delta

## MODIFIED Requirements

### Requirement: Only active segments have leaderboards
`feed.getSegmentLeaderboard` SHALL return a segment's board only while the segment is `active` and of kind `run`. For a segment of any other status, and for a lift, it SHALL return no entries. A segment that isn't indexed or isn't served to the viewer SHALL be `NotFound`.

#### Scenario: Proposed segment
- **WHEN** a viewer asks for the leaderboard of a proposed segment that three efforts are on
- **THEN** the AppView returns no entries

#### Scenario: Unknown segment
- **WHEN** a viewer asks for the leaderboard of a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

#### Scenario: Active lift
- **WHEN** a viewer asks for the leaderboard of an active lift that many athletes have ridden
- **THEN** the AppView returns no entries

#### Scenario: Active alpine run
- **WHEN** a viewer asks for the leaderboard of an active `alpine` run
- **THEN** the AppView returns its board like any other segment's, with `ski` and `snowboard` as separate board types

### Requirement: Implausibly fast efforts are not ranked
An effort SHALL NOT be ranked when its average speed over the segment's computed length is above its sport group's cap: 100 km/h for `ride`, 40 km/h for `run`, 30 km/h for `hike`, 120 km/h for `ski`, 120 km/h for `alpine` and 25 km/h for `paddle`. It SHALL stay on its activity. The caps SHALL apply as they stand when a board is read, without rematching.

#### Scenario: Run in a car
- **WHEN** a run's effort covers a 2 km segment in 60 s
- **THEN** it is on no board, and the run still lists it

#### Scenario: Fast descent
- **WHEN** a ride covers a 3 km descent at an average of 85 km/h
- **THEN** the effort is ranked

#### Scenario: Ski run on a snowmobile
- **WHEN** an `alpine` effort covers a 1 km run in 25 s, an average of 144 km/h
- **THEN** it is on no board, and the ski day still lists it
