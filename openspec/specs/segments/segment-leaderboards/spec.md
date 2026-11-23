# segments/segment-leaderboards Specification

## Purpose

Defines segment leaderboards: which boards a segment has, which efforts are ranked on them and in what order, what each viewer sees, how athletes keep themselves or one activity off them, and how implausible efforts are kept off.

## Requirements

### Requirement: Only active segments have leaderboards
`feed.getSegmentLeaderboard` SHALL return a segment's board only while the segment is `active` and of kind `run`, whether its status is derived or pinned (`segments/segment-pins`). Any other segment, a lift too, SHALL return no entries, and one that isn't indexed or served to the viewer SHALL be `NotFound`. An `archived` pin SHALL keep its efforts, so its board returns once it is `active` again; a `duplicate` pin SHALL remove them, so its board holds only efforts matched after it is live again.

#### Scenario: Proposed segment
- **WHEN** a viewer asks for the leaderboard of a proposed segment that three efforts are on
- **THEN** the AppView returns no entries

#### Scenario: Unknown segment
- **WHEN** a viewer asks for the leaderboard of a segment URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

#### Scenario: Active lift
- **WHEN** a viewer asks for the leaderboard of an active lift that many athletes have ridden
- **THEN** the AppView returns no entries

#### Scenario: Active alpine run
- **WHEN** a viewer asks for the leaderboard of an active `alpine` run
- **THEN** the AppView returns its board like any other segment's, with `ski` and `snowboard` as separate board types

#### Scenario: Pinned active with one rider
- **WHEN** a proposed segment with efforts from one athlete is pinned `active`
- **THEN** its leaderboard lists that athlete's best effort the viewer may see

#### Scenario: Pinned archived and unpinned
- **WHEN** an active segment with 40 athletes on its board is pinned `archived`, and later unpinned
- **THEN** its board returns no entries while pinned, and after the unpin lists again each of the 40 who still has an effort on it

#### Scenario: Pinned duplicate
- **WHEN** an active segment is pinned `duplicate` of another
- **THEN** its board returns no entries, and its efforts no longer rank on any board or count for its Mayors

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

### Requirement: Boards can be limited to people the viewer follows
A leaderboard SHALL cover everyone by default, or, when asked, only the viewer and the athletes they effectively follow. Asking for the following scope without being signed in SHALL be refused.

#### Scenario: Following scope
- **WHEN** a signed-in viewer asks for the following scope
- **THEN** the board holds only the viewer and athletes whose follow by the viewer counts, ranked among themselves

#### Scenario: Signed out
- **WHEN** a viewer who isn't signed in asks for the following scope
- **THEN** the AppView refuses the request

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

### Requirement: Boards can be filtered by age group, weight class and gender
A leaderboard SHALL cover every bracket by default, or, when asked, only athletes in a given age group, weight class or gender bucket (`segments/leaderboard-brackets`), with any combination of the three. A filtered board SHALL list only athletes whose indexed brackets match every filter, ranked among themselves, together with the period, scope and e-bike choices. An unknown bracket value SHALL be refused.

#### Scenario: Age group board
- **WHEN** a viewer asks for the `35-44` board and athletes A (`35-44`), B (`45-54`) and C (no brackets record) have efforts on it
- **THEN** the board lists only A, ranked 1st

#### Scenario: Combined filters
- **WHEN** a viewer asks for the `female`, `55-64` kg board for this year
- **THEN** it lists only athletes in both brackets, by their efforts this year

#### Scenario: Missing bracket
- **WHEN** an athlete has brackets on but gave no weight
- **THEN** they are on age-group and gender boards and on no weight-class board

#### Scenario: Unknown value
- **WHEN** a viewer asks for age group `40-49`
- **THEN** the AppView refuses the request

#### Scenario: Viewer's own entry
- **WHEN** a signed-in viewer outside the `25-34` group asks for the `25-34` board
- **THEN** the response has no viewer entry

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
An effort SHALL NOT be ranked when its average speed over the segment's computed length is above its sport group's cap: 100 km/h for `ride`, 40 km/h for `run`, 30 km/h for `hike`, 120 km/h for `ski`, 120 km/h for `alpine` and 25 km/h for `paddle`. It SHALL stay on its activity. The caps SHALL apply as they stand when a board is read, without rematching.

#### Scenario: Run in a car
- **WHEN** a run's effort covers a 2 km segment in 60 s
- **THEN** it is on no board, and the run still lists it

#### Scenario: Fast descent
- **WHEN** a ride covers a 3 km descent at an average of 85 km/h
- **THEN** the effort is ranked

#### Scenario: Ski run on a snowmobile
- **WHEN** an `alpine` effort covers a 1 km run in 25 s, an average of 144 km/h
- **THEN** it is on no board, and the ski day still lists it

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

### Requirement: The leaderboard screen offers the bracket filters
The leaderboard screen SHALL let the viewer choose an age group, a weight class and a gender, each "All" by default, showing weight classes in the viewer's units. A choice SHALL show its board from the first page, as a new period does.

#### Scenario: Choosing an age group
- **WHEN** a viewer on the all-time board chooses the `45-54` age group
- **THEN** the unfiltered entries leave the screen, and the `45-54` board shows from its first page once it answers

#### Scenario: Weight in pounds
- **WHEN** a viewer whose units are imperial opens the weight-class choice
- **THEN** the classes are labelled in pounds

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

### Requirement: The segment screen shows its leaderboard's leader
The screen of an `active` segment that isn't a lift SHALL show a Leaderboard card in the boards row (`segments/segment-page`): the all-time, everyone board's fastest time and athlete, of the board type the screen carries or the AppView's default, and the viewer's own time and place when they're on that board. The card SHALL open the leaderboard on that type. While the board hasn't loaded, or when it fails to, the card SHALL still show and open the leaderboard.

#### Scenario: Leader and viewer
- **WHEN** a viewer ranked 18th with 5:03 opens an active segment whose fastest effort is Kate's 4:12
- **THEN** the Leaderboard card shows 4:12 and Kate, and "You: 5:03 · #18"

#### Scenario: Viewer not on the board
- **WHEN** a viewer with no effort on the board opens the segment
- **THEN** the card shows the leader and no line for the viewer

#### Scenario: Empty board
- **WHEN** no one the viewer may see is on the board
- **THEN** the card says no one is on the leaderboard yet and still opens it

#### Scenario: Board fails to load
- **WHEN** the leaderboard read fails
- **THEN** the card shows "Leaderboard" alone and still opens the leaderboard

#### Scenario: Opened from an e-bike ride
- **WHEN** a viewer opens a ride segment from an e-bike activity
- **THEN** the card shows the `eBike` board's leader, and the leaderboard opens on `eBike`

### Requirement: The Trail page's segments carry their leaders
`feed.getPath` SHALL return, for each listed segment with a leaderboard, its default board's type (all time, everyone), the leader's athlete and elapsed time, and a signed-in viewer's own elapsed time and rank when they're on that board. Each SHALL match what `feed.getSegmentLeaderboard` returns for that board, under the same rules. A segment with no leaderboard or no entries SHALL have no leader.

#### Scenario: Leader and viewer
- **WHEN** a viewer ranked 18th with 5:03 asks for a path holding an active segment Kate leads with 4:12
- **THEN** that segment's entry gives the board type, Kate and 4:12, and the viewer's 5:03 at rank 18

#### Scenario: Same as the board
- **WHEN** the leader of a segment's board hides from leaderboards
- **THEN** `getPath` gives the next athlete as leader, as `getSegmentLeaderboard` lists them first

#### Scenario: Proposed segment and lift
- **WHEN** a path lists a proposed segment and an active lift
- **THEN** neither has a leader in `getPath`

#### Scenario: Signed out
- **WHEN** a signed-out viewer asks for a path
- **THEN** each leader is given with no viewer's own entry

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
