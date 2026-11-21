# Spec Delta

## ADDED Requirements

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
