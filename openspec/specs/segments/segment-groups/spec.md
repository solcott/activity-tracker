# segment-groups Specification

## Purpose

Groups segments that follow the same line, across sport groups and in either direction, into one segment group, so athletes see a line once and switch between its segments from the segment page.

## Requirements

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
Where browse shows a segment group as one row, its name SHALL be the shown member's current name with a trailing direction removed: one of northbound, eastbound, southbound, westbound, clockwise, counterclockwise, uphill or downhill in parentheses. When the row lists both directions and the name holds exactly one "→", the arrow SHALL read "↔". Any other name SHALL be shown unchanged.

#### Scenario: Whole trail both ways
- **WHEN** a row lists "Ann's (eastbound)" and "Ann's (westbound)"
- **THEN** it is named "Ann's"

#### Scenario: Whole trail named by climb
- **WHEN** a row lists "Clarks (uphill)" and "Clarks (downhill)"
- **THEN** it is named "Clarks"

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
A segment group's row SHALL show its length and the sport groups of its listed members in the app's sport group order. It SHALL say, per sport group, whether it goes both ways (its listed members run in both directions) or one way only (its listed members all run one way and are one-way, `segments/segment-direction`), naming that way as "Uphill only", "Downhill only" or by compass or turn.

#### Scenario: Multi-use trail
- **WHEN** a row lists ride, run and hike segments of a 5.4 mi trail in both directions
- **THEN** its details read "5.40 mi · Ride, Run, Hike · Both ways"

#### Scenario: One-way climb
- **WHEN** a row lists only the 1.42 mi one-way ride segment "Clarks", whose line clearly climbs
- **THEN** its details read "1.42 mi · Ride · Uphill only"

#### Scenario: One-way on a flat line
- **WHEN** a row lists only a one-way ride segment that heads east and neither clearly climbs nor descends
- **THEN** its details end "Ride · Eastbound only"

#### Scenario: One direction published, not one-way
- **WHEN** a row lists only the eastbound run segment of a two-way trail, which isn't one-way
- **THEN** its details read "<length> · Run" and say nothing about direction

### Requirement: Opening a segment group opens one of its segments
Opening a segment group's row SHALL open the page of the member of the chosen sport group, or of the first sport group when All is chosen, in the direction of the first member listed for it.

#### Scenario: Ride filter
- **WHEN** an athlete with Ride chosen opens the "Ann's" row
- **THEN** the page of a ride segment of Ann's opens

### Requirement: The segment page switches within its segment group
When a segment's segment group has other members the viewer may see, or the segment is one-way, the segment page SHALL offer its sport groups and directions to choose from, each in a fixed order that choosing never changes. Choosing one SHALL show that member on the same page, keeping the direction when the chosen sport group has a member that way. Back SHALL leave the page.

#### Scenario: Switching sport
- **WHEN** an athlete on the eastbound ride page of Ann's chooses Run
- **THEN** the page shows the eastbound run segment of Ann's, without opening another page

#### Scenario: Switching direction
- **WHEN** the athlete then chooses Westbound
- **THEN** the page shows the westbound run segment, Eastbound and Westbound keep their places, and back returns to the browse screen

#### Scenario: Alone in its segment group
- **WHEN** an athlete opens a segment alone in its segment group that isn't one-way
- **THEN** the page offers no switcher

### Requirement: The native segment page switches within its segment group
On iOS the native segment page SHALL offer the same sport groups and directions with the system's own controls, in the shared page's words, and switching SHALL behave as on the shared page. A one-way sport group's single direction SHALL be shown disabled, as on the shared page.

#### Scenario: Switching on iOS
- **WHEN** alice chooses Hike on the native ride page of Ann's
- **THEN** the native page shows the hike segment of Ann's in the same direction

#### Scenario: One-way on iOS
- **WHEN** alice opens the native page of the one-way ride segment "Clarks", whose line clearly climbs
- **THEN** its direction control shows "Uphill only", disabled

### Requirement: A segment group's row words mixed directions per sport group
When every sport group of a row says the same about direction, the row SHALL say it once after the sport groups. Otherwise it SHALL give each wording after the sport groups it covers, both ways first, then one way only, and sport groups that say nothing last.

#### Scenario: Mixed sport groups
- **WHEN** a row lists run and hike segments both ways and a one-way ride segment whose line clearly climbs
- **THEN** its details read "<length> · Run, Hike both ways · Ride uphill only"

### Requirement: The segment page names directions by climb
The segment page SHALL name its directions as `segments/segment-direction` names them, judging the other direction's climb from the shown segment's elevation reversed, since a segment group's members share one line.

#### Scenario: Directions named by climb
- **WHEN** an athlete opens a run segment whose line clearly climbs and whose group has a run segment the other way
- **THEN** the directions read "Uphill" and "Downhill", the shown one current

### Requirement: The segment page shows a one-way sport group's single direction
When the shown segment is one-way and its sport group has no member the other way, the segment page SHALL show its one direction as a single choice that can't be chosen, reading "Uphill only", "Downhill only" or by compass or turn. When that segment is alone in its segment group, the page SHALL offer no sport choice.

#### Scenario: One-way sport group
- **WHEN** an athlete opens a one-way ride segment whose line clearly climbs, in a segment group that also holds run segments
- **THEN** the directions show one choice, "Uphill only", which can't be chosen

#### Scenario: One-way and alone
- **WHEN** an athlete opens the one-way ride segment "Clarks", alone in its segment group, whose line clearly climbs
- **THEN** the page shows no sport choice and one direction choice, "Uphill only", which can't be chosen

### Requirement: A row whose name shows both ways doesn't say it again
When a segment group's row is named with ↔ (`segments/segment-groups`: a two-way stretch named "A ↔ B") and every sport group it lists goes both ways, its details SHALL leave out "Both ways". A row with any one-way wording, or whose name has no ↔, SHALL word its details as before.

#### Scenario: Two-way stretch
- **WHEN** a row named "Lupine Hiking ↔ Potato Hill" lists ride and run segments both ways
- **THEN** its details read "2.10 km · Ride, Run"

#### Scenario: Whole trail
- **WHEN** a row named "Ann's" lists ride, run and hike segments both ways
- **THEN** its details still read "8.69 km · Ride, Run, Hike · Both ways"

#### Scenario: Mixed directions
- **WHEN** a row named with ↔ lists run segments both ways and a one-way ride segment
- **THEN** its details give each wording as before
