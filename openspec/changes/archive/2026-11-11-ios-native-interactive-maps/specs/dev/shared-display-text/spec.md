# Spec Delta

## ADDED Requirements

### Requirement: A segment's credit reads the same in both UIs
What a segment's data credit says SHALL be chosen once in shared code: OpenStreetMap's linked credit, an agency's line with its name when
given, and the licence line when the record names one.

#### Scenario: Agency credit
- **WHEN** a segment's line came from an agency named "USFS" under "PD"
- **THEN** both UIs read "From USFS trail data" and "Data licence: PD"

### Requirement: Segment browse reads the same in both UIs
What segment browse lists and says SHALL be chosen once in shared code: each row's length, type and distance away, which message the list
shows instead of runs (locating, nowhere, zoom in, the web prompt), the capped and none-here notes, the no-match message and whether
Search everywhere is offered, and the sport and scope choices' labels.

#### Scenario: Too wide
- **WHEN** the browse map shows more than segments are searched over and no name is typed
- **THEN** both UIs ask the athlete to zoom in

### Requirement: A map of many segments behaves the same in every UI
How a map of many segments draws and answers SHALL be decided once in shared code: the lines in drawing order (lifts, then runs, then the
selected run), which run a tap selects (the nearest within the tap's reach, never a lift), and what the map reports it shows.

#### Scenario: Tap between two runs
- **WHEN** a tap lands 5 m from one run and 9 m from another, within its 15 m reach
- **THEN** the nearer run is selected

### Requirement: Accept invite reads the same in both UIs
What Accept invite says SHALL be chosen once in shared code: the intro naming the inviter, the overlap warning, the gone message, and
Accept's label while accepting.

#### Scenario: Accepting
- **WHEN** alice taps Accept
- **THEN** both UIs label the button "Accepting…" until it finishes
