# Spec Delta

## ADDED Requirements

### Requirement: Only proposed and active segments get new efforts
The AppView SHALL match segment tracks only against `proposed` and `active` segments. A segment marked `duplicate` SHALL lose its efforts. An `archived` segment SHALL keep the efforts it has but get no new ones, and a later change to an activity SHALL remove that activity's efforts on it rather than recompute them.

#### Scenario: Duplicate segment
- **WHEN** a ride passes both a segment and a duplicate of it
- **THEN** the ride gets an effort on the original only

#### Scenario: Archived segment
- **WHEN** an athlete rides an archived segment
- **THEN** the ride gets no effort on it, and their earlier efforts on it stay

## MODIFIED Requirements

### Requirement: The app shows efforts on the activity and segment pages
The activity page SHALL list the activity's efforts the viewer is given, each with its segment's name and the effort's time, and mark the athlete's fastest with "PR". An effort on a segment that isn't `active` SHALL also show the segment's status. Tapping one SHALL open the segment screen. The segment screen SHALL show the segment's line on a map, its name, length, sport group and status, a link to the original for a `duplicate`, and the viewer's own efforts on it, fastest first. An activity with no efforts SHALL show no segments section.

#### Scenario: Activity with efforts
- **WHEN** an athlete opens their own ride with two efforts
- **THEN** the page lists both segments with their times, and opens a segment's screen when one is tapped

#### Scenario: Effort on a proposed segment
- **WHEN** an athlete opens a run with an effort on a proposed segment
- **THEN** the effort is listed and marked as proposed

#### Scenario: Duplicate segment's screen
- **WHEN** an athlete opens a segment marked `duplicate`
- **THEN** the screen shows that it's a duplicate and links to the original

#### Scenario: Activity without efforts
- **WHEN** a viewer opens an activity with no efforts they may see
- **THEN** the page shows no segments section
