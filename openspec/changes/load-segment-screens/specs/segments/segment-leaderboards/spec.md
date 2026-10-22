# Spec Delta

## MODIFIED Requirements

### Requirement: The leaderboard screen
The segment screen of an `active` segment SHALL open its leaderboard. The leaderboard screen SHALL list the entries with rank, athlete, time and date, and SHALL let the viewer choose the period and scope. For a `ride` segment it SHALL also let the viewer choose the e-bike board. It SHALL load further pages as the viewer scrolls, and SHALL show the viewer's own entry, for the chosen board, when it isn't among those listed. Tapping an entry SHALL open its activity. A failed load SHALL show an error with a retry.

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
