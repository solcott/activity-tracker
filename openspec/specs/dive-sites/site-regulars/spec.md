# dive-sites/site-regulars Specification

## Purpose

The Regulars board of a dive site: which athletes have dived it most, counted from visits the viewer may see.

## Requirements

### Requirement: Only active sites have Regulars
`feed.getDiveSiteRegulars` SHALL return a board only while the site is `active`, and no entries otherwise. A site that isn't indexed or isn't served SHALL be `NotFound`.

#### Scenario: Proposed site
- **WHEN** a viewer asks for the Regulars of a proposed site
- **THEN** the AppView returns no entries

### Requirement: Regulars count visits the viewer may see
A board SHALL count, per athlete, their visits to the site in the window that the viewer may see, leaving out athletes who chose not to appear on leaderboards and activities excluded from leaderboards. It SHALL order athletes by count, most first, the athlete who reached their count on an earlier date first, with equal counts reached on one date sharing a rank.

#### Scenario: Hidden athlete
- **WHEN** an athlete with the most dives at a site has chosen not to appear on leaderboards
- **THEN** the board leaves them out, to everyone, themselves included

#### Scenario: Private dives
- **WHEN** bob has six visits, four from Only me dives
- **THEN** another viewer sees bob with a count of two

### Requirement: Regulars boards take a window and scope
A board SHALL cover all time by default, or this or last calendar year, by the dive's local date. It SHALL cover everyone, or only the viewer and the athletes they effectively follow; asking for the latter signed out SHALL be refused. It SHALL be paged, at most 100 entries, with a cursor, ranks counting the whole board, and include the signed-in viewer's own entry.

#### Scenario: Following scope
- **WHEN** a signed-in viewer asks for this year's following board
- **THEN** it lists only them and those they effectively follow who dived the site this year

### Requirement: The site screen shows its Regulars
The screen of an `active` site SHALL show its top regular for all time with their count, and open the full board, where the viewer chooses window and scope and more pages load as they scroll.

#### Scenario: Opening the board
- **WHEN** a viewer taps the top regular on an active site
- **THEN** the Regulars screen opens on all time, everyone

### Requirement: The Regulars screen is native on iOS
On iOS a site's Regulars board SHALL be drawn with the system's own list and bar, titled "Regulars of" the site's name, with the scope as a segmented choice and the window (All time, This year, Last year) as a menu, listing in the shared screen's words each diver's rank (the first accented), picture, name, "Reached" with a day, and dives, opening their profile; the viewer's place under "Your place"; and the empty message, paged per `ui/content-loading`.

#### Scenario: A regular
- **WHEN** a viewer opens a site's Regulars where Bob has 3 dives
- **THEN** it lists 1 Bob "3 dives" with "Reached" and the day he reached them

#### Scenario: Choosing last year
- **WHEN** a viewer on the all-time board chooses Last year from the window menu
- **THEN** the all-time entries leave the screen, and last year's board shows from its first page once it answers

#### Scenario: No dives in the window
- **WHEN** no one has dived the site in the chosen window
- **THEN** it reads "No one has dived here in this period"
