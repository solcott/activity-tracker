# Spec Delta

## MODIFIED Requirements

### Requirement: Map and list share a selection
Tapping a segment's line on the map SHALL select it: its line SHALL be drawn highlighted and its row SHALL be scrolled to and marked. On a touch screen (Android and iOS), tapping a row that isn't selected SHALL select it the same way, and tapping the selected row SHALL open that segment's page. With a pointer (desktop), clicking a row SHALL open that segment's page. Tapping the map away from any line SHALL clear the selection.

#### Scenario: Tapping a line
- **WHEN** an athlete taps a segment's line on the map
- **THEN** the line is highlighted and the list shows that segment's row marked

#### Scenario: Tapping a row on a phone
- **WHEN** an athlete on Android taps Ridge Trail's row, which isn't selected
- **THEN** Ridge Trail's line is highlighted on the map, its row is marked, and no page opens

#### Scenario: Opening a segment
- **WHEN** an athlete on a phone taps the selected segment's row
- **THEN** the segment page opens, and back returns to the browse screen as it was

#### Scenario: Clicking a row on desktop
- **WHEN** an athlete in the desktop app clicks a segment's row
- **THEN** the segment page opens

### Requirement: Name search has a scope
Where the map is shown, the athlete SHALL choose whether a name search covers This area, only segments crossing the visible area, or Everywhere. This area SHALL be the default. When This area finds nothing, the screen SHALL offer to search Everywhere. Opening a result found Everywhere SHALL open its segment page. The map SHALL NOT move by itself as results load; it SHALL move only to show a selected row's line, as "The map shows a selected row's line" says.

#### Scenario: Nothing in this area
- **WHEN** an athlete searches "kingdom trails" in This area over Burlington
- **THEN** the list says nothing matches in this area and offers to search everywhere

#### Scenario: Everywhere
- **WHEN** the athlete switches the scope to Everywhere
- **THEN** the list shows matching segments wherever they are, nearest the map's centre first, and the map stays where it was

### Requirement: Segment browse is native on iOS
On iOS the Search tab's Segments mode SHALL show segment browse natively under the mode choice: the search box, the This area and
Everywhere choice while a name is searched in an area, the sport chips, the segments on the native map, and the list, beside the map on a
wide window and under it on a phone. The list's rows and messages SHALL read as the shared page's, and what it opens SHALL open on the
Search tab, keeping its query, map and list while the tab's root stays.

#### Scenario: Browsing on an iPad
- **WHEN** alice chooses Segments on an iPad
- **THEN** the list shows beside the native map, each run with its name, length, type and how far away it is

#### Scenario: Selecting a run in the list
- **WHEN** alice taps Ridge Trail in the native list, which isn't selected
- **THEN** Ridge Trail's line is highlighted on the native map and its row is marked

#### Scenario: Opening a segment
- **WHEN** alice taps the selected run in the native list
- **THEN** its page opens on the Search tab, and back returns to Segments mode with the map and list as she left them

#### Scenario: Nothing matches here
- **WHEN** alice searches "zzz" in This area and nothing matches
- **THEN** the native list says so and offers Search everywhere

## ADDED Requirements

### Requirement: Hovering shows a segment on the map and in the list
With a pointer (the desktop app, or Android with a mouse), while the pointer is over a segment's row the browse map SHALL draw its line highlighted as a selected line is drawn, and while it is over a run's line on the map that run's row SHALL be marked. Hovering SHALL NOT scroll the list, move the map or change the selection, and SHALL end when the pointer leaves.

#### Scenario: Hovering a row
- **WHEN** an athlete in the desktop app moves the pointer over Ridge Trail's row
- **THEN** Ridge Trail's line is highlighted on the map until the pointer leaves the row

#### Scenario: Hovering a line
- **WHEN** an athlete in the desktop app moves the pointer over Ridge Trail's line on the map
- **THEN** Ridge Trail's row is marked if it is in view, and the list doesn't scroll

#### Scenario: Hovering a lift
- **WHEN** the pointer is over a ski lift's line
- **THEN** no row is marked

### Requirement: The map shows a selected row's line
When a row is selected from the list and no part of its line lies in the visible area, the browse map SHALL move to show the whole line. Selecting a row whose line already crosses the visible area, or selecting a line on the map, SHALL NOT move the map. Moving the map this way SHALL NOT restart the name search or change its results.

#### Scenario: Selecting a result found Everywhere
- **WHEN** an athlete on a phone over Burlington taps a Kingdom Trails result found Everywhere
- **THEN** the map moves to show that segment's whole line, highlighted, and the list keeps its results and order

#### Scenario: Selecting a row in the area
- **WHEN** an athlete browsing an area with no name query taps a run's row
- **THEN** the map stays where it is and the run's line is highlighted
