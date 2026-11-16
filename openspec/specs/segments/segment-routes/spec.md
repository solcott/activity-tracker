# segments/segment-routes Specification

## Purpose

Groups segments that follow the same path, across sport groups and in either direction, into one route, so athletes browse a trail once and switch between its segments from the segment page.

## Requirements

### Requirement: Segments that follow one path form a route
Two live (`proposed` or `active`) segments of the same kind SHALL be in one route when one's line duplicates the other's, by the test duplicates use, either as drawn or with one line reversed. Sport group SHALL NOT matter. A route SHALL be named by its earliest-indexed live member, and a segment that shares no path SHALL be its own route.

#### Scenario: Same trail, three sport groups
- **WHEN** the importer publishes ride, run and hike segments along the same trail in the same direction
- **THEN** all three are in one route, named by the first one indexed

#### Scenario: Opposite directions
- **WHEN** a ride segment runs east along a trail and another ride segment runs west along the same trail
- **THEN** they are in one route

#### Scenario: Longer stretch
- **WHEN** a segment covers an existing 1 km segment plus 2 km more of the trail
- **THEN** it is in its own route

#### Scenario: Lift and run
- **WHEN** an `alpine` run follows a lift's line
- **THEN** they are not in one route

### Requirement: Routes don't change what a segment is
Being in a route SHALL NOT change a segment's status, duplicate checks, efforts, leaderboards, Mayors, names or matching. Each member SHALL stay a segment of its own sport group and direction.

#### Scenario: Run over a ride line
- **WHEN** a run segment follows an existing ride segment's line
- **THEN** it is in the ride segment's route, it isn't a duplicate, and its efforts and leaderboard are its own

### Requirement: Routes follow their members
When a route's naming member is deleted, archived, re-lined, or marked duplicate, the AppView SHALL derive the route again for each of the other members, earliest-indexed first. The earliest-indexed live member SHALL then name the route, and a re-lined segment SHALL join the route its new line belongs to. Segments indexed before routes existed SHALL be given their routes.

#### Scenario: Naming member deleted
- **WHEN** the segment naming a route of three members is deleted
- **THEN** the remaining two stay in one route, named by the earlier-indexed of them

#### Scenario: Member re-lined elsewhere
- **WHEN** a member of a route is re-lined onto a different trail
- **THEN** it leaves the route and joins that trail's route, or forms its own

### Requirement: Segment views carry their route
Every segment view the AppView serves SHALL include the URI naming the segment's route.

#### Scenario: Searching an area
- **WHEN** a viewer's area search returns the ride and run segments of one trail
- **THEN** both views carry the same route

### Requirement: An area page holds its routes whole
A page of `feed.searchSegments` SHALL also hold every other member of its segments' routes that the same search would list, so a route is never split across pages, and the client's area cap SHALL keep or drop a route whole.

#### Scenario: Members indexed far apart
- **WHEN** a trail's southbound run is indexed early and its northbound and ride segments much later, and the area holds more segments than the cap
- **THEN** the area list shows the trail as one row with all its sport groups and both ways, as name search does

### Requirement: A segment lists its route's other members
`feed.getSegment` SHALL list the other members of the segment's route that the viewer could get from a segment search, each with its URI, sport group, current name and line. It SHALL list none for a lift or a segment alone in its route.

#### Scenario: Three-sport, two-way route
- **WHEN** a viewer opens the eastbound ride segment of a route holding ride, run and hike segments each way
- **THEN** the view lists the other five members

#### Scenario: Proposed member someone else made
- **WHEN** a route holds a proposed segment that the viewer didn't create and has no effort on
- **THEN** that member isn't listed to the viewer

### Requirement: A route's row is named without its direction
Where browse shows a route as one row, its name SHALL be the shown member's current name with a trailing direction removed: one of northbound, eastbound, southbound, westbound, clockwise or counterclockwise in parentheses. When the row lists both directions and the name holds exactly one "→", the arrow SHALL read "↔". Any other name SHALL be shown unchanged.

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

### Requirement: A route's row says what it covers
A route's row SHALL show its length, the sport groups of its listed members in the app's sport group order, and "Both ways" when its listed members run in both directions.

#### Scenario: Multi-use trail
- **WHEN** a row lists ride, run and hike segments of a 5.4 mi trail in both directions
- **THEN** its details read "5.40 mi · Ride, Run, Hike · Both ways"

### Requirement: Opening a route opens one of its members
Opening a route's row SHALL open the page of the member of the chosen sport group, or of the first sport group when All is chosen, in the direction of the first member listed for it.

#### Scenario: Ride filter
- **WHEN** an athlete with Ride chosen opens the "Ann's" row
- **THEN** the page of a ride segment of Ann's opens

### Requirement: The segment page switches within its route
When a segment's route has other members the viewer may see, the segment page SHALL offer its sport groups and directions to choose from, each in a fixed order that choosing never changes. Directions SHALL be named by compass heading, or clockwise and counterclockwise for a loop. Choosing one SHALL show that member on the same page, keeping the direction when the chosen sport group has a member that way. Back SHALL leave the page.

#### Scenario: Switching sport
- **WHEN** an athlete on the eastbound ride page of Ann's chooses Run
- **THEN** the page shows the eastbound run segment of Ann's, without opening another page

#### Scenario: Switching direction
- **WHEN** the athlete then chooses Westbound
- **THEN** the page shows the westbound run segment, Eastbound and Westbound keep their places, and back returns to the browse screen

#### Scenario: Alone in its route
- **WHEN** an athlete opens a segment alone in its route
- **THEN** the page offers no switcher

### Requirement: The native segment page switches within its route
On iOS the native segment page SHALL offer the same sport groups and directions with the system's own controls, in the shared page's words, and switching SHALL behave as on the shared page.

#### Scenario: Switching on iOS
- **WHEN** alice chooses Hike on the native ride page of Ann's
- **THEN** the native page shows the hike segment of Ann's in the same direction
