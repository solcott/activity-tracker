# segments/segment-browse Specification

## Purpose

Lets a signed-in athlete discover segments: browse the ones around a place on a map and in a list, and find one by name, then open its segment page.

## Requirements

### Requirement: Segments mode in the Search tab
The Search tab SHALL offer a switch between Athletes, Segments and Dive sites. Athletes SHALL be the athlete search it shows today, and SHALL be selected the first time the tab opens. Segments SHALL show the segment browse screen, and Dive sites the dive site browse screen (`dive-sites/site-browse`). The tab SHALL keep the chosen mode, and each mode's query and results, while the athlete visits other tabs and comes back.

#### Scenario: Switching to segments
- **WHEN** an athlete on the Search tab chooses Segments
- **THEN** the tab shows the segment browse screen in place of athlete search

#### Scenario: Coming back to the tab
- **WHEN** an athlete in Segments mode goes to the Feed tab and back to Search
- **THEN** Segments mode is still selected, with the same map area, query and results

### Requirement: Browse shows segments on a map and in a list
On Android, iOS and desktop, the browse screen SHALL show a map that the athlete can pan and zoom, north-up, and a list. With no name query, the map SHALL draw every segment whose line crosses the visible area, and the list SHALL list the runs among them, nearest the map's centre first, each with its name, sport group and length. Both SHALL follow the area as the athlete moves the map.

#### Scenario: Moving the map
- **WHEN** an athlete pans the map to a valley with three ride segments
- **THEN** the map draws the three lines and the list lists them, nearest the map's centre first

#### Scenario: Proposed segment someone else made
- **WHEN** the visible area holds a proposed segment the athlete didn't create and has no effort on
- **THEN** neither the map nor the list shows it

### Requirement: Browse opens at the athlete's location
On Android and iOS, the browse screen SHALL first centre the map on the athlete's current location, asking for location permission if it isn't granted. When no location is available, because permission is refused, no fix arrives, or the platform has no location (desktop), it SHALL centre on the start of the athlete's latest activity with a route, and failing that, show a prompt to move the map or search by name. Coming back to the screen SHALL keep the area the athlete last looked at.

#### Scenario: Permission granted
- **WHEN** an athlete with location permission opens Segments mode for the first time
- **THEN** the map centres on their location and lists the segments around it

#### Scenario: Permission refused
- **WHEN** an athlete refuses location permission and their latest activity started in Stowe
- **THEN** the map centres on Stowe

#### Scenario: Desktop
- **WHEN** an athlete opens Segments mode in the desktop app
- **THEN** no location is asked for and the map centres on their latest activity's start

### Requirement: Browse needs a close enough view
When the visible area is wider than 1 degree on either side, the browse screen SHALL draw no segments, list none, and say to zoom in to see segments. It SHALL NOT ask the AppView for that area.

#### Scenario: Zoomed out to a state
- **WHEN** an athlete zooms the map out until it shows most of Vermont
- **THEN** no lines are drawn and the list says to zoom in to see segments

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

### Requirement: Lifts appear only on the map
The browse map SHALL draw ski lifts in the visible area in a muted style, distinct from runs, so a resort's layout is recognisable. Lifts SHALL never be listed, selected or found by name.

#### Scenario: Resort area
- **WHEN** an athlete browses a ski resort with five pistes and two chairlifts
- **THEN** the map draws all seven lines, the lifts muted, and the list holds only the five pistes

### Requirement: Segments can be found by name
The browse screen SHALL have a search box. A non-blank query SHALL list the segments whose current name matches it, best match first and then nearest the map's centre, a page at a time, and the map SHALL draw the matches in the visible area. Clearing the query SHALL return to browsing the area.

#### Scenario: Searching a name
- **WHEN** an athlete types "hogback" with the map over Hogback Mountain
- **THEN** the list shows "Hogback Climb" above "Hogback Connector", nearest first among equally good matches

#### Scenario: Renamed segment
- **WHEN** a segment's adopted name is "Wiggly Woods" and its original name is "Trail 214"
- **THEN** searching "wiggly" finds it and searching "214" doesn't

### Requirement: Name search has a scope
Where the map is shown, the athlete SHALL choose whether a name search covers This area, only segments crossing the visible area, or Everywhere. This area SHALL be the default. When This area finds nothing, the screen SHALL offer to search Everywhere. Opening a result found Everywhere SHALL open its segment page. The map SHALL NOT move by itself as results load; it SHALL move only to show a selected row's line, as "The map shows a selected row's line" says.

#### Scenario: Nothing in this area
- **WHEN** an athlete searches "kingdom trails" in This area over Burlington
- **THEN** the list says nothing matches in this area and offers to search everywhere

#### Scenario: Everywhere
- **WHEN** the athlete switches the scope to Everywhere
- **THEN** the list shows matching segments wherever they are, nearest the map's centre first, and the map stays where it was

### Requirement: Browse can be limited to one sport group
The browse screen SHALL let the athlete choose one sport group or all. The choice SHALL apply to the map, the list and name results, and SHALL be kept while the screen is kept.

#### Scenario: Only runs
- **WHEN** an athlete chooses the run sport group over a park with ride and run segments
- **THEN** only the run segments are drawn and listed

### Requirement: Web lists segments without a map
The web app SHALL show the browse screen as the search box, the sport filter and the name results list only, with no map, no area browsing and no scope choice: every name search SHALL cover Everywhere, ordered by match alone. A blank query SHALL show a prompt to search by name.

#### Scenario: Searching on web
- **WHEN** an athlete on the web app types "hogback" in Segments mode
- **THEN** matching segments from anywhere are listed, and tapping one opens its segment page

#### Scenario: Blank query on web
- **WHEN** an athlete on the web app opens Segments mode
- **THEN** no map is shown and the screen asks them to search by name

### Requirement: Browse shows why segments couldn't load
When the segments for an area or a name query can't be loaded, the browse screen SHALL keep any segments it was showing, say why through the app's error messages, and offer to try again.

#### Scenario: Offline while panning
- **WHEN** an athlete pans the map while the device is offline
- **THEN** the lines already drawn stay, and the list says it couldn't load segments because the server couldn't be reached, with a way to try again

### Requirement: The Search tab is native on iOS
On iOS the Search tab's root SHALL be drawn with the system's navigation bar under a large title ("Find athletes" in Athletes mode,
"Search" in Segments and Dive sites modes), with Athletes, Segments and Dive sites as a segmented choice under the bar.

#### Scenario: Switching mode
- **WHEN** alice chooses Segments on the native Search tab
- **THEN** the title reads "Search" and the choice shows Segments

### Requirement: Native athlete search uses the system's search field
On iOS, Athletes mode SHALL search with the system's search field and list the matching accounts with their picture, name and handle,
each opening its profile, loaded page by page as `ui/content-loading` says. It SHALL show the prompt while the query is blank and say
that nothing matched when nothing did.

#### Scenario: Finding an athlete
- **WHEN** alice types "bo" into the native Search tab's search field
- **THEN** once typing pauses it lists Bob with his picture, name and @handle, and tapping him opens his profile on the Search tab

#### Scenario: Nothing matches
- **WHEN** alice searches "zzz" and no athlete matches
- **THEN** the native Search tab says No athletes match “zzz”.

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

### Requirement: The native browse map shares the selection
On iOS a tap on the browse map SHALL select the run nearest the tap within a finger's width, mark it in the list and bring it into view,
and a tap away from every run SHALL clear the choice. Each time the map stops it SHALL report what it shows, and each new place the
browse screen moves it to SHALL move it once.

#### Scenario: Selecting on the map
- **WHEN** alice taps on Ridge Trail's line on the native map
- **THEN** Ridge Trail is highlighted on the map and marked in the list, scrolled into view
