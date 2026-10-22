# Spec Delta

## ADDED Requirements

### Requirement: Each activity type has its own board
A leaderboard SHALL rank only efforts of one board type: the activity's sport, except that an e-bike or e-mountain-bike ride's type SHALL be `eBike`. An effort SHALL be ranked only on its own type's board.

#### Scenario: Hike segment
- **WHEN** athletes hike, walk and ruck the same hike segment
- **THEN** the `hike`, `walk` and `rucking` boards each rank only their own efforts

#### Scenario: E-mountain-bike ride
- **WHEN** an athlete's e-mountain-bike ride has the fastest time on a ride segment
- **THEN** it leads the `eBike` board and isn't on the `ride` board

#### Scenario: Unknown board
- **WHEN** a viewer asks for a board token that isn't a sport and isn't `eBike`
- **THEN** the AppView refuses the request

### Requirement: Boards list the segment's types
A leaderboard response SHALL give the board type it served and the types the viewer can see ranked efforts of on the segment over all time, whatever period, scope and brackets were asked for, most efforts first. A type asked for by name SHALL be served and listed even with no such efforts.

#### Scenario: Types listed
- **WHEN** a viewer may see 9 `ride`, 2 `handcycle` and 1 `eBike` efforts on a segment and asks for this month's board
- **THEN** the response lists `ride`, `handcycle` and `eBike`, in that order

#### Scenario: Requested type with no efforts
- **WHEN** a viewer asks for the `canoe` board of a paddle segment that only has kayak efforts
- **THEN** the response serves an empty `canoe` board and lists `kayak` and `canoe`

### Requirement: The busiest type is the default board
A leaderboard request without a board type SHALL be served the listed type with the most efforts, the earliest token on a tie. With no efforts to list, it SHALL return no entries and name no board.

#### Scenario: No type asked for
- **WHEN** a viewer asks for a hike segment's board without a type and may see 5 `walk` and 3 `hike` efforts on it
- **THEN** the `walk` board is served

### Requirement: Boards open on the type you came from
A segment opened from an activity's segment list SHALL carry that activity's board type to its leaderboard, which SHALL open on it. A segment opened any other way SHALL open its leaderboard on the AppView's default type.

#### Scenario: From a canoe trip
- **WHEN** a viewer opens a paddle segment from a canoe activity and then its leaderboard
- **THEN** the `canoe` board is shown, even if kayaks have more efforts there

#### Scenario: From search
- **WHEN** a viewer opens the same segment from search and then its leaderboard
- **THEN** the board of the type with the most efforts is shown

## MODIFIED Requirements

### Requirement: The leaderboard screen
The segment screen of an `active` segment SHALL open its leaderboard, which SHALL list the entries with rank, athlete, time and date, let the viewer choose the period and scope, and show the board's type, a menu when several types are listed; a new type SHALL show from the first page. It SHALL load more pages as the viewer scrolls and show the viewer's own entry when it isn't among those listed. Tapping an entry SHALL open its activity. A failed load SHALL show an error with a retry.

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
- **WHEN** a viewer on a ride segment's `ride` board chooses `eBike` from the type menu
- **THEN** the `ride` entries and the viewer's `ride` entry leave the screen, and the `eBike` board and the viewer's `eBike` entry, if any, show once it answers

#### Scenario: One type
- **WHEN** every effort a viewer may see on a segment is a run
- **THEN** the screen shows the type as a label, not a menu

## REMOVED Requirements

### Requirement: E-bike efforts have their own board
**Reason**: Every activity type now has its own board, and e-bike rides are one of those types (`eBike`), so a separate standard and e-bike pair no longer exists.
**Migration**: Ask for `board: eBike` instead of `eBike: true`, and for the activity's sport instead of the standard board.
