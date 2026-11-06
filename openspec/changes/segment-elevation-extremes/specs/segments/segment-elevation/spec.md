# Spec Delta

## ADDED Requirements

### Requirement: A segment's maximum grade
For each segment with terrain elevation, the AppView SHALL keep a maximum grade in tenths of a percent: the steepest absolute grade over about 50 m (25 m either side of each point) on the smoothed terrain heights its gain and loss come from. Descents SHALL count as climbs. Segments without terrain elevation SHALL have none, and a changed line SHALL be recomputed.

#### Scenario: Steady climb
- **WHEN** a covered segment's smoothed terrain rises 8 m every 100 m along its whole length
- **THEN** its maximum grade is 80 (8.0%)

#### Scenario: Steep descent within a gentle segment
- **WHEN** a covered segment climbs gently but falls 15 m over one 100 m stretch
- **THEN** its maximum grade is at least 150 (15.0%)

#### Scenario: Short ripple
- **WHEN** a covered segment's raw terrain has a 4 m bump over 10 m that smoothing removes
- **THEN** the bump does not set its maximum grade

#### Scenario: Uncovered segment
- **WHEN** a segment's line runs past the edge of the terrain data
- **THEN** it has no maximum grade

### Requirement: The segment query serves the maximum grade with the profile
`feed.getSegment` SHALL include the segment's maximum grade in its `elevationProfile` whenever it serves a profile and the segment has a maximum grade. The field SHALL be optional, so a profile without it stays valid, and no other query SHALL carry it.

#### Scenario: Terrain segment with a maximum grade
- **WHEN** a viewer asks `feed.getSegment` for a segment whose elevation comes from terrain and which has a maximum grade
- **THEN** the output's `elevationProfile` carries the heights and the maximum grade

#### Scenario: Segment without terrain
- **WHEN** a viewer asks `feed.getSegment` for a segment whose elevation comes from efforts
- **THEN** the output has no `elevationProfile`, and so no maximum grade

### Requirement: Existing segments gain a maximum grade
After this change is deployed, the AppView SHALL recompute the terrain elevation of every indexed segment, so each covered segment gains a maximum grade without any record being republished.

#### Scenario: Upgrade
- **WHEN** the AppView first starts after this change, with terrain data configured
- **THEN** segments indexed earlier inside its coverage get a maximum grade

### Requirement: The segment screen shows high point, low point and maximum grade
When the segment query returns a profile, the segment screen SHALL show the segment's high point and low point, from the profile's heights, and its maximum grade when the profile carries one. They SHALL appear on a line under the gain, loss and average grade, with elevation in the viewer's units and grade as a whole percent. Without a profile, the screen SHALL show none of them.

#### Scenario: Segment with a profile and maximum grade
- **WHEN** a viewer using imperial units opens a segment whose profile runs from 1,616.0 m to 1,835.3 m and has a maximum grade of 162
- **THEN** the screen shows a high point of 6,021 ft, a low point of 5,302 ft and a maximum grade of 16%

#### Scenario: Profile without a maximum grade
- **WHEN** a viewer opens a segment whose profile carries no maximum grade
- **THEN** the screen shows the high and low points and no maximum grade

#### Scenario: Segment without a profile
- **WHEN** a viewer opens a segment whose elevation comes from efforts
- **THEN** the screen shows the gain, loss and grade line and no high point, low point or maximum grade
