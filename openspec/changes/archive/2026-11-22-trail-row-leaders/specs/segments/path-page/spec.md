# Spec Delta

## ADDED Requirements

### Requirement: Trail rows show their board's leader
A segment-group row on the Trail page SHALL show, after its name and details, the fastest time on its shown segment's board (as `getPath` serves it, `segments/segment-leaderboards`) and the leader's handle under it, the time set heavier than the row's text and aligned to the trailing edge, so times line up down the list. A row whose segment has no leader SHALL show no column, its name and details using the full width.

#### Scenario: Ranked stretch
- **WHEN** an athlete opens Ann's, and Kate leads its "Lupine Hiking ↔ Potato Hill" segment with 4:31
- **THEN** that row ends with 4:31 and Kate's handle under it

#### Scenario: Lift
- **WHEN** a resort's Trail page lists a lift
- **THEN** the lift's row has no time column

#### Scenario: No one on the board
- **WHEN** a row's segment is active but no effort the viewer may see is on its board
- **THEN** the row has no time column

### Requirement: Trail rows show the viewer's own place
When the viewer is on a row's board, the row SHALL add a line under its details with their time and place, worded as the Segment page's Leaderboard card words them. A viewer not on the board SHALL see no such line.

#### Scenario: Viewer on the board
- **WHEN** a viewer ranked 12th with 14:02 opens Ann's
- **THEN** the "Ann's" row reads "You: 14:02 · #12" under its details

#### Scenario: Viewer leads
- **WHEN** the viewer holds the fastest time on a row's board
- **THEN** the row shows their time and handle in its column and "You: <time> · #1" under its details

### Requirement: Trail rows share one anatomy
A Trail page row SHALL be laid out alike on every platform: the rating swatch on the name's first line, a wrapped name and the lines under it starting under the name's text, then the time column. On iOS a disclosure chevron SHALL follow the column; Android, desktop and web SHALL show none. A row's column SHALL keep its width at large text sizes, the name and details wrapping beside it.

#### Scenario: Long name
- **WHEN** a rated row's name wraps onto two lines on a phone
- **THEN** its swatch sits beside the first line and the second line, the details and the viewer's line start under the name's first letter

#### Scenario: Large text
- **WHEN** the athlete's text size is at its largest
- **THEN** each row's time and handle stay whole at the trailing edge and its name wraps beside them
