# Spec Delta

## Purpose

Ski lifts as a kind of segment: public lift lines published by the segment importer, matched against consenting athletes' tracks so a ski day records which lifts it rode, without ranking, naming or Mayors.

## ADDED Requirements

### Requirement: A lift is an alpine segment of kind lift
A lift SHALL be a `feed.segment` of sport group `alpine` and kind `lift`, its line running from the bottom station to the top. The AppView SHALL NOT index a segment of kind `lift` in any other sport group.

#### Scenario: Chairlift
- **WHEN** the importer publishes a segment of kind `lift` in the `alpine` group for a chairlift
- **THEN** the AppView indexes it as a lift

#### Scenario: Lift in another group
- **WHEN** a `feed.segment` of kind `lift` names the sport group `hike`
- **THEN** the AppView doesn't index it

### Requirement: Only the importer publishes lifts
The AppView SHALL index a segment of kind `lift` only from the account it is configured to accept as the segment importer, and SHALL drop one from any other account.

#### Scenario: Athlete-written lift
- **WHEN** an athlete writes a `feed.segment` of kind `lift` straight to their PDS
- **THEN** the AppView doesn't index it

### Requirement: Lift rides are efforts
A lift SHALL be matched against segment tracks, given efforts and given a status exactly as a run segment is, under the same consent, the same tests and the same visibility (`segments/segment-efforts`). Its efforts SHALL count toward its status.

#### Scenario: Riding a gondola
- **WHEN** an athlete with matching on rides an active gondola from bottom to top on a ski day
- **THEN** their activity gets an effort on the gondola, visible at the narrowest of the activity's, Map and Pace/Speed visibilities

#### Scenario: Riding down
- **WHEN** an athlete rides a gondola down from top to bottom
- **THEN** that ride gives no effort on it

### Requirement: Lift efforts are never ranked
An effort on a lift SHALL NOT be marked as the athlete's fastest, SHALL NOT carry a rank, and SHALL NOT carry a reason for not being ranked, in `feed.getActivitySegments` or anywhere else.

#### Scenario: Fastest lift ride
- **WHEN** an athlete's activity has their fastest ride ever on a chairlift
- **THEN** `feed.getActivitySegments` lists the effort with no fastest mark and no rank

### Requirement: Segment views carry their kind
Every segment view the AppView serves, in full or in brief, SHALL give the segment's kind, `run` or `lift`.

#### Scenario: Effort on a lift
- **WHEN** a viewer lists an activity's efforts and one is on a lift
- **THEN** that effort's segment view has kind `lift`

### Requirement: The activity page lists the lifts ridden
The activity page SHALL show an activity's lift efforts, the ones the viewer is given, in a Lifts section apart from its segments. It SHALL list each lift once, in the order first ridden, with its name, how many times it was ridden and the total elapsed time on it. Tapping one SHALL open the lift's screen. An activity with no lift efforts SHALL show no Lifts section.

#### Scenario: Ski day with lifts
- **WHEN** a viewer opens a ski day with three rides on "Summit Quad" and one on "Base Gondola"
- **THEN** the Lifts section lists "Summit Quad" ridden 3 times and "Base Gondola" once, each with its total time, and no "PR" or rank

#### Scenario: Ski day without lifts
- **WHEN** a viewer opens a ski day with efforts only on runs
- **THEN** the page shows its runs in the segments section and no Lifts section

### Requirement: The lift screen
Opening a lift SHALL show its line on a map, its name, length, status and data credit, and the viewer's own rides on it. It SHALL NOT offer a leaderboard, a Mayor, or name proposals and voting.

#### Scenario: Opening an active lift
- **WHEN** a viewer opens an active lift
- **THEN** the screen shows the lift and the viewer's rides on it, and no leaderboard, Mayor or naming section
