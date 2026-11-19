# segment-direction Specification

## Purpose

Says which way a segment runs in words riders use: uphill or downhill where its line clearly climbs or descends, else by compass or turn. Also says whether its sport group may travel it only that way, as the segment importer's source data rules.

## Requirements

### Requirement: A line clearly climbs or descends by its net change
A segment's line SHALL count as uphill when its gain minus its loss is at least 15 m and at least 3% of its length, and as downhill when its loss minus its gain meets the same bounds. Otherwise it SHALL count as neither. The same line run the other way SHALL swap uphill and downhill. A line with no known elevation SHALL count as neither.

#### Scenario: Steady climb
- **WHEN** a 2287 m line gains 199 m and loses 0 m
- **THEN** it counts as uphill, and the same line reversed counts as downhill

#### Scenario: Rolling trail
- **WHEN** a 2000 m line gains 100 m and loses 90 m
- **THEN** it counts as neither

#### Scenario: Short steep pitch
- **WHEN** a 300 m line gains 12 m and loses 0 m
- **THEN** it counts as neither, because its net climb is under 15 m

#### Scenario: Elevation unknown
- **WHEN** a segment view carries no elevation
- **THEN** its line counts as neither

### Requirement: A direction is named by climb, else by compass or turn
A segment's direction SHALL be named "Uphill" or "Downhill" when its line clearly climbs or descends. Otherwise it SHALL be named by its compass heading, or "Clockwise" or "Counterclockwise" for a loop, as the segment switcher names directions today.

#### Scenario: Climbing line
- **WHEN** a line heading mostly east clearly climbs
- **THEN** its direction is named "Uphill"

#### Scenario: Flat line
- **WHEN** a line heading mostly east neither clearly climbs nor descends
- **THEN** its direction is named "Eastbound"

#### Scenario: Loop
- **WHEN** a loop's ends are within 30 m of each other and it runs counterclockwise
- **THEN** its direction is named "Counterclockwise", whatever its elevation

### Requirement: A segment record may say it is one-way
`feed.segment` SHALL accept an optional boolean `oneWay`, meaning its sport group may travel its line only in the direction it is drawn, under the rules of the data it came from. A record without it SHALL mean the same as `false`.

#### Scenario: Record without the field
- **WHEN** an athlete's client writes a `feed.segment` with no `oneWay`
- **THEN** the record is valid and the segment isn't one-way

### Requirement: Only the importer's segments are served as one-way
A segment view SHALL carry `oneWay: true` only when the segment is in the segment importer account's repo and its record holds `oneWay: true`. For every other segment, the view SHALL omit it or carry `false`.

#### Scenario: Importer's one-way segment
- **WHEN** the importer publishes the ride segment "Clarks" with `oneWay: true`
- **THEN** its segment views carry `oneWay: true`

#### Scenario: Athlete sets the field
- **WHEN** an athlete publishes a `feed.segment` in their own repo with `oneWay: true`
- **THEN** its segment views don't carry `oneWay: true`

### Requirement: Alpine segments are never labelled one-way
The apps SHALL show no one-way wording for a segment of sport group `alpine`, run or lift, whatever its `oneWay`.

#### Scenario: Downhill piste
- **WHEN** a browse row lists an imported alpine run published with `oneWay: true`
- **THEN** its details say nothing about direction

### Requirement: A segment record may say which way it climbs
`feed.segment` SHALL accept an optional string `climb`, `uphill` or `downhill`, meaning its line, travelled in the direction it is drawn, climbs or descends as its trail does. A record without it SHALL say nothing about climb.

#### Scenario: Record without the field
- **WHEN** an athlete's client writes a `feed.segment` with no `climb`
- **THEN** the record is valid and its direction is judged from its elevation

#### Scenario: Unknown value
- **WHEN** a segment record holds `climb: "sideways"`
- **THEN** the record is indexed and its climb is treated as absent

### Requirement: Only the importer's segments are served with a climb
A segment view SHALL carry `climb` only when the segment is in the segment importer account's repo and its record holds `uphill` or `downhill`. For every other segment, the view SHALL omit it.

#### Scenario: Importer's downhill segment
- **WHEN** the importer publishes the ride segment "Rush - Dreamline" with `climb: "downhill"`
- **THEN** its segment views carry `climb: "downhill"`

#### Scenario: Athlete sets the field
- **WHEN** an athlete publishes a `feed.segment` in their own repo with `climb: "uphill"`
- **THEN** its segment views carry no `climb`

### Requirement: A served climb names the direction before elevation
The apps SHALL take a segment's climb from its served `climb` when it has one, and otherwise from its served elevation by the net-change rule. A loop SHALL still be named by its turn. The other direction of the same line SHALL take the reversed climb.

#### Scenario: Flat piece of a downhill trail
- **WHEN** a one-way ride segment's served elevation is 4 m up and 9 m down over 522 m, and it is served with `climb: "downhill"`
- **THEN** its row reads "Downhill only"

#### Scenario: No served climb, no elevation
- **WHEN** a one-way segment heading mostly west is served with neither `climb` nor elevation
- **THEN** its row reads "Westbound only"
