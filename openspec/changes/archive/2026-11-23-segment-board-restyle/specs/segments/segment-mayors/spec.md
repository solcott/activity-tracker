# Spec Delta

## ADDED Requirements

### Requirement: The Mayors screen pins the viewer's place
While the viewer is on a Mayors board but not among the entries loaded, their entry SHALL show under "Your place" in a bar fixed to the bottom of the screen, which stays while the list scrolls, and the list SHALL leave room so the bar never covers its last row. A viewer not on the board SHALL see no bar.

#### Scenario: Far down the board
- **WHEN** a viewer ranked 58th opens the Mayors screen
- **THEN** a bar at the bottom shows their row, and it stays while the list scrolls

#### Scenario: Not on the board
- **WHEN** the viewer has no effort in the chosen window
- **THEN** no bar shows

### Requirement: The pinned place says what it takes to be Mayor
When the board's first entry has loaded and the viewer isn't ranked first, the pinned bar SHALL add how many more efforts would make them Mayor: the first entry's count less theirs, plus one, since an equal count reached later ranks behind. The count SHALL be to the first entry of the board as filtered.

#### Scenario: Behind the Mayor
- **WHEN** a viewer with 6 efforts, ranked 7th, opens a board whose Mayor has 13
- **THEN** the bar reads "8 more efforts to be Mayor"

#### Scenario: Level with the Mayor
- **WHEN** the viewer's pinned entry has the Mayor's count, reached later
- **THEN** the bar reads "1 more effort to be Mayor"

### Requirement: The Mayors screen's choices are the leaderboard's chip row
The Mayors screen SHALL lay out its choices as the leaderboard's row of chips (`segments/segment-leaderboards`), with the window as the date range, the last 90 days by default.

#### Scenario: Choosing a window
- **WHEN** a viewer opens the date chip and chooses This week
- **THEN** the chip reads This week, and this week's board shows from its first page once it answers

## MODIFIED Requirements

### Requirement: Mayors boards cover a window
A board SHALL cover the last 90 days by default, or, when asked, today, all time, this or last calendar year, this or last calendar month, or this or last week. An effort SHALL belong to a window by its activity's local date, compared with the AppView's current date in UTC. The last 90 days SHALL be today and the 89 days before it. Weeks SHALL start on Monday. An unknown window SHALL be refused.

#### Scenario: Default window
- **WHEN** a viewer asks for a segment's Mayors without a window on 22 October 2026
- **THEN** the board counts efforts dated 25 July 2026 to 22 October 2026

#### Scenario: Today
- **WHEN** a viewer asks for today's board on 22 October 2026
- **THEN** the board counts only efforts dated 22 October 2026

#### Scenario: Last week
- **WHEN** a viewer asks for last week's board on Thursday 22 October 2026
- **THEN** the board counts efforts dated Monday 12 October to Sunday 18 October 2026

#### Scenario: Last year
- **WHEN** a viewer asks for last year's board in 2026
- **THEN** the board counts only efforts dated in 2025

#### Scenario: Unknown window
- **WHEN** a viewer asks for a window the AppView doesn't know
- **THEN** the AppView refuses the request

### Requirement: The Mayors screen's choices on iOS
On iOS the Mayors board SHALL offer its choices as the leaderboard's row of chips, in a bar fixed under the navigation bar: the window (the last 90 days by default, or today, all time, this or last year, month or week), the scope, the board's type only when two or more are listed, and the age, weight and gender, each a menu showing its choice.

#### Scenario: Choosing a window on iOS
- **WHEN** a viewer on the last-90-days board opens the window menu and chooses This week
- **THEN** the earlier entries leave the screen, and this week's board shows from its first page once it answers

#### Scenario: Choosing Following on iOS
- **WHEN** a viewer opens the scope menu and chooses Following
- **THEN** the menu reads Following and is filled, and the following board shows from its first page once it answers
