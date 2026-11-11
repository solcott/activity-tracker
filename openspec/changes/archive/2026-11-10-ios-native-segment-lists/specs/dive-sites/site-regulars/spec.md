# Spec Delta

## ADDED Requirements

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
