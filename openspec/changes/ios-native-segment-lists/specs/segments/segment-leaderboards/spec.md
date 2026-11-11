# Spec Delta

## ADDED Requirements

### Requirement: The leaderboard screen is native on iOS
On iOS a segment's leaderboard SHALL be drawn with the system's own list, segmented controls, menus and navigation bar, titled with the segment's name, and SHALL list what the shared screen lists, in the same words: each entry's rank, the athlete's picture and name, the day of the effort and its time, opening the activity, the viewer's own place under "Your place" when it isn't among those loaded, and the empty message. It SHALL load page by page following `ui/content-loading`.

#### Scenario: Three entries
- **WHEN** a viewer opens a leaderboard where Bob rode it in 3:50, Alice in 4:05 and Carol in 4:22
- **THEN** it lists 1 Bob 3:50, 2 Alice 4:05 and 3 Carol 4:22, each with the day of the effort, and the three ranks in the accent colour

#### Scenario: Opening an entry
- **WHEN** the viewer taps Bob's entry
- **THEN** the activity his effort came from opens, as from the shared screen

#### Scenario: Own place pinned on iOS
- **WHEN** a viewer ranked 140th opens the leaderboard
- **THEN** their own entry shows under "Your place", below the first page's entries

#### Scenario: An empty board
- **WHEN** no one the viewer may see has an effort for the chosen filters
- **THEN** it reads "No one is on this leaderboard yet"

### Requirement: The leaderboard's choices on iOS
On iOS the leaderboard SHALL offer the period (All time, This year, This month) and the scope (Everyone, Following) as segmented choices, the board's type as a menu when two or more are listed and as a label when one is, and the age, weight and gender brackets as menus, each showing its choice and marked while it narrows the board. Weight classes SHALL read in the viewer's units.

#### Scenario: Choosing a period
- **WHEN** a viewer on the all-time board chooses This month
- **THEN** the all-time entries leave the screen, and this month's board shows from its first page once it answers

#### Scenario: Two types
- **WHEN** a ride segment lists the `ride` and `eBike` boards
- **THEN** the type reads "Ride" and opens a menu of Ride and E-bike

#### Scenario: Weight in pounds on iOS
- **WHEN** a viewer whose units are imperial opens the weight menu
- **THEN** it offers "All weights" and the classes in pounds, such as "Under 121 lb"
