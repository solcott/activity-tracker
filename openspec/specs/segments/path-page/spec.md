# path-page Specification

## Purpose

The Trail page: one trail or road with every segment on it, so an athlete who found a place in Trails & roads can see its segments and open one.

## Requirements

### Requirement: The Trail page names its path
The Trail page SHALL show the path's name, whether it is a trail or a road, its length in the athlete's units, the sport groups of its listed segments with the one-way wording a trail row gives them (`segments/segment-browse`), and its data credit as segment pages credit a line's source.

#### Scenario: OSM trail
- **WHEN** an athlete opens Ann's, an OSM trail of 5.4 mi with ride, run and hike segments
- **THEN** the page reads "Ann's", Trail, 5.40 mi, Ride, Run, Hike, and credits OpenStreetMap contributors

#### Scenario: Downhill-only trail
- **WHEN** an athlete opens Rush, whose ride segments go one way only, downhill
- **THEN** the line under its name reads "Trail · 4.70 mi · Ride · Downhill only"

### Requirement: The Trail page lists its segments by segment group
The Trail page SHALL list the segments on the path that the viewer may see, one row per segment group (`segments/segment-groups`), each named and described as a segment group's row is. Rows SHALL run from the longest to the shortest, then by name.

#### Scenario: Whole trail and stretches
- **WHEN** Ann's holds its whole line and two stretches, each for three sports both ways
- **THEN** the page lists three rows, "Ann's" first

#### Scenario: Combined segment
- **WHEN** an athlete's segment covers part of Ann's and part of Hogback
- **THEN** it is listed on both trails' pages

#### Scenario: One-way for one sport
- **WHEN** the Clarks trail holds, on one line, a one-way ride segment that clearly climbs and run and hike segments both ways, all named "Clarks"
- **THEN** it lists one row, "Clarks", whose details read "<length> · Run, Hike both ways · Ride uphill only"

#### Scenario: Neighbouring trails' imported segments
- **WHEN** the importer's Ann's stretches and its Bonneville Shoreline segment share only part of their length with Clarks
- **THEN** none of them is listed on the Clarks page

#### Scenario: Rows inside a section
- **WHEN** the Rush page folds three rows into its Through the Trees Trail section
- **THEN** those rows run from the longest to the shortest inside the section, and the section takes its place in the list by its longest row

### Requirement: The Trail page keeps the browse sport filter
When opened from browse with one sport group chosen, the Trail page SHALL open listing only that group's segments, with that group chosen in its sport chips (`segments/path-filters`), from which any other sport group or All MAY be chosen.

#### Scenario: Opened with Ride
- **WHEN** an athlete with Ride chosen opens Ann's
- **THEN** the page lists Ann's ride segments only, with Ride chosen in the sport chips

#### Scenario: Showing all sports
- **WHEN** that athlete chooses All
- **THEN** the page lists Ann's segment groups for every sport

### Requirement: The Trail page draws its path and segments
Where the segment page draws a map, the Trail page SHALL draw the path's line framed whole, muted, with its listed segments' lines over it, each in its rating level's colour for the page's sport group (`segments/trail-ratings`), and the legend of the levels it draws.

#### Scenario: Opening Ann's
- **WHEN** an athlete opens Ann's with three segment groups
- **THEN** the map shows all of Ann's line with the three groups' lines drawn over it

#### Scenario: Rated stretches
- **WHEN** an athlete opens Ann's from browse with Ride chosen, and its two stretches are IMBA Easy and Intermediate
- **THEN** the stretches are drawn green and blue over the muted path, and the legend lists Easy and Intermediate

### Requirement: The Trail page's map and list share a selection
As in browse: tapping a segment's line SHALL select its row, drawn highlighted and its row scrolled to and marked. On a touch screen, tapping an unselected row SHALL select it, and tapping the selected row SHALL open it as `segments/segment-groups` says. With a pointer, clicking a row SHALL open it. Tapping away from every line SHALL clear the selection. Back SHALL return to the page as it was.

#### Scenario: Tapping a row on a phone
- **WHEN** an athlete on Android taps the unselected "Lupine Hiking ↔ Potato Hill" row on Ann's
- **THEN** that stretch's line is highlighted, its row is marked, and no page opens

#### Scenario: Tapping a line
- **WHEN** an athlete taps a stretch's line on Ann's map
- **THEN** the line is highlighted and its row is scrolled into view and marked

#### Scenario: Opening a stretch
- **WHEN** the athlete taps the selected row
- **THEN** its segment page opens with the sport and direction switcher, and back returns to Ann's with the row still selected

#### Scenario: Clicking a row on desktop
- **WHEN** an athlete in the desktop app clicks a row on Ann's
- **THEN** its segment page opens

### Requirement: Hovering on the Trail page shows a segment on the map and in the list
With a pointer, while the pointer is over a row the Trail page's map SHALL draw its line highlighted, and while it is over a segment's line its row SHALL be marked. Hovering SHALL NOT scroll the list, move the map or change the selection, and SHALL end when the pointer leaves.

#### Scenario: Hovering a row
- **WHEN** an athlete in the desktop app moves the pointer over a stretch's row on Ann's
- **THEN** that stretch's line is highlighted until the pointer leaves the row

#### Scenario: Hovering a line
- **WHEN** the pointer is over a stretch's line on the map
- **THEN** its row is marked if in view, and the list doesn't scroll

### Requirement: The Trail page's map shows a selected row's line
When a row is selected from the list and its line isn't wholly in view, the Trail page's map SHALL move to show it whole. Selecting a line on the map SHALL NOT move the map.

#### Scenario: Selecting a stretch off screen
- **WHEN** an athlete zoomed in at one end of a long trail selects a stretch at the other end
- **THEN** the map moves to show that stretch's whole line, highlighted

### Requirement: The Trail page loads as content
The Trail page SHALL load, fail and retry as `ui/content-loading` says, and SHALL say when the path has no segments the athlete may see.

#### Scenario: Offline
- **WHEN** an athlete opens a trail while offline
- **THEN** the page says the server couldn't be reached and offers to try again

#### Scenario: Nothing visible
- **WHEN** every segment on a road is proposed by someone else
- **THEN** the page says there are no segments here yet

### Requirement: The Trail page is native on iOS
On iOS the Trail page SHALL be drawn natively with the system's navigation bar, the native map and a native list, in the shared page's words, and SHALL open on the tab it was opened from. Its map and list SHALL share the selection as the native browse map does: a tap selects the segment nearest within a finger's width.

#### Scenario: Opening a trail on iOS
- **WHEN** alice opens Ann's from Trails & roads on her iPhone
- **THEN** the native Trail page shows Ann's map and rows, and back returns to Trails & roads as she left it

#### Scenario: Selecting on the native map
- **WHEN** alice taps a stretch's line on the native Trail page's map
- **THEN** the stretch is highlighted and marked in the list, scrolled into view

### Requirement: The Trail page folds a section's rows together
When at least two of the page's segment-group rows are in one of the path's sections (`segments/segment-paths`), the Trail page SHALL list them under one collapsible section row, named after the section, with the length of its longest row and the sport groups and direction wording of its rows, as a segment group's row words them. A section with only one listed row SHALL be listed as that plain row. A row is in the section that its shown segment is in.

#### Scenario: Through the Trees
- **WHEN** the Rush page lists "Rush › Through the Trees Trail" (4.16 mi), its spans to Bonneville Shoreline and Canyon Hollow, and "Rush - Through the Trees Trail" (1.07 mi), all in the Through the Trees Trail section
- **THEN** it lists one section row, "Through the Trees Trail", 4.16 mi, holding those four rows

#### Scenario: Rows in no section
- **WHEN** "Rush → Bonneville Shoreline" and an athlete's own segment on Rush are in no section
- **THEN** each is listed as a plain row among the sections, by length

#### Scenario: One row left after the sport filter
- **WHEN** the Rush page lists only ride segments, and only one Limelight row is ridden
- **THEN** Limelight is listed as a plain row

### Requirement: Sections open expanded and can all be collapsed
Each section SHALL be shown expanded when the page loads, and tapping its section row SHALL collapse or expand it. When the page lists two or more sections, it SHALL offer one control that expands every section, or collapses every one, depending on the sections' state. A section SHALL keep its state while the page stays open, including after returning from a segment page.

#### Scenario: Collapsing all
- **WHEN** an athlete on the Rush page, with its three sections expanded, chooses collapse all
- **THEN** the page lists the three section rows and the plain rows, and the control now offers to expand all

#### Scenario: One section
- **WHEN** a trail page lists one section and plain rows
- **THEN** it offers no expand-all or collapse-all control

### Requirement: Selection reaches into collapsed sections
Selecting a segment's line on the map SHALL expand a collapsed section holding its row before the row is scrolled to and marked. With a pointer, hovering a section row SHALL highlight the lines of every row in it.

#### Scenario: Tapping a hidden row's line
- **WHEN** the Exit Stage Left section is collapsed and an athlete taps the "Rush › Exit Stage Left" line on the map
- **THEN** the section expands, and the row is scrolled into view and marked

#### Scenario: Hovering a section
- **WHEN** an athlete in the desktop app moves the pointer over the Through the Trees Trail section row
- **THEN** the map highlights the lines of its four rows until the pointer leaves

### Requirement: The native Trail page folds sections
On iOS the native Trail page SHALL show sections with the system's own disclosure controls, expanded when it loads, with the same expand-all and collapse-all control and selection behaviour as the shared page, in its words.

#### Scenario: Collapsing on iOS
- **WHEN** alice taps the disclosure of Through the Trees Trail on the native Rush page
- **THEN** its rows are hidden, and tapping the line of one of them on the map shows them again with that row marked
