# Spec Delta

## MODIFIED Requirements

### Requirement: An effort is a pass from start to end
An activity SHALL get an effort on a segment of its sport group each time one piece of its segment track passes within 25 m of the segment's start, then follows the segment's line in order to within 25 m of its end, staying within 30 m of every point of the line. For an `alpine` run the distances SHALL be 40 m at the start and end and 50 m along the line, since skiers cross a piste's width. Following the line in reverse SHALL NOT count, and a pass SHALL NOT span two pieces.

#### Scenario: Full pass
- **WHEN** a run's segment track follows a segment from its start to its end
- **THEN** the run gets one effort on it

#### Scenario: Two laps
- **WHEN** a ride does a loop segment twice
- **THEN** the ride gets two efforts on it

#### Scenario: Wrong direction
- **WHEN** a ride covers a segment from its end to its start
- **THEN** the ride gets no effort on it

#### Scenario: Shortcut
- **WHEN** a run leaves a segment's line by more than 30 m in the middle and rejoins it
- **THEN** the run gets no effort on it

#### Scenario: Across a privacy zone
- **WHEN** a segment passes through one of the athlete's privacy zones, so their segment track has a gap there
- **THEN** the activity gets no effort on it

#### Scenario: Carving across a wide piste
- **WHEN** a skier's track swings up to 45 m either side of an `alpine` run's line while following it from top to bottom
- **THEN** the ski day gets an effort on the run

#### Scenario: Lift keeps the usual corridor
- **WHEN** a track passes 40 m to the side of a lift's line all the way up
- **THEN** it gets no effort on the lift

### Requirement: The app shows efforts on the activity page
The activity page SHALL list the activity's efforts on run segments that the viewer is given, each with its segment's name and the effort's time, and mark the athlete's fastest with "PR". An effort on a segment that isn't `active` SHALL also show the segment's status. Tapping one SHALL open the segment screen. An activity with no efforts on run segments SHALL show no segments section. Efforts on lifts SHALL be shown only in the Lifts section (`segments/segment-lifts`).

#### Scenario: Activity with efforts
- **WHEN** an athlete opens their own ride with two efforts
- **THEN** the page lists both segments with their times, and opens a segment's screen when one is tapped

#### Scenario: Effort on a proposed segment
- **WHEN** an athlete opens a run with an effort on a proposed segment
- **THEN** the effort is listed and marked as proposed

#### Scenario: Activity without efforts
- **WHEN** a viewer opens an activity with no efforts they may see
- **THEN** the page shows no segments section

#### Scenario: Ski day with runs and lifts
- **WHEN** a viewer opens a ski day with efforts on two runs and on one lift
- **THEN** the segments section lists the two runs, and the lift appears only in the Lifts section
