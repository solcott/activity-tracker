# segments/segment-leaderboards Specification

## Purpose

Defines segment leaderboards: which boards a segment has, which efforts are ranked on them and in what order, what each viewer sees, how athletes keep themselves or one activity off them, and how implausible efforts are kept off.

## Requirements

### Requirement: Only active segments have leaderboards
`feed.getSegmentLeaderboard` SHALL return a segment's board only while the segment is `active`. For a segment of any other status it SHALL return no entries. A segment that isn't indexed or isn't served to the viewer SHALL be `NotFound`.

#### Scenario: Proposed segment
- **WHEN** a viewer asks for the leaderboard of a proposed segment that three efforts are on
- **THEN** the AppView returns no entries

#### Scenario: Unknown segment
- **WHEN** a viewer asks for the leaderboard of a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

### Requirement: A board ranks each athlete's best effort
A leaderboard SHALL hold one entry per athlete: their fastest ranked effort on the segment that the viewer may see, fastest first. Each entry SHALL give its rank, the athlete, the effort's elapsed time, its activity and the activity's date, and SHALL NOT give a time of day. Athletes with equal times SHALL share a rank, the next rank SHALL skip accordingly, and the earlier effort SHALL be listed first. An athlete with two equally fast efforts SHALL be shown with the earlier one.

#### Scenario: Three athletes
- **WHEN** athlete A has efforts of 300 s and 280 s, B one of 290 s and C one of 310 s on a segment
- **THEN** the board lists A (280 s) 1st, B 2nd and C 3rd, each once

#### Scenario: Tie
- **WHEN** two athletes both have a best of 290 s, after one at 280 s
- **THEN** both are ranked 2nd, the earlier effort listed first, and the next athlete is 4th

### Requirement: Boards are paged and show the viewer's own place
A leaderboard SHALL be paged fastest first, at most 100 entries per page, with a cursor. Ranks SHALL count every athlete on the board, not only those on the page. For a signed-in viewer who is on the board, the response SHALL also give the viewer's own entry with its rank, whichever page is asked for.

#### Scenario: Second page
- **WHEN** a viewer asks for the second page of 30 on a board of 75 athletes
- **THEN** the first entry on it is ranked 31st

#### Scenario: Viewer far down
- **WHEN** a viewer ranked 140th opens the first page
- **THEN** the response holds the first page and the viewer's own entry ranked 140th

### Requirement: Boards are filtered by period
A leaderboard SHALL cover all time, the current calendar year or the current calendar month, all time by default. An effort SHALL belong to a period by its activity's local date, compared with the AppView's current date in UTC.

#### Scenario: This month
- **WHEN** a viewer asks for this month's board and an athlete's best is from last year, but they rode it slower this month
- **THEN** the athlete is ranked by this month's effort

### Requirement: Boards can be limited to people the viewer follows
A leaderboard SHALL cover everyone by default, or, when asked, only the viewer and the athletes they effectively follow. Asking for the following scope without being signed in SHALL be refused.

#### Scenario: Following scope
- **WHEN** a signed-in viewer asks for the following scope
- **THEN** the board holds only the viewer and athletes whose follow by the viewer counts, ranked among themselves

#### Scenario: Signed out
- **WHEN** a viewer who isn't signed in asks for the following scope
- **THEN** the AppView refuses the request

### Requirement: E-bike efforts have their own board
A `ride` segment SHALL have a standard board and an e-bike board. E-bike efforts SHALL be ranked only on the e-bike board, and other efforts only on the standard board. Segments of other sport groups SHALL have only the standard board.

#### Scenario: E-mountain-bike ride
- **WHEN** an athlete's e-mountain-bike ride has the fastest time on a ride segment
- **THEN** it leads the e-bike board and isn't on the standard board

### Requirement: Viewers see only the efforts they may see
A leaderboard SHALL hold only efforts the viewer may see under `segments/segment-efforts`: at the narrowest of the activity's, its Map category's and its Pace/Speed category's visibility, never across a block, and never from an account or activity that isn't served. A viewer who isn't signed in SHALL see only fully public efforts. An athlete's best entry SHALL be their fastest effort that the viewer may see, so different viewers MAY see different times for the same athlete.

#### Scenario: Followers-only map
- **WHEN** an athlete's fastest effort is on a Public run whose Map category is Followers, and a slower one on a fully Public run
- **THEN** their followers see the fastest on the board, and others see the slower one

#### Scenario: Only me
- **WHEN** an athlete's only effort on a segment is on an Only me ride
- **THEN** the athlete sees themselves on the board, and no one else does

#### Scenario: Blocked
- **WHEN** an athlete has blocked the viewer
- **THEN** the viewer's board doesn't list the athlete, and the athlete's board doesn't list the viewer

### Requirement: Athletes can keep off every leaderboard
An athlete SHALL be able to choose, in their `actor.segmentPrefs`, not to appear on leaderboards. Athletes appear by default. While the athlete has chosen this, no board SHALL list them, to anyone, themselves included, and they SHALL keep their efforts and PRs on their own activities. The AppView SHALL NOT serve the choice to anyone else, and segment statuses SHALL still count the athlete's efforts.

#### Scenario: Hidden athlete
- **WHEN** an athlete with the fastest Public effort on a segment hides from leaderboards
- **THEN** no viewer's board lists them, and their activity still shows the effort marked as their PR

#### Scenario: Back on
- **WHEN** the athlete turns the choice off again
- **THEN** once the AppView indexes the setting, boards list them again

### Requirement: Athletes can keep one activity off leaderboards
An athlete SHALL be able to mark one of their activities as excluded from leaderboards. The mark SHALL be on its segment track, so it SHALL be private and SHALL exist only while the athlete's matching is on. No board SHALL rank an effort from an excluded activity, and the activity SHALL keep its efforts.

#### Scenario: Group ride
- **WHEN** an athlete excludes a drafted group ride from leaderboards
- **THEN** its efforts are on no board, and the ride still lists them

#### Scenario: Next best
- **WHEN** the excluded ride held the athlete's best time on a segment
- **THEN** boards rank the athlete by their next fastest effort that isn't excluded

### Requirement: Implausibly fast efforts are not ranked
An effort SHALL NOT be ranked when its average speed over the segment's computed length is above its sport group's cap: 100 km/h for `ride`, 40 km/h for `run`, 30 km/h for `hike`, 120 km/h for `ski` and 25 km/h for `paddle`. It SHALL stay on its activity. The caps SHALL apply as they stand when a board is read, without rematching.

#### Scenario: Run in a car
- **WHEN** a run's effort covers a 2 km segment in 60 s
- **THEN** it is on no board, and the run still lists it

#### Scenario: Fast descent
- **WHEN** a ride covers a 3 km descent at an average of 85 km/h
- **THEN** the effort is ranked

### Requirement: The leaderboard screen
The segment screen of an `active` segment SHALL open its leaderboard. The leaderboard screen SHALL list the entries with rank, athlete, time and date, and SHALL let the viewer choose the period and scope. For a `ride` segment it SHALL also let the viewer choose the e-bike board. It SHALL load further pages as the viewer scrolls, and SHALL show the viewer's own entry when it isn't among those listed. Tapping an entry SHALL open its activity. A failed load SHALL show an error with a retry.

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

### Requirement: The leaderboard choice is in Settings
While the athlete's segment matching is on, Settings SHALL offer showing them on leaderboards, on by default. Saving it SHALL be a direct write that reports a failure and keeps the earlier choice, like the matching setting, and SHALL keep the matching setting as it is.

#### Scenario: Hide
- **WHEN** an athlete with matching on turns off showing them on leaderboards
- **THEN** their `actor.segmentPrefs` says they're hidden and matching is still on

#### Scenario: Matching off
- **WHEN** an athlete's matching is off
- **THEN** Settings doesn't offer the leaderboard choice

### Requirement: The exclusion is in the activity editor
The activity editor SHALL offer excluding the activity from leaderboards on the athlete's own activity that has a segment track, showing the activity's current choice. It SHALL NOT offer it while that choice can't be read. Saving SHALL change only the mark on the segment track and SHALL queue the write like other edits.

#### Scenario: Exclude a ride
- **WHEN** an athlete edits their ride and excludes it from leaderboards
- **THEN** its segment track is rewritten with the mark, with the same points

#### Scenario: No segment track
- **WHEN** an athlete edits an activity without a segment track, such as one recorded while matching was off
- **THEN** the editor doesn't offer the exclusion
