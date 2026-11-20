## MODIFIED Requirements

### Requirement: Lift rides are efforts
A lift SHALL be matched, given efforts and given a status exactly as a run segment is, under the same consent, tests and visibility (`segments/segment-efforts`). Its efforts SHALL count toward its status. An operator MAY pin a lift's status or name as a run's (`segments/segment-pins`). A lift pinned `active` SHALL be matched and listed like an active lift, and SHALL still never be ranked, put on a Mayors board or take name proposals. A `duplicate` pin on a lift SHALL name another lift.

#### Scenario: Riding a gondola
- **WHEN** an athlete with matching on rides an active gondola from bottom to top on a ski day
- **THEN** their activity gets an effort on the gondola, visible at the narrowest of the activity's, Map and Pace/Speed visibilities

#### Scenario: Riding down
- **WHEN** an athlete rides a gondola down from top to bottom
- **THEN** that ride gives no effort on it

#### Scenario: Duplicate lift pinned active
- **WHEN** an importer lift marked `duplicate` of another lift is pinned `active`
- **THEN** it is matched against segment tracks and its efforts show in the activity's Lifts section, with no leaderboard, Mayor or naming

#### Scenario: Lift pinned duplicate of a run
- **WHEN** an operator pins a lift `duplicate` of an `alpine` run
- **THEN** the pin is refused

#### Scenario: Lift name pinned
- **WHEN** an operator pins the name "Summit Express" on a lift whose record names it "Lift 7"
- **THEN** every view serves the lift as "Summit Express", and it still takes no name proposals
