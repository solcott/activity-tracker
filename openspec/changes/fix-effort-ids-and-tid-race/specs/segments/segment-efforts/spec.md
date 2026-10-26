## MODIFIED Requirements

### Requirement: Efforts come only from usable segment tracks
The AppView SHALL match an activity against segments only from its athlete's segment track, and only while that athlete's segment matching is on. It SHALL NOT match from polylines, streams or anything else, and an activity without a usable segment track SHALL have no efforts. A segment track SHALL NOT be usable when its data can't be read, a piece's arrays differ in length, or a piece's times are negative or go backwards; times that repeat SHALL be allowed. Such a track SHALL give no efforts and no candidate riders, and SHALL remove those an earlier version of it gave.

#### Scenario: Matching off
- **WHEN** an athlete with matching off publishes a ride along a segment
- **THEN** the ride has no effort on it

#### Scenario: Matching turned on later
- **WHEN** an athlete turns matching on and their earlier ride's segment track arrives
- **THEN** the ride gets its efforts

#### Scenario: Times going backwards
- **WHEN** a segment track's piece has a time earlier than the one before it
- **THEN** its activity has no efforts and its track is counted as a rider of no candidate

#### Scenario: Negative times
- **WHEN** a segment track's piece starts at a negative time and passes a segment twice
- **THEN** its activity has no efforts, and the AppView doesn't keep retrying the track

#### Scenario: Repeated times
- **WHEN** a phone records several samples within one second, so a segment track's piece repeats a time, and the piece passes a segment
- **THEN** the activity gets its effort on the segment

#### Scenario: Valid track replaced by a malformed one
- **WHEN** an activity with efforts gets a new segment track whose data can't be read
- **THEN** the activity's earlier efforts are removed
