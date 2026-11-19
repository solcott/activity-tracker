## MODIFIED Requirements

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
