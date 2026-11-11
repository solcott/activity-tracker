# Spec Delta

## ADDED Requirements

### Requirement: The Mayors screen is native on iOS
On iOS a segment's Mayors board SHALL be drawn with the system's own list and navigation bar, titled "Mayors of" the segment's name, listing in the shared screen's words each entry's rank (the first accented), picture, name, "Reached" with a day, and count, opening the athlete's profile; the viewer's own place under "Your place"; and the empty message, page by page following `ui/content-loading`.

#### Scenario: Two entries
- **WHEN** a viewer opens a Mayors board where Bob has 14 efforts and Alice 9
- **THEN** it lists 1 Bob "14 efforts" and 2 Alice "9 efforts", each with "Reached" and a day, and only Bob's rank in the accent colour

#### Scenario: Opening an athlete on iOS
- **WHEN** the viewer taps Alice's entry
- **THEN** Alice's profile opens, as from the shared screen

#### Scenario: No efforts in the window
- **WHEN** no one has an effort in the chosen window
- **THEN** it reads "No one has an effort here in this period"

### Requirement: The Mayors screen's choices on iOS
On iOS the Mayors board SHALL offer the scope as a segmented choice, and the window (the last 90 days by default, or all time, this or last year, month or week), the board's type when two or more are listed, and the age, weight and gender brackets as menus, each showing its choice. The type SHALL read as a label when one is listed.

#### Scenario: Choosing a window on iOS
- **WHEN** a viewer on the last-90-days board opens the window menu and chooses This week
- **THEN** the earlier entries leave the screen, and this week's board shows from its first page once it answers
