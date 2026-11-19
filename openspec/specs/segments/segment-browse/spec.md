# segments/segment-browse Specification

## Purpose

Lets a signed-in athlete discover segments: browse the ones around a place on a map and in a list, and find one by name, then open its segment page.

## Requirements

### Requirement: Trails & roads mode in the Search tab
The Search tab SHALL offer a switch between Athletes, Trails & roads and Dive sites. Athletes SHALL be the athlete search it shows today, and SHALL be selected the first time the tab opens. Trails & roads SHALL show the browse screen, and Dive sites the dive site browse screen (`dive-sites/site-browse`). The tab SHALL keep the chosen mode, and each mode's query and results, while the athlete visits other tabs and comes back.

#### Scenario: Switching to segments
- **WHEN** an athlete on the Search tab chooses Trails & roads
- **THEN** the tab shows the browse screen in place of athlete search

#### Scenario: Coming back to the tab
- **WHEN** an athlete in Trails & roads mode goes to the Feed tab and back to Search
- **THEN** Trails & roads mode is still selected, with the same map area, query and results

### Requirement: Browse shows trails and roads on a map and in a list
On Android, iOS and desktop, the browse screen SHALL show a pannable, north-up map and a list. With no name query, both SHALL show each path (`segments/segment-paths`) with a listed segment crossing the visible area, and each segment group crossing it that lies on no path, one line and one row each, nearest the map's centre first. Both SHALL follow the area as the map moves.

#### Scenario: Moving the map
- **WHEN** an athlete pans the map to a valley with segments on three different trails
- **THEN** the map draws the three trails and the list lists them, nearest the map's centre first

#### Scenario: One trail, several segments
- **WHEN** the visible area holds Ann's whole line and two of its stretches, each as ride, run and hike segments in both directions
- **THEN** the map draws one line for Ann's and the list has one row, "Ann's", reading Trail · 5.40 mi · Ride, Run, Hike · 3 segments

#### Scenario: Segment on no path
- **WHEN** the visible area holds an athlete's segment along a street with no path yet
- **THEN** the list shows it as a segment group's row of its own

#### Scenario: Proposed segment someone else made
- **WHEN** the visible area holds a proposed segment the athlete didn't create and has no effort on
- **THEN** neither the map nor the list shows it, nor a path it alone lies on

### Requirement: Browse opens at the athlete's location
On Android and iOS, the browse screen SHALL first centre the map on the athlete's current location, asking for location permission if it isn't granted. When no location is available, because permission is refused, no fix arrives, or the platform has no location (desktop), it SHALL centre on the start of the athlete's latest activity with a route, and failing that, show a prompt to move the map or search by name. Coming back to the screen SHALL keep the area the athlete last looked at.

#### Scenario: Permission granted
- **WHEN** an athlete with location permission opens Trails & roads mode for the first time
- **THEN** the map centres on their location and lists the trails and roads around it

#### Scenario: Permission refused
- **WHEN** an athlete refuses location permission and their latest activity started in Stowe
- **THEN** the map centres on Stowe

#### Scenario: Desktop
- **WHEN** an athlete opens Trails & roads mode in the desktop app
- **THEN** no location is asked for and the map centres on their latest activity's start

### Requirement: Browse needs a close enough view
When the visible area is wider than 1 degree on either side, the browse screen SHALL draw no lines, list nothing, and say to zoom in to see trails and roads. It SHALL NOT ask the AppView for that area.

#### Scenario: Zoomed out to a state
- **WHEN** an athlete zooms the map out until it shows most of Vermont
- **THEN** no lines are drawn and the list says to zoom in to see trails and roads

### Requirement: Map and list share a selection
Tapping a line on the map SHALL select its row: the line drawn highlighted, the row scrolled to and marked. On a touch screen, tapping an unselected row SHALL select it the same way, and tapping the selected row SHALL open it. With a pointer, clicking a row SHALL open it. Opening a path's row SHALL open its Trail page (`segments/path-page`); opening a segment group's row SHALL open it as `segments/segment-groups` says. Tapping away from every line SHALL clear the selection.

#### Scenario: Tapping a line
- **WHEN** an athlete taps Ann's line on the map
- **THEN** the line is highlighted and the list shows Ann's row marked

#### Scenario: Tapping a row on a phone
- **WHEN** an athlete on Android taps Ridge Trail's row, which isn't selected
- **THEN** Ridge Trail's line is highlighted on the map, its row is marked, and no page opens

#### Scenario: Opening a trail
- **WHEN** an athlete on a phone taps the selected row of Ann's
- **THEN** Ann's Trail page opens, and back returns to the browse screen as it was

#### Scenario: Opening a segment
- **WHEN** an athlete on a phone taps the selected row of a segment group on no path
- **THEN** one of its segments' pages opens

#### Scenario: Clicking a row on desktop
- **WHEN** an athlete in the desktop app clicks a trail's row
- **THEN** its Trail page opens

### Requirement: Hovering shows a row on the map and in the list
With a pointer (the desktop app, or Android with a mouse), while the pointer is over a row the browse map SHALL draw its line highlighted as a selected line is drawn. While the pointer is over a path's or segment group's line on the map, its row SHALL be marked. Hovering SHALL NOT scroll the list, move the map or change the selection, and SHALL end when the pointer leaves.

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

### Requirement: Trails and roads can be found by name
The browse screen SHALL have a search box. A non-blank query SHALL list, a page at a time, the paths and the segment groups on no path that `feed.searchPathNames` finds, in its order, one row each. The map SHALL draw those in the visible area, one line each. Clearing the query SHALL return to browsing the area.

#### Scenario: Searching a name
- **WHEN** an athlete types "hogback" with the map over Hogback Mountain
- **THEN** the list shows the Hogback trail above "Hogback Connector", nearest first among equally good matches

#### Scenario: Renamed segment
- **WHEN** a segment on Hogback has adopted the name "Wiggly Woods" and its original name is "Trail 214"
- **THEN** searching "wiggly" lists Hogback and searching "214" doesn't

#### Scenario: Several segments of one trail
- **WHEN** an athlete searches "ann's" and Ann's has its whole line and two stretches, each for three sports both ways, plus a 2.1 mi Ann's elsewhere
- **THEN** the list shows one row for each of the two trails

### Requirement: Name search has a scope
Where the map is shown, the athlete SHALL choose whether a name search covers This area, only rows with a segment crossing the visible area, or Everywhere. This area SHALL be the default. When This area finds nothing, the screen SHALL offer to search Everywhere. Opening a result found Everywhere SHALL open it as a row in the area opens. The map SHALL move only to show a selected row's line, as "The map shows a selected row's line" says.

#### Scenario: Nothing in this area
- **WHEN** an athlete searches "kingdom trails" in This area over Burlington
- **THEN** the list says nothing matches in this area and offers to search everywhere

#### Scenario: Everywhere
- **WHEN** the athlete switches the scope to Everywhere
- **THEN** the list shows matching trails and roads wherever they are, nearest the map's centre first, and the map stays where it was

### Requirement: Browse can be limited to one sport group
The browse screen SHALL let the athlete choose one sport group or all. The choice SHALL apply to the map, the list and name results, which SHALL show only paths and segment groups with a segment of that group, and SHALL be kept while the screen is kept. A row SHALL describe only that group's segments, and the Trail page SHALL open with that group chosen.

#### Scenario: Only runs
- **WHEN** an athlete chooses the run sport group over a park with ride-only and run trails
- **THEN** only the run trails are drawn and listed

#### Scenario: One group, both directions
- **WHEN** an athlete chooses Ride and Ann's has ride segments in 3 segment groups
- **THEN** the list has one "Ann's" row reading Ride · 3 segments

### Requirement: Web lists trails and roads without a map
The web app SHALL show the browse screen as the search box, the sport filter and the name results list only, with no map, no area browsing and no scope choice: every name search SHALL cover Everywhere, ordered by match alone. A blank query SHALL show a prompt to search by name.

#### Scenario: Searching on web
- **WHEN** an athlete on the web app types "hogback" in Trails & roads mode
- **THEN** matching trails and roads from anywhere are listed, and tapping a trail opens its Trail page

#### Scenario: Blank query on web
- **WHEN** an athlete on the web app opens Trails & roads mode
- **THEN** no map is shown and the screen asks them to search by name

### Requirement: Browse shows why trails and roads couldn't load
When the rows for an area or a name query can't be loaded, the browse screen SHALL keep any rows it was showing, say why through the app's error messages, and offer to try again.

#### Scenario: Offline while panning
- **WHEN** an athlete pans the map while the device is offline
- **THEN** the lines already drawn stay, and the list says it couldn't load because the server couldn't be reached, with a way to try again

### Requirement: The Search tab is native on iOS
On iOS the Search tab's root SHALL be drawn with the system's navigation bar under a large title ("Find athletes" in Athletes mode,
"Search" in Trails & roads and Dive sites modes), with Athletes, Trails & roads and Dive sites as a segmented choice under the bar.

#### Scenario: Switching mode
- **WHEN** alice chooses Trails & roads on the native Search tab
- **THEN** the title reads "Search" and the choice shows Trails & roads

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

### Requirement: Trails & roads browse is native on iOS
On iOS the Trails & roads mode SHALL show browse natively under the mode choice: the search box, the This area and Everywhere choice while a name is searched in an area, the sport chips, the native map, and the list, beside the map on a wide window and under it on a phone. Its rows and messages SHALL read as the shared page's. What it opens SHALL open on the Search tab, keeping its query, map and list.

#### Scenario: Browsing on an iPad
- **WHEN** alice chooses Trails & roads on an iPad
- **THEN** the list shows beside the native map, each trail or road with its name, kind, length, sport groups and how far away it is

#### Scenario: One row per trail
- **WHEN** alice searches "ann's" and Ann's has its whole line and two stretches for ride, run and hike
- **THEN** the native list shows one "Ann's" row reading Trail · 5.40 mi · Ride, Run, Hike · 3 segments

#### Scenario: Selecting a run in the list
- **WHEN** alice taps Ridge Trail in the native list, which isn't selected
- **THEN** Ridge Trail's line is highlighted on the native map and its row is marked

#### Scenario: Opening a trail
- **WHEN** alice taps the selected Ridge Trail row in the native list
- **THEN** its native Trail page opens on the Search tab, and back returns to Trails & roads with the map and list as she left them

#### Scenario: Opening a segment
- **WHEN** alice taps the selected row of a segment group on no path in the native list
- **THEN** its segment page opens on the Search tab

#### Scenario: Nothing matches here
- **WHEN** alice searches "zzz" in This area and nothing matches
- **THEN** the native list says so and offers Search everywhere

### Requirement: The native browse map shares the selection
On iOS a tap on the browse map SHALL select the row whose line is nearest the tap within a finger's width, mark it in the list and bring it into view,
and a tap away from every line SHALL clear the choice. Each time the map stops it SHALL report what it shows, and each new place the
browse screen moves it to SHALL move it once.

#### Scenario: Selecting on the map
- **WHEN** alice taps on Ridge Trail's line on the native map
- **THEN** Ridge Trail is highlighted on the map and marked in the list, scrolled into view

### Requirement: A trail or road row says which sport groups go one way
A trail or road's row SHALL say, after its sport groups, which of them go one way only and which way, as a path view gives it (`segments/segment-paths`), in the wording segment group rows use: once after the sports when every listed sport group goes the same one way, otherwise after each one-way sport group, with the others listed first and plain. When the list is narrowed to one sport group, the row SHALL say only that group's way.

#### Scenario: Downhill-only ride trail
- **WHEN** the name search "rush" finds the Rush trail, whose ride segments go one way only, downhill
- **THEN** its row reads "Trail · 4.70 mi · Ride · Downhill only · 21 segments"

#### Scenario: Mixed trail
- **WHEN** a row lists Clarks, whose ride segments go one way only, uphill, and whose run and hike segments go both ways
- **THEN** its row reads "Trail · 1.42 mi · Run, Hike · Ride uphill only · 3 segments"

#### Scenario: Two-way trail
- **WHEN** a row lists Ann's, with no one-way sport group
- **THEN** its row reads as before, "Trail · 5.40 mi · Ride, Run, Hike · 3 segments"
