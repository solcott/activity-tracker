# Spec Delta

## MODIFIED Requirements

### Requirement: The segment screen shows its Mayor
The screen of an `active` segment SHALL show, in a card in the boards row beside the Leaderboard card (`segments/segment-page`), the Mayor for the last 90 days of the board type it carries, or of the AppView's default type when it carries none, with their count, or that it has none yet, and the viewer's own count when they're on that board. The card SHALL open the Mayors screen on that type. The screen of a segment of any other status SHALL show no Mayor.

#### Scenario: Active segment
- **WHEN** a viewer opens an active segment whose last-90-days board is led by A with 14 efforts, and the viewer has 6
- **THEN** the Mayor card beside the Leaderboard card shows A as Mayor with 14 efforts and the viewer's 6

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
