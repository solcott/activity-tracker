## ADDED Requirements

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Same count on the same day shares a rank
**Reason**: A segment has one Mayor, so a Mayors board never shares a rank; ties are broken by the time each count was reached ("A Mayors board has one Mayor").
**Migration**: None for clients: `rank` stays an integer. Dive site Regulars keeps its own shared ranks (`dive-sites/site-regulars`).
