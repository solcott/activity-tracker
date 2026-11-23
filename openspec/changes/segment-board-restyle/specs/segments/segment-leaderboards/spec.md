# Spec Delta

## ADDED Requirements

### Requirement: A ranked board's rows line up
Each row of a segment's leaderboard, a segment's Mayors board and a dive site's Regulars board SHALL show, in order, the rank in a column of one width, the athlete's picture, their name with the row's date under it, and the row's value (a time or a count) at the trailing edge, heavier than the name and in figures of equal width. At the largest text sizes the name SHALL wrap while the value stays whole.

#### Scenario: Times line up
- **WHEN** a leaderboard lists 3:50, 4:05 and 12:22
- **THEN** the three times end at the same trailing edge, each digit taking the same width

#### Scenario: Long name at a large text size
- **WHEN** an athlete with a long name is listed at the largest text size
- **THEN** their name wraps onto a second line and their time stays whole at the trailing edge

### Requirement: A ranked board's rows keep each platform's marks
A ranked board's accented ranks SHALL stay as each board defines them. On iOS a row SHALL end with a disclosure chevron; Android, desktop and web SHALL show none.

#### Scenario: Chevron on iOS only
- **WHEN** the same leaderboard is opened on iOS and on Android
- **THEN** each iOS row ends with a chevron and no Android row does

### Requirement: A ranked board marks the viewer's own row
Wherever the viewer's own entry shows on a ranked board, among the loaded rows or in the pinned bar, it SHALL be marked as theirs.

#### Scenario: Own row among those loaded
- **WHEN** the viewer is ranked 2nd and the first page is shown
- **THEN** their row is marked as theirs and no "Your place" bar shows

### Requirement: The leaderboard pins the viewer's place
While the viewer is on a leaderboard but not among the entries loaded, their entry SHALL show under "Your place" in a bar fixed to the bottom of the screen, which stays while the list scrolls, and the list SHALL leave room so the bar never covers its last row. A viewer not on the board SHALL see no bar.

#### Scenario: Far down
- **WHEN** a viewer ranked 18th opens the leaderboard
- **THEN** a bar at the bottom shows their row, and it stays while the list scrolls

#### Scenario: Reaching their own row
- **WHEN** the viewer scrolls until the page holding their entry loads
- **THEN** the bar leaves and their row in the list is marked as theirs

### Requirement: The pinned place says how far behind first
When the board's first entry has loaded and the viewer isn't ranked first, the pinned bar SHALL add how far the viewer's time is behind that entry's: "<seconds> s behind 1st" under a minute, else the gap in a time's clock form, such as "1:05 behind 1st". The gap SHALL be to the first entry of the board as filtered.

#### Scenario: Under a minute
- **WHEN** the viewer's time is 5:03 and the leader's 4:12
- **THEN** the bar reads "51 s behind 1st"

#### Scenario: More than a minute behind
- **WHEN** the viewer's time is 5:17 and the leader's 4:12
- **THEN** the bar reads "1:05 behind 1st"

#### Scenario: Filtered board
- **WHEN** the viewer chooses the 35–44 age group, whose fastest is 4:30, and their time is 5:03
- **THEN** the bar reads "33 s behind 1st"

### Requirement: A segment board's choices are one row of chips
The leaderboard and the Mayors screen SHALL show every choice as its own menu chip in one row fixed under the screen's bar, which scrolls sideways when it doesn't fit and never wraps, in this order: the date range, the scope (Everyone, Following), the board's type only when two or more types are listed, then the age group, the weight class and the gender. Each chip SHALL name its choice ("This year", "Everyone", "35–44", "All weights") and be marked while it narrows the board. Neither screen SHALL show a segmented control or a chip that holds several choices. The first row of the board SHALL start directly under the chip row.

#### Scenario: Choosing Following
- **WHEN** a viewer on the everyone board opens the scope chip and chooses Following
- **THEN** the chip reads Following and is marked, and the following board shows from its first page once it answers

#### Scenario: Choosing an age group
- **WHEN** a viewer opens the age chip and chooses 35–44
- **THEN** the chip reads 35–44 and is marked, and the 35–44 board shows from its first page once it answers

#### Scenario: Narrow screen
- **WHEN** the chips don't fit across the screen
- **THEN** the row scrolls sideways and the board's first row stays where it was

#### Scenario: One type
- **WHEN** the segment lists only the `ride` board
- **THEN** no type chip or label shows

## MODIFIED Requirements

### Requirement: Boards are filtered by period
A leaderboard SHALL cover all time by default, or, when asked, the windows a Mayors board offers (`segments/segment-mayors`): today, this or last week, this or last calendar month, the last 90 days, or this or last calendar year. An effort SHALL belong to a window by its activity's local date, compared with the AppView's current date in UTC, and weeks SHALL start on Monday. The values `year` and `month` that older clients send SHALL mean this year and this month. An unknown period SHALL be refused.

#### Scenario: This month
- **WHEN** a viewer asks for this month's board and an athlete's best is from last year, but they rode it slower this month
- **THEN** the athlete is ranked by this month's effort

#### Scenario: Today
- **WHEN** a viewer asks for today's board on 22 October 2026
- **THEN** only efforts dated 22 October 2026 are ranked

#### Scenario: Last week
- **WHEN** a viewer asks for last week's board on Thursday 22 October 2026
- **THEN** only efforts dated Monday 12 October to Sunday 18 October 2026 are ranked

#### Scenario: An older client
- **WHEN** a client asks for the period `year`
- **THEN** it gets this year's board

### Requirement: The leaderboard screen is native on iOS
On iOS a segment's leaderboard SHALL be drawn with the system's own list, segmented control, menus and navigation bar, titled with the segment's name, and SHALL list what the shared screen lists, in the same words: each entry's rank, the athlete's picture and name, the day of the effort and its time, opening the activity, the viewer's own place pinned as the shared screen pins it, and the empty message. It SHALL load page by page following `ui/content-loading`.

#### Scenario: Three entries
- **WHEN** a viewer opens a leaderboard where Bob rode it in 3:50, Alice in 4:05 and Carol in 4:22
- **THEN** it lists 1 Bob 3:50, 2 Alice 4:05 and 3 Carol 4:22, each with the day of the effort, and the three ranks in the accent colour

#### Scenario: Opening an entry
- **WHEN** the viewer taps Bob's entry
- **THEN** the activity his effort came from opens, as from the shared screen

#### Scenario: Own place pinned on iOS
- **WHEN** a viewer ranked 140th opens the leaderboard
- **THEN** their own entry shows under "Your place" in a bar at the bottom of the screen, with their gap to first

#### Scenario: An empty board
- **WHEN** no one the viewer may see has an effort for the chosen filters
- **THEN** it reads "No one is on this leaderboard yet"

### Requirement: The leaderboard's choices on iOS
On iOS the leaderboard SHALL offer its choices as the shared screen's row of chips (menus, each a capsule naming its choice and filled while it narrows the board), in a bar fixed under the navigation bar, with no segmented control. Weight classes SHALL read in the viewer's units.

#### Scenario: Choosing a period
- **WHEN** a viewer on the all-time board opens the date chip and chooses Today
- **THEN** the all-time entries leave the screen, and today's board shows from its first page once it answers

#### Scenario: Choosing Following on iOS
- **WHEN** a viewer opens the scope menu and chooses Following
- **THEN** the menu reads Following and is filled, and the following board shows from its first page once it answers

#### Scenario: Two types
- **WHEN** a ride segment lists the `ride` and `eBike` boards
- **THEN** the type reads "Ride" and opens a menu of Ride and E-bike

#### Scenario: Weight in pounds on iOS
- **WHEN** a viewer whose units are imperial opens the weight menu
- **THEN** it offers "All weights" and the classes in pounds, such as "Under 121 lb"
