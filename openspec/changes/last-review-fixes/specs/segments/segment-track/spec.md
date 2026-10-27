# Spec Delta

## MODIFIED Requirements

### Requirement: A segment track leaves out privacy zones
A segment track SHALL hold every point of the activity's full track except those inside any of the athlete's privacy zones or within the hide-start/end distance of either end, cut where the track crosses each boundary. The track SHALL be treated as the lines between its points, not only the points: a line between two points outside a zone that passes through the zone SHALL be cut where it enters the zone and where it leaves. Where the track leaves and later re-enters the shown area, the parts SHALL be separate pieces, never joined across what was left out.

#### Scenario: Starts at home
- **WHEN** an athlete with a zone around home publishes a ride that starts there
- **THEN** its segment track starts where the ride leaves the zone, and holds no point inside it

#### Scenario: Passes through a zone
- **WHEN** a run passes through one of the athlete's zones mid-route
- **THEN** its segment track has one piece before the zone and one after, with no line between them

#### Scenario: Sparse samples across a zone
- **WHEN** a ride recorded with sparse samples has two points in a row outside a zone, and the straight line between them passes through the zone
- **THEN** its segment track ends one piece where that line enters the zone and starts the next where it leaves, with no line between them

#### Scenario: Line passing beside a zone
- **WHEN** the line between two points comes near a zone but stays outside it
- **THEN** the segment track keeps that line whole, in one piece

#### Scenario: Entirely hidden
- **WHEN** every point of an activity is inside a zone or within the hide-start/end distance
- **THEN** no segment track is written for it
