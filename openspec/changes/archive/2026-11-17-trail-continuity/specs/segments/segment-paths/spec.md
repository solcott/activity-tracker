# Spec Delta

## MODIFIED Requirements

### Requirement: A segment lies on a path when much of it follows the path
A live run segment SHALL lie on a path when the stretches of its line that follow the path, within the corridor duplicates use, add up to at least a quarter of its length or 1 km, whichever is shorter. A segment in the segment importer's repo SHALL instead lie on a path only when such stretches add up to at least 90% of its length, so an imported segment is listed on the trail it was cut from and on trails sharing its tread, not on trails it only crosses or runs along for part of its length.

A stretch SHALL count only for the paths nearest it, within 5 m of the nearest. Direction SHALL NOT matter. A segment MAY lie on several paths. Lifts SHALL lie on none.

#### Scenario: Stretch of a trail
- **WHEN** a 1.2 km segment follows Ann's for its whole length
- **THEN** it lies on Ann's

#### Scenario: Two trails combined
- **WHEN** an athlete's 4 km segment follows Ann's for 1.5 km and then Hogback for 2.5 km
- **THEN** it lies on both Ann's and Hogback

#### Scenario: Clip at a junction
- **WHEN** a 3 km road segment follows a crossing trail for 40 m
- **THEN** it doesn't lie on that trail

#### Scenario: Parallel trail
- **WHEN** a segment follows Hog Hollow Road, which runs 20 m from Achtung Baby for a kilometre
- **THEN** it lies on Hog Hollow Road and not on Achtung Baby

#### Scenario: Shared tread
- **WHEN** two trails follow the same ways for a stretch and a segment follows that stretch
- **THEN** it lies on both

#### Scenario: Reverse direction
- **WHEN** a westbound segment follows a path drawn eastbound
- **THEN** it lies on the path

#### Scenario: Imported segment along part of another trail
- **WHEN** the importer's 5 km Bonneville Shoreline segment follows Clarks for 2 km
- **THEN** it lies on Bonneville Shoreline and not on Clarks

#### Scenario: Athlete's segment along part of a trail
- **WHEN** an athlete's 5 km segment follows Clarks for 2 km
- **THEN** it lies on Clarks
