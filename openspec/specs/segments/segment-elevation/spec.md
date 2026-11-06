# segments/segment-elevation Specification

## Purpose

Gives every segment an elevation gain and loss that the AppView derives itself, from terrain data first and then from athletes' matched efforts, without revealing any single athlete's track, and shows them on the segment screen.

## Requirements

### Requirement: A segment view carries its elevation when known
Every segment view the AppView serves SHALL include the segment's elevation gain and loss in whole meters, both at least 0, and their source (`terrain` or `efforts`) whenever the AppView has derived them. When it has derived neither, it SHALL leave all three out. The fields SHALL be optional, so clients that don't know them keep working.

#### Scenario: Segment with elevation
- **WHEN** a viewer asks for a segment whose gain is 84 m and loss is 12 m from terrain
- **THEN** the view has `elevationGain` 84, `elevationLoss` 12 and `elevationSource` `terrain`

#### Scenario: Segment without elevation
- **WHEN** a viewer asks for a segment with no terrain coverage and too few efforts with altitude
- **THEN** the view has no `elevationGain`, `elevationLoss` or `elevationSource`

#### Scenario: Every segment view
- **WHEN** a segment with elevation appears in area search, name search or a duplicate check's results
- **THEN** each of those views carries the same elevation as `feed.getSegment`

### Requirement: Elevation is derived, never read from the record
The AppView SHALL derive a segment's elevation itself. It SHALL ignore any elevation a `feed.segment` record carries, whoever wrote it.

#### Scenario: Record claims an elevation
- **WHEN** a segment record includes an unknown `elevationGain` field of 5000
- **THEN** the served elevation is the AppView's own derivation and not 5000

### Requirement: Terrain elevation comes from the segment's line
When a segment is indexed and the AppView has terrain data covering its whole line, the AppView SHALL sample terrain heights along the line from start to end and store the gain and loss as the segment's terrain elevation. A segment whose line is not fully covered SHALL have no terrain elevation.

#### Scenario: Covered segment
- **WHEN** a segment is indexed inside the area the AppView's terrain data covers
- **THEN** it gets a terrain gain and loss without anyone having ridden it

#### Scenario: Partly covered segment
- **WHEN** a segment's line runs past the edge of the terrain data
- **THEN** it has no terrain elevation

#### Scenario: No terrain data configured
- **WHEN** the AppView runs without terrain data
- **THEN** segments are indexed as before, with no terrain elevation

### Requirement: Editing a segment recomputes its terrain elevation
When a segment's line changes, the AppView SHALL recompute its terrain elevation from the new line.

#### Scenario: Line edited
- **WHEN** a creator republishes a segment with a longer line that climbs further
- **THEN** its terrain gain grows to match the new line

### Requirement: Small fluctuations don't count as climbing
Gain and loss SHALL count only height changes that reverse direction by at least 3 m, so noise and small undulations in terrain or altitude samples don't add up. This SHALL apply to both terrain and effort elevation.

#### Scenario: Noisy flat segment
- **WHEN** a segment's heights wobble up and down by 1 m around a constant level
- **THEN** its gain and loss are 0

#### Scenario: Steady climb
- **WHEN** a segment's heights rise steadily by 120 m from start to end
- **THEN** its gain is 120 and its loss is 0

### Requirement: Terrain is smoothed before it is counted
Before gain and loss are counted, terrain heights SHALL be smoothed along the line: each sample replaced by the mean of the samples within 25 m either side of it, narrowed evenly near each end so the start and end keep their own heights. Terrain detail finer than about 50 m SHALL NOT count as climbing. Effort altitude SHALL NOT be smoothed this way.

#### Scenario: Short terrain ripples
- **WHEN** a segment's terrain heights climb steadily by 100 m but also rise and fall by 4 m every 20 m along the way
- **THEN** its terrain gain is 100 and its terrain loss is 0

#### Scenario: Effort altitude isn't smoothed
- **WHEN** an effort's altitude rises 5 m and falls back 5 m within 20 m along a flat segment
- **THEN** the effort records a gain of 5 and a loss of 5

### Requirement: An effort records elevation only from its track's altitude
When the AppView records an effort, it SHALL compute that effort's gain and loss from the altitude samples its segment track holds between the effort's start and end. When the track has no altitude, or none within that span, the effort SHALL have no elevation. Missing samples SHALL be skipped.

#### Scenario: Track with altitude
- **WHEN** a segment track with altitude passes over a segment
- **THEN** the effort records the gain and loss its altitude shows over the pass

#### Scenario: Track without altitude
- **WHEN** a segment track with no altitude passes over a segment
- **THEN** the effort is recorded as before, with no elevation

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

### Requirement: No single athlete's elevation is revealed
The AppView SHALL never serve an individual effort's elevation, in any view or to any viewer, including the effort's own athlete. Effort elevation SHALL be used only for the segment's aggregate.

#### Scenario: Effort views
- **WHEN** a viewer reads a leaderboard, their own efforts or an activity's segment efforts
- **THEN** no effort in them carries an elevation

### Requirement: Effort elevation follows its efforts
When efforts are dropped (their track, activity, setting or segment goes away), the segment's effort elevation SHALL be recomputed without them. If too few athletes remain, a segment without terrain elevation SHALL serve no elevation.

#### Scenario: Athlete turns matching off
- **WHEN** one of exactly three athletes with efforts with elevation on a segment without terrain coverage turns segment matching off, and the threshold is three
- **THEN** the segment serves no elevation instead of the effort median

### Requirement: Existing segments get elevation
After this change is deployed, the AppView SHALL derive terrain elevation for segments already indexed, and effort elevation from efforts already recorded, without needing their records or tracks to be republished.

#### Scenario: Upgrade
- **WHEN** the AppView starts with terrain data for the first time
- **THEN** segments indexed earlier inside its coverage get terrain elevation

#### Scenario: Stored efforts
- **WHEN** the AppView first runs with effort elevation
- **THEN** segments whose stored efforts' tracks have altitude get effort elevation once enough athletes' efforts are recomputed

### Requirement: Existing segments are recomputed with smoothing
After this change is deployed, the AppView SHALL recompute the terrain elevation of every indexed segment, smoothed, and give each covered segment a profile, without needing any record to be republished.

#### Scenario: Upgrade
- **WHEN** the AppView first starts after this change, with terrain data configured
- **THEN** segments indexed earlier inside its coverage serve smoothed terrain elevation and get a profile

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

### Requirement: The segment screen shows elevation
When a segment's view has elevation, the segment screen SHALL show its gain, its loss and its average grade (net height change over its length, as a percentage), in the viewer's units, next to its length. When the view has none, the screen SHALL show neither the values nor a placeholder.

#### Scenario: Segment with elevation
- **WHEN** a viewer using imperial units opens a 1.6 km segment with gain 84 m and loss 12 m
- **THEN** the screen shows its gain and loss in feet and an average grade of 4.5%

#### Scenario: Segment without elevation
- **WHEN** a viewer opens a segment whose view has no elevation
- **THEN** the screen shows its length and type as before, with no elevation line

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
