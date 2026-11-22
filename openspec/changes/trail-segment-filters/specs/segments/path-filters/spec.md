## Purpose

Lets an athlete narrow and order a Trail page's segment-group rows (`segments/path-page`) by name, sport, difficulty, direction and length, with the map following what the list shows.

## ADDED Requirements

### Requirement: The Trail page searches its rows by name
When the page lists more than three segment-group rows, before any filter, the Trail page SHALL offer a search field above its list. A row SHALL match when the query, ignoring case and accents, is part of its shown name or of any member's current name. A section SHALL show every row when its own name matches, and otherwise only its matching rows. A blank query SHALL match every row.

#### Scenario: Searching a stretch
- **WHEN** an athlete types "potato" on Ann's, whose rows include "Lupine Hiking ↔ Potato Hill"
- **THEN** the list shows that row alone, among the rows matching

#### Scenario: Searching a section's name
- **WHEN** an athlete types "trees" on Rush
- **THEN** the Through the Trees Trail section shows all four of its rows

#### Scenario: Few rows
- **WHEN** a trail lists three rows
- **THEN** the page offers no search field and no filters other than sport

### Requirement: The Trail page filters by sport
When the path's segments are in two or more sport groups, the Trail page SHALL offer sport chips: All, then each of those sport groups in app order. Choosing one SHALL list only that group's segments, as the browse sport filter does (`segments/path-page`). Choosing All SHALL list every group's segments.

#### Scenario: Switching sport
- **WHEN** an athlete on Ann's, opened with Ride chosen, chooses Hike
- **THEN** the list shows Ann's hike segment groups, and the map draws them in their SAC levels' colours

#### Scenario: One sport
- **WHEN** every segment on a road is a ride segment
- **THEN** the page offers no sport chips

### Requirement: The Trail page filters by difficulty
When the listed rows differ in rating level (`segments/trail-ratings`), two levels or one beside unrated rows, the Trail page SHALL offer a difficulty filter listing the levels its rows have, Easiest to Extreme, each with its swatch, then Unrated when an unrated row is listed. Any number of levels MAY be chosen. With none chosen, every row SHALL show. With some chosen, only rows of those levels SHALL show. A row's level SHALL be the one it is drawn and marked in.

#### Scenario: Blue and black
- **WHEN** an athlete on a resort's page, with Alpine chosen, chooses Intermediate and Difficult
- **THEN** only the blue and black runs are listed and drawn

#### Scenario: No ratings
- **WHEN** no row on a road is rated
- **THEN** the page offers no difficulty filter

#### Scenario: One level
- **WHEN** every row on Ann's, with All chosen, is Easy
- **THEN** the page offers no difficulty filter

### Requirement: The Trail page filters by direction
When the listed rows differ in which ways they go, the Trail page SHALL offer a direction filter: Both ways, then each one-way direction the rows go (`segments/segment-direction`), such as Uphill only or Downhill only. Choosing one SHALL show the rows that go that way for the chosen sport, or for any sport when All is chosen.

#### Scenario: Downhill only
- **WHEN** an athlete on Rush, with Ride chosen, chooses Downhill only
- **THEN** the list shows the rows whose ride segments go one way only, downhill

#### Scenario: Every row both ways
- **WHEN** every row on a trail goes both ways
- **THEN** the page offers no direction filter

### Requirement: The Trail page filters by length
When the listed rows fall into more than one length band, the Trail page SHALL offer a length filter with three bands in the athlete's units: under 1 mi, 1 to 3 mi, and over 3 mi in imperial, and under 2 km, 2 to 5 km, and over 5 km in metric. Any number of bands MAY be chosen. A row SHALL be in the band of its shown segment's length.

#### Scenario: Short stretches
- **WHEN** an athlete using miles chooses "Under 1 mi" on Ann's
- **THEN** only rows shorter than one mile are listed

### Requirement: The Trail page sorts its rows
The Trail page SHALL offer a sort choice: Longest (the default, which is the page's usual order), Shortest, Name, Most climbing, Easiest first, and Hardest first. A sort SHALL order the rows inside each section and SHALL place each section by its first row. Rows that tie, or that have no value to sort by (no elevation, or unrated), SHALL come after the others, longest first and then by name.

#### Scenario: Easiest first
- **WHEN** an athlete on Ann's with Ride chosen sorts Easiest first
- **THEN** the Easy stretch comes before the Intermediate one, and unrated rows come last

#### Scenario: Most climbing without elevation
- **WHEN** an athlete sorts by Most climbing and one row has no elevation
- **THEN** that row comes after every row that has one

### Requirement: Each chip says what it is set to
A difficulty, direction or length chip SHALL read its filter's name while nothing is chosen in it, the choice itself when one is chosen, and the first choice and how many more when several are; assistive technology SHALL hear the filter's name before what it is set to. The sort chip SHALL show a sort icon with its choice, and assistive technology SHALL hear it as a sort.

#### Scenario: One length chosen
- **WHEN** an athlete using miles chooses "1–3 mi"
- **THEN** the length chip reads "1–3 mi", and is read aloud as "Length: 1–3 mi"

#### Scenario: Two levels chosen
- **WHEN** an athlete chooses Intermediate and Difficult
- **THEN** the difficulty chip reads "Intermediate +1"

#### Scenario: Sorted by name
- **WHEN** an athlete sorts by Name
- **THEN** the sort chip shows the sort icon and "Name", and is read aloud as "Sort: Name"

### Requirement: The map and the selection follow the list
The Trail page's map SHALL draw the path's line as before, and over it only the lines of the rows the search and filters show, still in their levels' colours with the legend of the levels drawn. When the search or a filter hides the selected row, it SHALL no longer be selected. Sorting SHALL NOT change the selection or move the map.

#### Scenario: Filtering out the selection
- **WHEN** an athlete has the Easy stretch selected on Ann's and chooses Intermediate only
- **THEN** the Easy stretch's line is gone from the map, and no row is selected

### Requirement: The Trail page says when nothing matches
When the search and filters leave no row, the Trail page SHALL say that no segments match and SHALL offer one control that clears the search and every filter except the sport. This SHALL be distinct from the path having no segments the athlete may see.

#### Scenario: Nothing matches
- **WHEN** an athlete chooses Extreme on a trail with no Extreme rows for the chosen sport
- **THEN** the list says no segments match and offers to clear the filters

### Requirement: The search, filters and sort last while the page is open
The search, filters and sort SHALL be kept while the Trail page stays open, including after returning from a segment page. A new visit SHALL start with the search blank, no filter chosen, Longest order, and the sport browse opened it with. A filter whose choice no longer applies, such as a level that no row has after the sport changes, SHALL drop that choice.

#### Scenario: Back from a segment
- **WHEN** an athlete filters Ann's to Intermediate, opens a stretch, and goes back
- **THEN** Ann's still shows Intermediate rows only

#### Scenario: Changing sport drops a level
- **WHEN** an athlete has Difficult chosen with Ride, and switches to Hike, where no row is Difficult
- **THEN** the difficulty filter has nothing chosen and every hike row is listed

### Requirement: The native Trail page searches and filters natively
On iOS the native Trail page SHALL search from the navigation bar's search field, and SHALL offer sport, difficulty, direction, length and sort in one toolbar menu built from the system's menu controls, in the shared page's words, showing that filters are on while any is chosen. Its list, map and no-matches state SHALL follow the same rules as the shared page.

#### Scenario: Filtering on iOS
- **WHEN** alice chooses Intermediate from the filter menu on the native Ann's page
- **THEN** the list and the native map show only the Intermediate rows, and the menu's button shows that a filter is on
