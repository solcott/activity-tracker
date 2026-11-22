# path-profile Specification

## Purpose

A trail or road's terrain profile (`segments/segment-paths`): the AppView derives it from terrain tiles as it does a segment's (`segments/segment-elevation`) and serves it with where each segment lies along it. The Trail page (`segments/path-page`) draws it under its map, marks its rows' stretches on it, and ties it to the selection, the filters (`segments/path-filters`) and the map.

## Requirements

### Requirement: A path's terrain profile
For each line piece of an indexed path that the terrain data covers, the AppView SHALL keep the piece's terrain profile:
- **Heights:** its smoothed terrain heights in whole decimeters, sampled and smoothed as a segment's terrain is, evenly spaced from its start to its end, one about every 50 m, with at least 2 and at most 512.
- **Length, maximum grade, climb and descent:** measured and counted as a segment's terrain are.

#### Scenario: Covered trail
- **WHEN** the importer publishes a 6 km trail as one piece inside the terrain coverage
- **THEN** that piece gets a profile of about 120 heights from its start to its end, with its climb, descent and maximum grade

#### Scenario: Long trail
- **WHEN** a 60 km trail's one piece is covered
- **THEN** its profile has 512 heights, evenly spaced over the 60 km

### Requirement: Only covered pieces have a profile
A piece that runs past the edge of the terrain data SHALL have no profile, and neither SHALL any piece when no terrain tiles are configured.

#### Scenario: One piece past the edge
- **WHEN** a trail has two pieces and the second runs past the edge of the terrain data
- **THEN** only the first piece has a profile

### Requirement: Edited paths are sampled again
When a path's lines change, its profiles SHALL follow the new lines, and a deleted path's profiles SHALL go with it.

#### Scenario: Lines edited
- **WHEN** the importer republishes a path with a longer line
- **THEN** its profiles follow the new line

### Requirement: Existing paths get a profile
Once terrain tiles are configured, every path indexed before them, or whose pieces they didn't cover when it was indexed, SHALL be sampled again. This SHALL happen in the background, without holding up indexing or queries.

#### Scenario: Tiles added later
- **WHEN** the AppView starts with terrain tiles for the first time
- **THEN** each covered path already indexed gets its profile in the background

### Requirement: The path query serves the profile
`feed.getPath` SHALL return the path's profile beside the path view whenever at least one of its pieces has one, and SHALL leave it out otherwise. The profile SHALL hold each covered piece's index in the path's lines, its heights, length and maximum grade, in the lines' order, and the climb and descent of the covered pieces together. No other query SHALL carry a path's profile, and the profile SHALL be optional so older clients keep working.

#### Scenario: Covered trail
- **WHEN** a viewer asks `feed.getPath` for Ann's, whose one piece is covered
- **THEN** the output has a profile with that piece's heights and the trail's climb and descent

#### Scenario: No terrain
- **WHEN** no piece of a road is covered
- **THEN** the output has no profile

### Requirement: The path query gives each segment's stretch
The profile SHALL hold a stretch for each returned segment that lies along a covered piece: the segment's URI, the piece's index, and how far along the piece its ends are, nearer first. A segment SHALL lie along a piece when its start and its end are each within 30 m of it. When it lies along several, the first in the lines' order SHALL be used. A segment along none SHALL have no stretch. Stretches SHALL be given only for segments the query returns.

#### Scenario: A segment on the trail
- **WHEN** a viewer asks `feed.getPath` for Ann's
- **THEN** the profile has a stretch for each returned segment on Ann's covered piece

#### Scenario: A segment running against the line
- **WHEN** a downhill-only segment runs the opposite way to the piece's line
- **THEN** its stretch still gives its ends nearer first

#### Scenario: A segment the viewer can't get
- **WHEN** a proposed segment on Ann's isn't one the viewer could get from a segment search
- **THEN** the profile has no stretch for it

### Requirement: The Trail page shows the profile
When the path has a profile, the Trail page SHALL draw it as an elevation chart, tinted by grade as a segment's profile is (`segments/segment-elevation`): covered pieces left to right in the order the trail runs, across the distance in the athlete's units, leaving out a piece without a profile. The pieces SHALL be chained end to end: the longest piece first in its own direction, then each piece whose end lies within 30 m of the chain's last or first point running on from it, turned round (its heights, its stretches and the map's marker with it) when its other end is the one that meets. Pieces that meet SHALL run on with no break. A piece meeting a chained piece anywhere but at the chain's ends is a branch and SHALL be left out of the chart. Pieces meeting none SHALL follow, chained the same way, after a break where the line lifts. It SHALL read out a point's distance, height and grade as a segment's chart does. Without a profile the page SHALL show no chart.

#### Scenario: Two arms meeting at the bottom
- **WHEN** a trail is stored as two arms, each drawn from the top down, that meet at the bottom
- **THEN** the chart runs down the longer arm and on up the shorter one, as one line with no break

#### Scenario: A stub off the trail
- **WHEN** a trail has a short piece branching off the middle of another
- **THEN** the chart leaves it out, and the map still draws it

#### Scenario: Two separate parts
- **WHEN** a trail's two pieces don't meet
- **THEN** the chart draws the longer, a break, then the other

#### Scenario: Road without terrain
- **WHEN** an athlete opens a road with no profile
- **THEN** the page has no elevation chart

### Requirement: The profile sits under the map with the trail's climb
On a phone the chart SHALL be the list's first row, scrolling with the rest of the list, and with the map beside the list, under the map. When the path has a profile, the page's summary SHALL add the trail's climb and descent; without one it SHALL say neither.

#### Scenario: Ann's on a phone
- **WHEN** an athlete opens Ann's on a phone
- **THEN** the elevation chart shows at the top of the list under the map, scrolls away with the list, and the summary says how much Ann's climbs and descends

### Requirement: The profile marks each listed row's stretch
Under the chart, the Trail page SHALL mark each listed row's stretch along the trail, from the stretch of the row's shown segment, in the row's rating colour (`segments/trail-ratings`), or the unrated colour for a row with no level. Only rows the search and filters show SHALL be marked. A row whose segment has no stretch SHALL have no mark.

#### Scenario: Marks follow the filters
- **WHEN** an athlete on Ann's chooses Intermediate only
- **THEN** only the Intermediate rows' stretches are marked under the chart

#### Scenario: A row with no stretch
- **WHEN** a row's segment lies along no covered piece
- **THEN** it has no mark under the chart, and still shows in the list

### Requirement: The profile follows the selection
When a row is selected, the chart SHALL shade that row's stretch, and SHALL stop when it is no longer selected. With a pointer, hovering a row or its line SHALL shade its stretch until the pointer leaves. Neither SHALL zoom or move the chart. Sorting SHALL NOT change what is shaded.

#### Scenario: Selecting a stretch
- **WHEN** an athlete selects the Easy stretch on Ann's
- **THEN** the chart shades where the Easy stretch lies along Ann's

#### Scenario: Filtering out the selection
- **WHEN** the selected row is hidden by a filter
- **THEN** no stretch is shaded

### Requirement: Scrubbing the profile marks the map
While an athlete scrubs the chart, the Trail page's map SHALL mark the spot on the trail's line at the readout's distance, and SHALL remove the mark when the readout goes. Scrubbing SHALL NOT move the map or change the selection.

#### Scenario: Scrubbing a climb
- **WHEN** an athlete drags along the chart to Ann's steepest climb
- **THEN** the map marks that spot on Ann's line, and the mark follows the drag

### Requirement: The native Trail page draws the profile natively
On iOS the native Trail page SHALL draw the profile with the system's charts, with the same pieces, breaks, marks, shading, readout and summary as the shared page. Scrubbing SHALL mark the native map, and a mostly vertical drag over the chart SHALL scroll the page.

#### Scenario: Scrubbing on iOS
- **WHEN** alice drags sideways along the chart on the native Ann's page
- **THEN** the readout follows her finger, and the native map marks the same spot
