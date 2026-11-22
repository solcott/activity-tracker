# Spec Delta

## ADDED Requirements

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
