# Spec Delta

## ADDED Requirements

### Requirement: A board's choices read the same in both UIs
For the leaderboard, Mayors and Regulars boards, each choice's options and their labels (periods and windows, scope, the board's types, and the age, weight and gender brackets, weight in the viewer's units), which option shows as chosen, whether a choice marks itself as narrowing the board, and whether the type is a menu or a label SHALL be decided by shared code that both UIs ask.

#### Scenario: A chosen weight class in pounds
- **WHEN** an imperial viewer has chosen the `55-64` weight class
- **THEN** both UIs show the weight choice as "121–142 lb", marked as narrowing the board

#### Scenario: One type
- **WHEN** a board lists a single type, `run`
- **THEN** both UIs show "Run" as a label and offer no menu

### Requirement: A board's entries read the same in both UIs
A board entry's rank, whether the rank is accented, the day line (the effort's day, or "Reached" with the day its count was reached) and its count SHALL be chosen by shared code that both UIs ask; the leaderboard's time is the shared clock form of a duration.

#### Scenario: A Mayors entry
- **WHEN** Bob leads a Mayors board with 1204 efforts, the latest on 2026-10-26
- **THEN** both UIs show rank 1 accented, "Reached Oct 26, 2026" and "1,204 efforts"

#### Scenario: A fourth place on a leaderboard
- **WHEN** an entry is ranked 4th
- **THEN** neither UI accents its rank

### Requirement: A written-out date reads the same in both UIs
A calendar date written out, as a board entry, a segment effort and a threshold history row show it, SHALL be chosen by shared code: the short month name, the day and the year, never grouped; a `YYYY-MM-DD` text that doesn't parse SHALL read as itself.

#### Scenario: An effort's day
- **WHEN** an effort's date is `2026-01-05`
- **THEN** both UIs read "Jan 5, 2026"

#### Scenario: A date that doesn't parse
- **WHEN** an entry's date is `soon`
- **THEN** both UIs read "soon"

### Requirement: The starred list reads the same in both UIs
A starred segment's line (its length in the viewer's units and its type: a ski lift, a ski run, or its sport group), whether a star opens its segment, whether its unstar can be used, the notice that the session must sign in again, and whether the empty message shows SHALL be decided by shared code that both UIs ask.

#### Scenario: A ski run
- **WHEN** a metric athlete has starred a 1.2 km alpine run
- **THEN** both UIs read "1.2 km · Ski run" under its name

#### Scenario: A star whose segment is gone
- **WHEN** a star's segment is no longer available
- **THEN** neither UI opens anything when it is tapped, and both offer its unstar
