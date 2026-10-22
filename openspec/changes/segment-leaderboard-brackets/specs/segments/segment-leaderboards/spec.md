# Spec Delta

## ADDED Requirements

### Requirement: Boards can be filtered by age group, weight class and gender
A leaderboard SHALL cover every bracket by default, or, when asked, only athletes in a given age group, weight class or gender bucket (`segments/leaderboard-brackets`), with any combination of the three. A filtered board SHALL list only athletes whose indexed brackets match every filter, ranked among themselves, together with the period, scope and e-bike choices. An unknown bracket value SHALL be refused.

#### Scenario: Age group board
- **WHEN** a viewer asks for the `35-44` board and athletes A (`35-44`), B (`45-54`) and C (no brackets record) have efforts on it
- **THEN** the board lists only A, ranked 1st

#### Scenario: Combined filters
- **WHEN** a viewer asks for the `female`, `55-64` kg board for this year
- **THEN** it lists only athletes in both brackets, by their efforts this year

#### Scenario: Missing bracket
- **WHEN** an athlete has brackets on but gave no weight
- **THEN** they are on age-group and gender boards and on no weight-class board

#### Scenario: Unknown value
- **WHEN** a viewer asks for age group `40-49`
- **THEN** the AppView refuses the request

#### Scenario: Viewer's own entry
- **WHEN** a signed-in viewer outside the `25-34` group asks for the `25-34` board
- **THEN** the response has no viewer entry

## MODIFIED Requirements

### Requirement: The leaderboard screen
The segment screen of an `active` segment SHALL open its leaderboard. The leaderboard screen SHALL list the entries with rank, athlete, time and date, and SHALL let the viewer choose the period and scope. For a `ride` segment it SHALL also let the viewer choose the e-bike board. It SHALL let the viewer choose an age group, a weight class and a gender, each "All" by default, showing weight classes in the viewer's units. It SHALL load further pages as the viewer scrolls, and SHALL show the viewer's own entry when it isn't among those listed. Tapping an entry SHALL open its activity. A failed load SHALL show an error with a retry.

#### Scenario: Open from segment
- **WHEN** a viewer opens an active segment and chooses the leaderboard
- **THEN** the all-time, everyone board is shown

#### Scenario: Proposed segment
- **WHEN** a viewer opens a proposed segment
- **THEN** the screen offers no leaderboard

#### Scenario: Own place pinned
- **WHEN** a viewer ranked 140th opens the leaderboard
- **THEN** their own entry is shown apart from the first page

#### Scenario: Switching to the e-bike board
- **WHEN** a viewer on a `ride` segment's standard board chooses the e-bike board
- **THEN** the standard entries and the viewer's standard entry leave the screen, and the e-bike board and the viewer's e-bike entry, if any, show once it answers

#### Scenario: Choosing an age group
- **WHEN** a viewer on the all-time board chooses the `45-54` age group
- **THEN** the unfiltered entries leave the screen, and the `45-54` board shows from its first page once it answers

#### Scenario: Weight in pounds
- **WHEN** a viewer whose units are imperial opens the weight-class choice
- **THEN** the classes are labelled in pounds
