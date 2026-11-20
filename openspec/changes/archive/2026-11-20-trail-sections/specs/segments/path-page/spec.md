# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
