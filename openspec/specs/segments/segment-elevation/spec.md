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

### Requirement: An effort records elevation only from its track's altitude
When the AppView records an effort, it SHALL compute that effort's gain and loss from the altitude samples its segment track holds between the effort's start and end. When the track has no altitude, or none within that span, the effort SHALL have no elevation. Missing samples SHALL be skipped.

#### Scenario: Track with altitude
- **WHEN** a segment track with altitude passes over a segment
- **THEN** the effort records the gain and loss its altitude shows over the pass

#### Scenario: Track without altitude
- **WHEN** a segment track with no altitude passes over a segment
- **THEN** the effort is recorded as before, with no elevation

### Requirement: Enough athletes' efforts replace the terrain elevation
Once efforts with elevation on a segment come from at least as many distinct athletes as make a proposed segment active, the segment SHALL serve, as source `efforts`, the median across those athletes of each athlete's median effort gain and loss. Below that number it SHALL serve its terrain elevation, if any.

#### Scenario: Enough athletes
- **WHEN** three distinct athletes, with the activation threshold at three, have efforts with elevation on a segment
- **THEN** the view's elevation is the median of the three athletes' medians, with source `efforts`

#### Scenario: One athlete rides often
- **WHEN** one of those athletes has twenty efforts and the others one each
- **THEN** that athlete counts once, through their own median

#### Scenario: Too few athletes
- **WHEN** two distinct athletes have efforts with elevation on a segment and the threshold is three
- **THEN** the view serves the terrain elevation, or none

### Requirement: No single athlete's elevation is revealed
The AppView SHALL never serve an individual effort's elevation, in any view or to any viewer, including the effort's own athlete. Effort elevation SHALL be used only for the segment's aggregate.

#### Scenario: Effort views
- **WHEN** a viewer reads a leaderboard, their own efforts or an activity's segment efforts
- **THEN** no effort in them carries an elevation

### Requirement: Effort elevation follows its efforts
When efforts are dropped (their track, activity, setting or segment goes away), the segment's effort elevation SHALL be recomputed without them. If too few athletes remain, the segment SHALL fall back to its terrain elevation, or to none.

#### Scenario: Athlete turns matching off
- **WHEN** one of exactly three athletes with efforts with elevation turns segment matching off
- **THEN** the segment serves its terrain elevation, or none, instead of the effort median

### Requirement: Existing segments get elevation
After this change is deployed, the AppView SHALL derive terrain elevation for segments already indexed, and effort elevation from efforts already recorded, without needing their records or tracks to be republished.

#### Scenario: Upgrade
- **WHEN** the AppView starts with terrain data for the first time
- **THEN** segments indexed earlier inside its coverage get terrain elevation

#### Scenario: Stored efforts
- **WHEN** the AppView first runs with effort elevation
- **THEN** segments whose stored efforts' tracks have altitude get effort elevation once enough athletes' efforts are recomputed

### Requirement: The segment screen shows elevation
When a segment's view has elevation, the segment screen SHALL show its gain, its loss and its average grade (net height change over its length, as a percentage), in the viewer's units, next to its length. When the view has none, the screen SHALL show neither the values nor a placeholder.

#### Scenario: Segment with elevation
- **WHEN** a viewer using imperial units opens a 1.6 km segment with gain 84 m and loss 12 m
- **THEN** the screen shows its gain and loss in feet and an average grade of 4.5%

#### Scenario: Segment without elevation
- **WHEN** a viewer opens a segment whose view has no elevation
- **THEN** the screen shows its length and type as before, with no elevation line
