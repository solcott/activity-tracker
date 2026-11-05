# Spec Delta

## MODIFIED Requirements

### Requirement: Small fluctuations don't count as climbing
Gain and loss SHALL count only height changes that reverse direction by at least 3 m, so noise and small undulations in terrain or altitude samples don't add up. This SHALL apply to both terrain and effort elevation. Terrain heights SHALL first be smoothed along the line, each sample replaced by the mean of the samples within 25 m either side of it, narrowed evenly near each end of the line so that the start and end keep their own heights, so terrain detail finer than about 50 m doesn't count as climbing. Effort altitude SHALL NOT be smoothed this way.

#### Scenario: Noisy flat segment
- **WHEN** a segment's heights wobble up and down by 1 m around a constant level
- **THEN** its gain and loss are 0

#### Scenario: Steady climb
- **WHEN** a segment's heights rise steadily by 120 m from start to end
- **THEN** its gain is 120 and its loss is 0

#### Scenario: Short terrain ripples
- **WHEN** a segment's terrain heights climb steadily by 100 m but also rise and fall by 4 m every 20 m along the way
- **THEN** its terrain gain is 100 and its terrain loss is 0

#### Scenario: Effort altitude isn't smoothed
- **WHEN** an effort's altitude rises 5 m and falls back 5 m within 20 m along a flat segment
- **THEN** the effort records a gain of 5 and a loss of 5

### Requirement: Effort elevation follows its efforts
When efforts are dropped (their track, activity, setting or segment goes away), the segment's effort elevation SHALL be recomputed without them. If too few athletes remain, a segment without terrain elevation SHALL serve no elevation.

#### Scenario: Athlete turns matching off
- **WHEN** one of exactly three athletes with efforts with elevation on a segment without terrain coverage turns segment matching off, and the threshold is three
- **THEN** the segment serves no elevation instead of the effort median

## ADDED Requirements

### Requirement: Terrain elevation wins where it covers a segment
A segment with terrain elevation SHALL serve it, with source `terrain`, however many athletes have efforts with elevation on it.

#### Scenario: Covered segment with many riders
- **WHEN** a segment has terrain elevation, and five distinct athletes have efforts with elevation on it with the threshold at three
- **THEN** the view serves the terrain gain and loss with source `terrain`

### Requirement: Efforts fill in where terrain doesn't cover a segment
A segment with no terrain elevation SHALL serve, as source `efforts`, the median across athletes of each athlete's median effort gain and loss. It SHALL do so only once efforts with elevation come from at least as many distinct athletes as make a proposed segment active. Below that it SHALL serve no elevation.

#### Scenario: Enough athletes
- **WHEN** three distinct athletes, with the activation threshold at three, have efforts with elevation on a segment without terrain elevation
- **THEN** the view's elevation is the median of the three athletes' medians, with source `efforts`

#### Scenario: One athlete rides often
- **WHEN** one of those athletes has twenty efforts and the others one each
- **THEN** that athlete counts once, through their own median

#### Scenario: Too few athletes
- **WHEN** two distinct athletes have efforts with elevation on a segment without terrain elevation and the threshold is three
- **THEN** the view has no elevation

### Requirement: A segment's terrain profile
For each segment with terrain elevation, the AppView SHALL keep a profile of its smoothed terrain heights: 128 heights in whole decimeters, evenly spaced along the line, the first at its start and the last at its end. A segment without terrain elevation SHALL have no profile. When the line changes, the profile SHALL be recomputed with the terrain elevation.

#### Scenario: Covered segment
- **WHEN** a segment is indexed inside the terrain coverage
- **THEN** it gets a profile of 128 heights from its start to its end

#### Scenario: Partly covered segment
- **WHEN** a segment's line runs past the edge of the terrain data
- **THEN** it has no profile

#### Scenario: Line edited
- **WHEN** a creator republishes a segment with a different line
- **THEN** its profile follows the new line

### Requirement: The segment query serves the profile
`feed.getSegment` SHALL return the segment's profile beside the segment view whenever the view's elevation source is `terrain`, and SHALL leave it out otherwise. No other query SHALL carry the profile, and the profile SHALL be optional so older clients keep working.

#### Scenario: Terrain segment
- **WHEN** a viewer asks `feed.getSegment` for a segment whose elevation comes from terrain
- **THEN** the output has the segment view and an `elevationProfile` with 128 heights

#### Scenario: Segment without terrain
- **WHEN** a viewer asks `feed.getSegment` for a segment whose elevation comes from efforts, or that has none
- **THEN** the output has no `elevationProfile`

#### Scenario: Other segment queries
- **WHEN** a segment with a profile appears in area search, name search or a duplicate check's results
- **THEN** none of those results carries a profile

### Requirement: Existing segments are recomputed with smoothing
After this change is deployed, the AppView SHALL recompute the terrain elevation of every indexed segment, smoothed, and give each covered segment a profile, without needing any record to be republished.

#### Scenario: Upgrade
- **WHEN** the AppView first starts after this change, with terrain data configured
- **THEN** segments indexed earlier inside its coverage serve smoothed terrain elevation and get a profile

### Requirement: The segment screen shows the elevation profile
When the segment query returns a profile, the segment screen SHALL draw it under the gain, loss and grade line. The chart's horizontal axis is distance along the segment and its vertical axis is elevation, both in the viewer's units. The area under the line is tinted by local grade. Pressing or dragging across it SHALL show the distance, elevation and grade at that point. Without a profile, the screen SHALL draw no chart and no placeholder.

#### Scenario: Segment with a profile
- **WHEN** a viewer using imperial units opens a segment whose query returned a profile
- **THEN** the screen shows the profile with distance in miles and elevation in feet

#### Scenario: Scrubbing
- **WHEN** the viewer drags across the profile
- **THEN** a readout shows the distance, elevation and grade at the point under their finger

#### Scenario: Segment without a profile
- **WHEN** a viewer opens a segment whose elevation comes from efforts
- **THEN** the screen shows the gain, loss and grade line and no chart

#### Scenario: Ski lift
- **WHEN** a viewer opens a ski lift segment with a profile
- **THEN** the screen shows its profile like any other segment's

### Requirement: Grade is tinted in five bands
The profile's tint SHALL show the absolute grade over about 50 m around each point, in five bands: under 3%, 3 to under 6%, 6 to under 10%, 10 to under 15%, and 15% or more. Each band SHALL have its own colour, and the colours SHALL remain distinguishable in light and dark themes. A descent SHALL be tinted by how steep it is, the same as a climb.

#### Scenario: Steep descent
- **WHEN** part of a profile falls 12 m over 100 m
- **THEN** that part is tinted with the 10 to 15% band's colour

## REMOVED Requirements

### Requirement: Enough athletes' efforts replace the terrain elevation
**Reason**: Smoothed terrain is within about 5% of 1 m lidar on the trails checked, while effort altitude comes largely from phone GPS. Terrain now wins wherever it covers a segment.
**Migration**: Replaced by "Terrain elevation wins where it covers a segment" and "Efforts fill in where terrain doesn't cover a segment". Segments with terrain switch to serving terrain values on upgrade. Uncovered segments behave as before.
