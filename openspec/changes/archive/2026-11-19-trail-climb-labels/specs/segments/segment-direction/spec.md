# Spec Delta

## ADDED Requirements

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
