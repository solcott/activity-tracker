# Spec Delta

## Purpose

Groups segments that follow the same line, across sport groups and in either direction, into one segment group, so athletes see a line once and switch between its segments from the segment page.

## ADDED Requirements

### Requirement: Segments that follow one line form a segment group
Two live (`proposed` or `active`) segments of the same kind SHALL be in one segment group when one's line duplicates the other's, by the test duplicates use, either as drawn or with one line reversed. Sport group SHALL NOT matter. A segment group SHALL be named by its earliest-indexed live member, and a segment that shares no line SHALL be its own segment group.

#### Scenario: Same trail, three sport groups
- **WHEN** the importer publishes ride, run and hike segments along the same trail in the same direction
- **THEN** all three are in one segment group, named by the first one indexed

#### Scenario: Opposite directions
- **WHEN** a ride segment runs east along a trail and another ride segment runs west along the same trail
- **THEN** they are in one segment group

#### Scenario: Longer stretch
- **WHEN** a segment covers an existing 1 km segment plus 2 km more of the trail
- **THEN** it is in its own segment group

#### Scenario: Lift and run
- **WHEN** an `alpine` run follows a lift's line
- **THEN** they are not in one segment group

### Requirement: Segment groups don't change what a segment is
Being in a segment group SHALL NOT change a segment's status, duplicate checks, efforts, leaderboards, Mayors, names or matching. Each member SHALL stay a segment of its own sport group and direction.

#### Scenario: Run over a ride line
- **WHEN** a run segment follows an existing ride segment's line
- **THEN** it is in the ride segment's segment group, it isn't a duplicate, and its efforts and leaderboard are its own

### Requirement: Segment groups follow their members
When a segment group's naming member is deleted, archived, re-lined, or marked duplicate, the AppView SHALL derive the segment group again for each of the other members, earliest-indexed first. The earliest-indexed live member SHALL then name the segment group, and a re-lined segment SHALL join the segment group its new line belongs to. Segments indexed before segment groups existed SHALL be given their segment groups.

#### Scenario: Naming member deleted
- **WHEN** the segment naming a segment group of three members is deleted
- **THEN** the remaining two stay in one segment group, named by the earlier-indexed of them

#### Scenario: Member re-lined elsewhere
- **WHEN** a member of a segment group is re-lined onto a different trail
- **THEN** it leaves the segment group and joins that trail's segment group, or forms its own

### Requirement: Segment views carry their segment group
Every segment view the AppView serves SHALL include the URI naming the segment's segment group.

#### Scenario: Searching an area
- **WHEN** a viewer's area search returns the ride and run segments of one trail
- **THEN** both views carry the same segment group

### Requirement: An area page holds its segment groups whole
A page of `feed.searchSegments` SHALL also hold every other member of its segments' segment groups that the same search would list, so a segment group is never split across pages, and the client's area cap SHALL keep or drop a segment group whole.

#### Scenario: Members indexed far apart
- **WHEN** a trail's southbound run is indexed early and its northbound and ride segments much later, and the area holds more segments than the cap
- **THEN** the trail's segment group is shown as one row with all its sport groups and both ways, as name search shows it

### Requirement: A segment lists its segment group's other members
`feed.getSegment` SHALL list the other members of the segment's segment group that the viewer could get from a segment search, each with its URI, sport group, current name and line. It SHALL list none for a lift or a segment alone in its segment group.

#### Scenario: Three-sport, two-way segment group
- **WHEN** a viewer opens the eastbound ride segment of a segment group holding ride, run and hike segments each way
- **THEN** the view lists the other five members

#### Scenario: Proposed member someone else made
- **WHEN** a segment group holds a proposed segment that the viewer didn't create and has no effort on
- **THEN** that member isn't listed to the viewer

### Requirement: A segment group's row is named without its direction
Where browse shows a segment group as one row, its name SHALL be the shown member's current name with a trailing direction removed: one of northbound, eastbound, southbound, westbound, clockwise or counterclockwise in parentheses. When the row lists both directions and the name holds exactly one "→", the arrow SHALL read "↔". Any other name SHALL be shown unchanged.

#### Scenario: Whole trail both ways
- **WHEN** a row lists "Ann's (eastbound)" and "Ann's (westbound)"
- **THEN** it is named "Ann's"

#### Scenario: Stretch both ways
- **WHEN** a row lists "Ann's: Lupine Hiking → Potato Hill" and its reverse
- **THEN** it is named "Ann's: Lupine Hiking ↔ Potato Hill"

#### Scenario: One direction only
- **WHEN** a row lists only "Oak: Fire Road 3 → Pine", which is one-way
- **THEN** it is named "Oak: Fire Road 3 → Pine"

#### Scenario: Renamed member
- **WHEN** the shown member's adopted name is "Wiggly Woods"
- **THEN** the row is named "Wiggly Woods"

### Requirement: A segment group's row says what it covers
A segment group's row SHALL show its length, the sport groups of its listed members in the app's sport group order, and "Both ways" when its listed members run in both directions.

#### Scenario: Multi-use trail
- **WHEN** a row lists ride, run and hike segments of a 5.4 mi trail in both directions
- **THEN** its details read "5.40 mi · Ride, Run, Hike · Both ways"

### Requirement: Opening a segment group opens one of its segments
Opening a segment group's row SHALL open the page of the member of the chosen sport group, or of the first sport group when All is chosen, in the direction of the first member listed for it.

#### Scenario: Ride filter
- **WHEN** an athlete with Ride chosen opens the "Ann's" row
- **THEN** the page of a ride segment of Ann's opens

### Requirement: The segment page switches within its segment group
When a segment's segment group has other members the viewer may see, the segment page SHALL offer its sport groups and directions to choose from, each in a fixed order that choosing never changes. Directions SHALL be named by compass heading, or clockwise and counterclockwise for a loop. Choosing one SHALL show that member on the same page, keeping the direction when the chosen sport group has a member that way. Back SHALL leave the page.

#### Scenario: Switching sport
- **WHEN** an athlete on the eastbound ride page of Ann's chooses Run
- **THEN** the page shows the eastbound run segment of Ann's, without opening another page

#### Scenario: Switching direction
- **WHEN** the athlete then chooses Westbound
- **THEN** the page shows the westbound run segment, Eastbound and Westbound keep their places, and back returns to the browse screen

#### Scenario: Alone in its segment group
- **WHEN** an athlete opens a segment alone in its segment group
- **THEN** the page offers no switcher

### Requirement: The native segment page switches within its segment group
On iOS the native segment page SHALL offer the same sport groups and directions with the system's own controls, in the shared page's words, and switching SHALL behave as on the shared page.

#### Scenario: Switching on iOS
- **WHEN** alice chooses Hike on the native ride page of Ann's
- **THEN** the native page shows the hike segment of Ann's in the same direction
