# Spec Delta

## MODIFIED Requirements

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

### Requirement: The segment screen shows its Mayor
The screen of an `active` segment SHALL show the Mayor for the last 90 days of the board type it carries, or of the AppView's default type when it carries none, with their count, or that it has none yet, and the viewer's own count when they're on that board. It SHALL open the Mayors screen on that type. The screen of a segment of any other status SHALL show no Mayor.

#### Scenario: Active segment
- **WHEN** a viewer opens an active segment whose last-90-days board is led by A with 14 efforts, and the viewer has 6
- **THEN** the screen shows A as Mayor with 14 efforts and the viewer's 6

#### Scenario: From a canoe trip
- **WHEN** a viewer opens a paddle segment from a canoe activity
- **THEN** the screen shows the `canoe` board's Mayor, and the Mayors screen opens on `canoe`

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
