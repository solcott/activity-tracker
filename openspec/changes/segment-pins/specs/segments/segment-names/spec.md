## ADDED Requirements

### Requirement: A pinned name beats adopted names
While an operator's name pin on a segment is in effect (`segments/segment-pins`), the segment's current name SHALL be the pinned name, in every view and search that serves the current name. A pin of free text SHALL always be in effect. A pinned name SHALL NOT enter the name history, which lists only adopted proposals.

#### Scenario: Unwanted adoption overridden
- **WHEN** a segment's adopted name is "Rooty Run" and an operator pins the name "Hogback Climb"
- **THEN** every view and name search serves "Hogback Climb", and its history still lists "Rooty Run"

#### Scenario: Original restored by pin
- **WHEN** a segment's original name is "Trail 214" and its adopted name is "Wiggly Woods", and an operator pins "Trail 214"
- **THEN** its current name is "Trail 214"

### Requirement: A name pinned from a proposal follows its labels
A name pin that names one of the segment's proposals SHALL take that proposal's name. It SHALL be in effect only while the proposal and its proposer carry neither `!hide` nor `!takedown`.

#### Scenario: Pinned proposal taken down
- **WHEN** a segment's name is pinned to alice's proposal "Rooty Run", it had adopted "Wiggly Woods" before, and alice's proposal is labelled `!takedown`
- **THEN** its current name is "Wiggly Woods", and once the label is negated it is "Rooty Run" again

## MODIFIED Requirements

### Requirement: Only regulars may propose
The AppView SHALL accept a proposal only if, when it's indexed, the segment is `active` and of kind `run` with no name pin, and the proposer has efforts on it on at least a configured number of distinct local dates, 5 by default. Efforts of every visibility SHALL count, but only efforts within the sport group's speed cap. It SHALL ignore any other proposal, and SHALL NOT drop an accepted proposal when the proposer later loses efforts. A segment whose status is pinned `active` SHALL count as `active`.

#### Scenario: Five days of efforts
- **WHEN** alice has efforts on a segment on 5 different dates, two of them from Only me activities, and proposes a name
- **THEN** the AppView accepts the proposal

#### Scenario: Five efforts in one day
- **WHEN** bob has 5 efforts on a segment, all from one ride, and proposes a name
- **THEN** the AppView ignores the proposal

#### Scenario: Proposed segment
- **WHEN** an eligible athlete proposes a name for a segment whose status is `proposed`
- **THEN** the AppView ignores the proposal

#### Scenario: Lift
- **WHEN** an athlete who has ridden an active lift on 20 different dates proposes a name for it
- **THEN** the AppView ignores the proposal, and `feed.getSegment` tells them they may not propose

#### Scenario: Name pinned
- **WHEN** an eligible athlete proposes a name for an active segment whose name an operator has pinned
- **THEN** the AppView ignores the proposal

### Requirement: A proposal is adopted once enough riders agree
The AppView SHALL adopt an open proposal once it has been open at least 7 days, its up votes reach the larger of 5 and 10% (rounded up) of the distinct athletes with efforts on the segment dated in the last 365 days, and at least two-thirds of its counted votes are up. It SHALL check at least hourly, only for `active` segments with no name pin. Open proposals on a segment with a name pin SHALL stay open, and SHALL still be rejected or expire. Each threshold SHALL be configurable.

#### Scenario: Quiet trail
- **WHEN** a segment has 20 riders in the last year and a proposal open 8 days has 5 up and 1 down
- **THEN** the AppView adopts it

#### Scenario: Busy trail
- **WHEN** a segment has 300 riders in the last year and a proposal open 8 days has 25 up and 2 down
- **THEN** the AppView doesn't adopt it until it has at least 30 up votes

#### Scenario: Too soon
- **WHEN** a proposal open 3 days already has 40 up and no down votes
- **THEN** the AppView doesn't adopt it before it has been open 7 days

#### Scenario: Name pinned while a proposal qualifies
- **WHEN** a proposal on a segment would qualify for adoption, but the segment's name is pinned
- **THEN** the AppView doesn't adopt it, and it expires after 90 days unless the pin is removed first

### Requirement: A segment has a current and an original name
A segment's original name SHALL be its record's name. Its current name SHALL be its pinned name while one is in effect (see "A pinned name beats adopted names"), else the name of its latest adopted proposal still in effect, else the original name. Changing the record's name SHALL change only the original name.

#### Scenario: Nothing adopted
- **WHEN** a segment has no adopted proposal and no pinned name
- **THEN** its current name and original name are both its record's name

#### Scenario: Importer refines a renamed segment
- **WHEN** a segment's current name is "Wiggly Woods" and the importer's `refine` rewrites its record name from "Trail 214" to "Trail 214 Connector"
- **THEN** its current name stays "Wiggly Woods" and its original name becomes "Trail 214 Connector"

#### Scenario: Pinned name and a record rename
- **WHEN** a segment's pinned name is "Hogback Climb" and its creator renames the record
- **THEN** its current name stays "Hogback Climb" and its original name changes

### Requirement: Labels remove a proposal or its name
A proposal whose record or proposer's account carries `!hide` or `!takedown` SHALL NOT be counted or adopted, nor listed to anyone but, under `!hide` alone, its proposer, as their own hidden content is. If it was already adopted, its name SHALL leave the history and stop being in effect. If a name pin names it, the pin SHALL stop being in effect. The segment's current name then falls back to a pinned name still in effect, else the previous adopted name in effect, else the original. Negating the label SHALL restore it.

#### Scenario: Offensive name adopted
- **WHEN** the adopted name "Rooty Run" replaced "Wiggly Woods", and its proposal is then labelled `!takedown`
- **THEN** the segment's current name is "Wiggly Woods" again and "Rooty Run" leaves its history

#### Scenario: Label negated
- **WHEN** that `!takedown` is negated
- **THEN** the segment's current name is "Rooty Run" again

#### Scenario: Proposer's account hidden
- **WHEN** bob's account is labelled `!hide` while his proposal "Bob's Bend" is open
- **THEN** other viewers no longer see it, while bob still does and may withdraw it, and it isn't adopted

#### Scenario: Pinned proposal's proposer taken down
- **WHEN** a segment's name is pinned to bob's proposal "Bob's Bend" and bob's account is labelled `!takedown`
- **THEN** the segment's current name falls back to its latest adopted name in effect, else its original

### Requirement: Viewers learn whether they may propose and vote
For a signed-in viewer, `feed.getSegment` SHALL say whether they may propose a name now (the segment is `active`, its name isn't pinned, it is not in a cooldown, they're a regular, and they have no open proposal on it) and whether they may vote (they have at least one effort on it). It SHALL give this only to that viewer.

#### Scenario: Eligible viewer
- **WHEN** alice, with efforts on 6 dates and no open proposal, opens an active segment outside any cooldown
- **THEN** her view says she may propose and may vote

#### Scenario: Signed out
- **WHEN** a signed-out viewer opens a segment
- **THEN** the view says nothing about proposing or voting

#### Scenario: Name pinned
- **WHEN** alice, otherwise eligible, opens an active segment whose name is pinned
- **THEN** her view says she may not propose, and may vote on its open proposals
