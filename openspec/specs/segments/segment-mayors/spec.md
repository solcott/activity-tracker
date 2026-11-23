# segments/segment-mayors Specification

## Purpose

Defines segment Mayors: a board per segment that ranks athletes by how many efforts they have on it over a window, whose leader is the segment's Mayor, and how clients show it.

## Requirements

### Requirement: Only active segments have Mayors
`feed.getSegmentMayors` SHALL return a segment's board only while the segment is `active` and of kind `run`. For a segment of any other status, and for a lift, it SHALL return no entries. A segment that isn't indexed or isn't served to the viewer SHALL be `NotFound`.

#### Scenario: Proposed segment
- **WHEN** a viewer asks for the Mayors of a proposed segment that three efforts are on
- **THEN** the AppView returns no entries

#### Scenario: Unknown segment
- **WHEN** a viewer asks for the Mayors of a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

#### Scenario: Active lift
- **WHEN** a viewer asks for the Mayors of an active chairlift
- **THEN** the AppView returns no entries

### Requirement: Mayors count the efforts leaderboards rank
A Mayors board SHALL count, per athlete, every effort on the segment in the window that the same viewer's time leaderboard could rank (`segments/segment-leaderboards`): one the viewer may see, not from an excluded activity, not by an athlete who hides from leaderboards, and within its sport group's speed cap. Each such effort SHALL count once, including several in one activity.

#### Scenario: Laps in one ride
- **WHEN** an athlete's one ride passes a segment four times and another ride passes it once
- **THEN** their count is 5

#### Scenario: Hidden athlete
- **WHEN** the athlete with the most efforts hides from leaderboards
- **THEN** no viewer's Mayors board lists them

#### Scenario: Excluded activity
- **WHEN** an athlete excludes from leaderboards a ride holding three of their seven efforts on a segment
- **THEN** their count is 4

#### Scenario: Private efforts
- **WHEN** an athlete has four public efforts and three Followers-only efforts on a segment
- **THEN** a follower sees a count of 7, and a viewer who doesn't follow them sees 4

### Requirement: Most efforts first, first to reach it wins
A board SHALL hold one entry per athlete with at least one counted effort, ordered by count, most first. Among equal counts, the athlete who reached their count first SHALL be first: the one whose latest counted effort in the window started at the earlier moment. Among those still equal, the athlete whose first counted effort in the window started earlier SHALL be first, and then the order SHALL be by athlete identifier, so it is stable from page to page. Each entry SHALL give its rank, the athlete, the count and the local date of their latest counted effort in the window, as the date the count was reached. An entry SHALL NOT give a time of day, and the board SHALL use effort start times only to order its entries.

#### Scenario: More efforts
- **WHEN** athlete A has 12 counted efforts and B has 9
- **THEN** A is ranked 1st and is the segment's Mayor, and B is 2nd

#### Scenario: First to reach it
- **WHEN** A and B both have 10 counted efforts, A's latest on 3 October and B's on 9 October
- **THEN** A is ranked 1st and B 2nd

#### Scenario: First to reach it on the same day
- **WHEN** A and B both have 10 counted efforts with their latest on 9 October, B's starting at 06:45 and A's at 17:20
- **THEN** B is ranked 1st and A 2nd, and both entries give 9 October with no time

#### Scenario: Holding the count by riding again
- **WHEN** A, ranked 1st with 10 efforts, rides the segment again
- **THEN** A has 11 efforts and stays 1st

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

### Requirement: Mayors boards share the leaderboard filters
A Mayors board SHALL take the same board type, scope and bracket choices as a time leaderboard, with the same meaning: one activity type's board (`segments/segment-leaderboards`), listed and defaulted as leaderboards are; everyone or only the viewer and the athletes they effectively follow; and any combination of age group, weight class and gender. Asking for an unknown board type, scope or bracket value, or the following scope without being signed in, SHALL be refused.

#### Scenario: E-bike board
- **WHEN** an athlete has 20 e-mountain-bike efforts and 2 other rides on a ride segment
- **THEN** the `eBike` board counts 20 for them and the `ride` board counts 2

#### Scenario: Default type
- **WHEN** a viewer asks for a paddle segment's Mayors without a type and may see 12 `kayak` and 4 `canoe` efforts on it
- **THEN** the `kayak` board is served, and `kayak` and `canoe` are listed

#### Scenario: Following scope
- **WHEN** a signed-in viewer asks for the following scope
- **THEN** the board holds only the viewer and athletes whose follow by the viewer counts, ranked among themselves

#### Scenario: Signed out following
- **WHEN** a viewer who isn't signed in asks for the following scope
- **THEN** the AppView refuses the request

### Requirement: Mayors boards are paged and show the viewer's own place
A board SHALL be paged in its order, at most 100 entries per page, with a cursor. Ranks SHALL count every athlete on the board, not only those on the page. For a signed-in viewer who is on the board, the response SHALL also give the viewer's own entry with its rank, whichever page is asked for.

#### Scenario: Second page
- **WHEN** a viewer asks for the second page of 30 on a board of 75 athletes with distinct counts
- **THEN** the first entry on it is ranked 31st

#### Scenario: Viewer further down
- **WHEN** a viewer ranked 58th opens the first page
- **THEN** the response holds the first page and the viewer's own entry ranked 58th

### Requirement: The segment screen shows its Mayor
The screen of an `active` segment SHALL show, in a card in the boards row beside the Leaderboard card (`segments/segment-page`), the Mayor for the last 90 days of the board type it carries, or of the AppView's default type when it carries none, with their count, or that it has none yet, and the viewer's own count when they're on that board. The card SHALL open the Mayors screen on that type. The screen of a segment of any other status SHALL show no Mayor.

#### Scenario: Active segment
- **WHEN** a viewer opens an active segment whose last-90-days board is led by A with 14 efforts, and the viewer has 6
- **THEN** the Mayor card beside the Leaderboard card shows A as Mayor with 14 efforts and the viewer's 6

#### Scenario: From a canoe trip
- **WHEN** a viewer opens a paddle segment from a canoe activity
- **THEN** the screen shows the `canoe` board's Mayor, and the Mayors screen opens on `canoe`

#### Scenario: Shared Mayor on the segment screen
- **WHEN** two athletes have equal counts reached on the same day
- **THEN** the screen shows the one who reached it first, the board's one Mayor

#### Scenario: No efforts yet
- **WHEN** an active segment has no counted efforts in the last 90 days
- **THEN** the screen shows that it has no Mayor yet and still opens the Mayors screen

#### Scenario: Proposed segment
- **WHEN** a viewer opens a proposed segment
- **THEN** the screen shows no Mayor

### Requirement: The Mayors screen offers the board's choices
The Mayors screen SHALL open on the board type it was given, or the AppView's default, and SHALL let the viewer choose the window (the last 90 days by default), the scope, the age group, weight class and gender, and the board type, as a menu of the listed types when there's more than one. A new choice SHALL show its board from the first page.

#### Scenario: Open from segment
- **WHEN** a viewer opens the Mayors screen from an active segment
- **THEN** the last-90-days, everyone board is shown

#### Scenario: Choosing a window
- **WHEN** a viewer on the last-90-days board chooses this week
- **THEN** the earlier entries leave the screen, and this week's board shows from its first page once it answers

#### Scenario: Choosing a type
- **WHEN** a viewer on a hike segment's `hike` board chooses `walk`
- **THEN** the `hike` entries leave the screen, and the `walk` board shows from its first page once it answers

### Requirement: The Mayors screen lists the board
The Mayors screen SHALL list the entries with rank, athlete, count and the date it was reached. It SHALL load further pages as the viewer scrolls and SHALL show the viewer's own entry when it isn't among those listed. Tapping an entry SHALL open the athlete's profile. A failed load SHALL show an error with a retry.

#### Scenario: Own place pinned
- **WHEN** a viewer ranked 58th opens the Mayors screen
- **THEN** their own entry is shown apart from the first page

#### Scenario: Open an athlete
- **WHEN** a viewer taps an entry
- **THEN** the athlete's profile opens

### Requirement: The Mayors screen is native on iOS
On iOS a segment's Mayors board SHALL be drawn with the system's own list and navigation bar, titled "Mayors of" the segment's name, listing in the shared screen's words each entry's rank (the Mayor's crowned in the accent colour, the rest plain), picture, name, "Reached" with a day ("Mayor · Reached" for the Mayor), and count, opening the athlete's profile; the viewer's own place under "Your place"; and the empty message, page by page following `ui/content-loading`.

#### Scenario: Two entries
- **WHEN** a viewer opens a Mayors board where Bob has 14 efforts and Alice 9
- **THEN** it lists Bob crowned, read as "Mayor", with "Mayor · Reached" and a day and "14 efforts", and 2 Alice, unaccented, with "Reached" and a day and "9 efforts"

#### Scenario: Opening an athlete on iOS
- **WHEN** the viewer taps Alice's entry
- **THEN** Alice's profile opens, as from the shared screen

#### Scenario: No efforts in the window
- **WHEN** no one has an effort in the chosen window
- **THEN** it reads "No one has an effort here in this period"

### Requirement: The Mayors screen's choices on iOS
On iOS the Mayors board SHALL offer its choices as the leaderboard's row of chips, in a bar fixed under the navigation bar: the window (the last 90 days by default, or today, all time, this or last year, month or week), the scope, the board's type only when two or more are listed, and the age, weight and gender, each a menu showing its choice.

#### Scenario: Choosing a window on iOS
- **WHEN** a viewer on the last-90-days board opens the window menu and chooses This week
- **THEN** the earlier entries leave the screen, and this week's board shows from its first page once it answers

#### Scenario: Choosing Following on iOS
- **WHEN** a viewer opens the scope menu and chooses Following
- **THEN** the menu reads Following and is filled, and the following board shows from its first page once it answers

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

### Requirement: A Mayors board has one Mayor
Every place on a Mayors board SHALL belong to exactly one athlete: no two entries SHALL share a rank, and the entry ranked 1st SHALL be the segment's one Mayor for that board. Ranks SHALL be the same whichever page an entry is read on, and the viewer's own entry SHALL carry the rank it has on the board.

#### Scenario: Equal counts on one day
- **WHEN** A and B both reach 10 efforts with their latest on 9 October, A's starting at 07:10 and B's at 18:30, and C has 8
- **THEN** A is ranked 1st and is the Mayor, B is 2nd and C is 3rd

#### Scenario: The viewer's own place
- **WHEN** the viewer is B in that board and isn't among the entries listed
- **THEN** their own entry is ranked 2nd

### Requirement: The Mayors screen crowns its Mayor
On every platform the Mayors screen SHALL show the entry ranked 1st with a crown in place of its rank number, in the board's accent and taking the rank column's width, so the rows' names stay aligned. Assistive technology SHALL read the crown as "Mayor". That entry's line SHALL read "Mayor · Reached" with the day it reached its count. Every other entry SHALL show its rank number, unaccented as before, and SHALL NOT be crowned. When the viewer is the Mayor, their crowned row SHALL also be marked as their own.

#### Scenario: Crowned first entry
- **WHEN** a viewer opens a Mayors board where Bob has 14 efforts, reached on 3 November, Alice 14, reached on 9 November, and Cy 11
- **THEN** Bob's row shows a crown where its rank would be, read as "Mayor", and the line "Mayor · Reached Nov 3", and Alice's and Cy's rows show 2 and 3

#### Scenario: No efforts, no crown
- **WHEN** no one has an effort in the chosen window
- **THEN** the screen shows its empty message and no crown

#### Scenario: The viewer is Mayor
- **WHEN** the viewer is ranked 1st
- **THEN** their row is crowned and marked as their own, and no place is pinned
