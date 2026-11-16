# Spec Delta

## MODIFIED Requirements

### Requirement: Browse shows segments on a map and in a list
On Android, iOS and desktop, the browse screen SHALL show a map that the athlete can pan and zoom, north-up, and a list. With no name query, the map SHALL draw one line for each route (`segments/segment-routes`) that has a run whose line crosses the visible area. The list SHALL list those routes as one row each, nearest the map's centre first, each named and described as a route's row is. Both SHALL follow the area as the athlete moves the map.

#### Scenario: Moving the map
- **WHEN** an athlete pans the map to a valley with three ride segments on different trails
- **THEN** the map draws the three lines and the list lists them, nearest the map's centre first

#### Scenario: One trail, several segments
- **WHEN** the visible area holds Ann's as ride, run and hike segments in both directions
- **THEN** the map draws one line for Ann's and the list has one row, "Ann's", reading Ride, Run, Hike · Both ways

#### Scenario: Proposed segment someone else made
- **WHEN** the visible area holds a proposed segment the athlete didn't create and has no effort on
- **THEN** neither the map nor the list shows it

### Requirement: Map and list share a selection
Tapping a route's line on the map SHALL select the route: its line SHALL be drawn highlighted and its row SHALL be scrolled to and marked. On a touch screen (Android and iOS), tapping a row that isn't selected SHALL select its route the same way. Tapping the selected row SHALL open the route as "Opening a route opens one of its members" says. With a pointer (desktop), clicking a row SHALL open it that way. Tapping the map away from any line SHALL clear the selection.

#### Scenario: Tapping a line
- **WHEN** an athlete taps a segment's line on the map
- **THEN** the line is highlighted and the list shows its route's row marked

#### Scenario: Tapping a row on a phone
- **WHEN** an athlete on Android taps Ridge Trail's row, which isn't selected
- **THEN** Ridge Trail's line is highlighted on the map, its row is marked, and no page opens

#### Scenario: Opening a segment
- **WHEN** an athlete on a phone taps the selected route's row
- **THEN** one of its segments' pages opens, and back returns to the browse screen as it was

#### Scenario: Clicking a row on desktop
- **WHEN** an athlete in the desktop app clicks a route's row
- **THEN** one of its segments' pages opens

### Requirement: Hovering shows a segment on the map and in the list
With a pointer (the desktop app, or Android with a mouse), while the pointer is over a route's row the browse map SHALL draw its line highlighted as a selected line is drawn. While the pointer is over a run's line on the map, its route's row SHALL be marked. Hovering SHALL NOT scroll the list, move the map or change the selection, and SHALL end when the pointer leaves.

#### Scenario: Hovering a row
- **WHEN** an athlete in the desktop app moves the pointer over Ridge Trail's row
- **THEN** Ridge Trail's line is highlighted on the map until the pointer leaves the row

#### Scenario: Hovering a line
- **WHEN** an athlete in the desktop app moves the pointer over Ridge Trail's line on the map
- **THEN** Ridge Trail's row is marked if it is in view, and the list doesn't scroll

#### Scenario: Hovering a lift
- **WHEN** the pointer is over a ski lift's line
- **THEN** no row is marked

### Requirement: Segments can be found by name
The browse screen SHALL have a search box. A non-blank query SHALL list, a page at a time, the routes with a segment whose current name matches it, best match first and then nearest the map's centre, one row per route. The map SHALL draw the matching routes in the visible area, one line each. Clearing the query SHALL return to browsing the area.

#### Scenario: Searching a name
- **WHEN** an athlete types "hogback" with the map over Hogback Mountain
- **THEN** the list shows "Hogback Climb" above "Hogback Connector", nearest first among equally good matches

#### Scenario: Renamed segment
- **WHEN** a segment's adopted name is "Wiggly Woods" and its original name is "Trail 214"
- **THEN** searching "wiggly" finds it and searching "214" doesn't

#### Scenario: Several segments of one trail
- **WHEN** an athlete searches "ann's" and Ann's has ride, run and hike segments each way, plus a 2.1 mi Ann's on another trail
- **THEN** the list shows one "Ann's" row for each of the two trails

### Requirement: Browse can be limited to one sport group
The browse screen SHALL let the athlete choose one sport group or all. The choice SHALL apply to the map, the list and name results, and SHALL be kept while the screen is kept. With one group chosen, a route's row SHALL list only that group's segments, in both directions when it has them.

#### Scenario: Only runs
- **WHEN** an athlete chooses the run sport group over a park with ride and run segments
- **THEN** only the run segments are drawn and listed

#### Scenario: One group, both directions
- **WHEN** an athlete chooses Ride and Ann's has ride segments each way
- **THEN** the list has one "Ann's" row reading Ride · Both ways

### Requirement: Segment browse is native on iOS
On iOS the Search tab's Segments mode SHALL show segment browse natively under the mode choice: the search box, the This area and Everywhere choice while a name is searched in an area, the sport chips, the native map, and the list, beside the map on a wide window and under it on a phone. The list SHALL show one row per route, its rows and messages reading as the shared page's. What it opens SHALL open on the Search tab, keeping its query, map and list while the tab's root stays.

#### Scenario: Browsing on an iPad
- **WHEN** alice chooses Segments on an iPad
- **THEN** the list shows beside the native map, each route with its name, length, types and how far away it is

#### Scenario: One row per trail
- **WHEN** alice searches "ann's" and Ann's has ride, run and hike segments each way
- **THEN** the native list shows one "Ann's" row reading Ride, Run, Hike · Both ways

#### Scenario: Selecting a run in the list
- **WHEN** alice taps Ridge Trail in the native list, which isn't selected
- **THEN** Ridge Trail's line is highlighted on the native map and its row is marked

#### Scenario: Opening a segment
- **WHEN** alice taps the selected run in the native list
- **THEN** its page opens on the Search tab, and back returns to Segments mode with the map and list as she left them

#### Scenario: Nothing matches here
- **WHEN** alice searches "zzz" in This area and nothing matches
- **THEN** the native list says so and offers Search everywhere
