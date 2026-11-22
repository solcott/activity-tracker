# Spec Delta

## ADDED Requirements

### Requirement: A row whose name shows both ways doesn't say it again
When a segment group's row is named with ↔ (`segments/segment-groups`: a two-way stretch named "A ↔ B") and every sport group it lists goes both ways, its details SHALL leave out "Both ways". A row with any one-way wording, or whose name has no ↔, SHALL word its details as before.

#### Scenario: Two-way stretch
- **WHEN** a row named "Lupine Hiking ↔ Potato Hill" lists ride and run segments both ways
- **THEN** its details read "2.10 km · Ride, Run"

#### Scenario: Whole trail
- **WHEN** a row named "Ann's" lists ride, run and hike segments both ways
- **THEN** its details still read "8.69 km · Ride, Run, Hike · Both ways"

#### Scenario: Mixed directions
- **WHEN** a row named with ↔ lists run segments both ways and a one-way ride segment
- **THEN** its details give each wording as before
