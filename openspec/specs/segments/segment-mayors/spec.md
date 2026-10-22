# segments/segment-mayors Specification

## Purpose

Defines segment Mayors: a board per segment that ranks athletes by how many efforts they have on it over a window, whose leader is the segment's Mayor, and how clients show it.

## Requirements

### Requirement: Only active segments have Mayors
`feed.getSegmentMayors` SHALL return a segment's board only while the segment is `active`. For a segment of any other status it SHALL return no entries. A segment that isn't indexed or isn't served to the viewer SHALL be `NotFound`.

#### Scenario: Proposed segment
- **WHEN** a viewer asks for the Mayors of a proposed segment that three efforts are on
- **THEN** the AppView returns no entries

#### Scenario: Unknown segment
- **WHEN** a viewer asks for the Mayors of a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

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
A board SHALL hold one entry per athlete with at least one counted effort, ordered by count, most first. Among equal counts, the athlete whose count was reached on an earlier date SHALL be first. The date it was reached is the local date of the athlete's latest counted effort in the window. Each entry SHALL give its rank, the athlete, the count and that date, and SHALL NOT give a time of day.

#### Scenario: More efforts
- **WHEN** athlete A has 12 counted efforts and B has 9
- **THEN** A is ranked 1st and is the segment's Mayor, and B is 2nd

#### Scenario: First to reach it
- **WHEN** A and B both have 10 counted efforts, A's latest on 3 October and B's on 9 October
- **THEN** A is ranked 1st and B 2nd

#### Scenario: Holding the count by riding again
- **WHEN** A, ranked 1st with 10 efforts, rides the segment again
- **THEN** A has 11 efforts and stays 1st

### Requirement: Same count on the same day shares a rank
Athletes with equal counts reached on the same date SHALL share a rank, and the next rank SHALL skip accordingly. Their order among themselves SHALL be stable from page to page.

#### Scenario: Shared Mayor
- **WHEN** A and B both reach 10 efforts with their latest on 9 October, and C has 8
- **THEN** A and B are both ranked 1st, and C is 3rd

### Requirement: Mayors boards cover a window
A board SHALL cover the last 90 days by default, or, when asked, all time, this or last calendar year, this or last calendar month, or this or last week. An effort SHALL belong to a window by its activity's local date, compared with the AppView's current date in UTC. The last 90 days SHALL be today and the 89 days before it. Weeks SHALL start on Monday. An unknown window SHALL be refused.

#### Scenario: Default window
- **WHEN** a viewer asks for a segment's Mayors without a window on 22 October 2026
- **THEN** the board counts efforts dated 25 July 2026 to 22 October 2026

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
A Mayors board SHALL take the same scope, e-bike and bracket choices as a time leaderboard, with the same meaning: everyone or only the viewer and the athletes they effectively follow; a `ride` segment's standard or e-bike board; and any combination of age group, weight class and gender. Asking for the following scope without being signed in, or for an unknown scope or bracket value, SHALL be refused.

#### Scenario: E-bike board
- **WHEN** an athlete has 20 e-mountain-bike efforts and 2 standard ones on a ride segment
- **THEN** the e-bike board counts 20 for them and the standard board counts 2

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
The screen of an `active` segment SHALL show the segment's Mayor for the last 90 days with their count, or that it has none yet, and the viewer's own count when they're on that board. It SHALL open the Mayors screen. The screen of a segment of any other status SHALL show no Mayor.

#### Scenario: Active segment
- **WHEN** a viewer opens an active segment whose last-90-days board is led by A with 14 efforts, and the viewer has 6
- **THEN** the screen shows A as Mayor with 14 efforts and the viewer's 6

#### Scenario: Shared Mayor on the segment screen
- **WHEN** two athletes share rank 1
- **THEN** the screen shows the first of them listed on the board

#### Scenario: No efforts yet
- **WHEN** an active segment has no counted efforts in the last 90 days
- **THEN** the screen shows that it has no Mayor yet and still opens the Mayors screen

#### Scenario: Proposed segment
- **WHEN** a viewer opens a proposed segment
- **THEN** the screen shows no Mayor

### Requirement: The Mayors screen offers the board's choices
The Mayors screen SHALL let the viewer choose the window (the last 90 days by default), the scope, and the age group, weight class and gender, and on a `ride` segment the e-bike board. A new choice SHALL show its board from the first page.

#### Scenario: Open from segment
- **WHEN** a viewer opens the Mayors screen from an active segment
- **THEN** the last-90-days, everyone board is shown

#### Scenario: Choosing a window
- **WHEN** a viewer on the last-90-days board chooses this week
- **THEN** the earlier entries leave the screen, and this week's board shows from its first page once it answers

### Requirement: The Mayors screen lists the board
The Mayors screen SHALL list the entries with rank, athlete, count and the date it was reached. It SHALL load further pages as the viewer scrolls and SHALL show the viewer's own entry when it isn't among those listed. Tapping an entry SHALL open the athlete's profile. A failed load SHALL show an error with a retry.

#### Scenario: Own place pinned
- **WHEN** a viewer ranked 58th opens the Mayors screen
- **THEN** their own entry is shown apart from the first page

#### Scenario: Open an athlete
- **WHEN** a viewer taps an entry
- **THEN** the athlete's profile opens
