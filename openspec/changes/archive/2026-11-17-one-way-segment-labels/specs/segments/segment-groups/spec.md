# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
