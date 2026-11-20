## MODIFIED Requirements

### Requirement: A path is a public importer record
A `feed.path` record SHALL hold a name, a kind (`trail` or `road`), one or more line pieces as encoded polylines, a source, a licence, an optional attribution, optionally the sport groups it is open to, optionally its sections, optionally its trail attributes (`segments/trail-attributes`), and optionally the import regions whose files list it, which only the importer reads. The AppView SHALL index paths only from the segment importer's repo and ignore a path in any other repo or in any space.

#### Scenario: Importer's trail
- **WHEN** the segment importer publishes a `feed.path` of kind `trail` named "Long Trail"
- **THEN** the AppView indexes it

#### Scenario: Athlete's path
- **WHEN** an athlete publishes a `feed.path` in their own repo
- **THEN** the AppView doesn't index it

#### Scenario: Path with trail attributes
- **WHEN** the segment importer publishes a `feed.path` whose `trail` holds `lit: yes`
- **THEN** the AppView indexes it with that attribute

### Requirement: A path's view says what it covers for the viewer
A path view SHALL carry the path's URI, name, kind, length, line pieces, source, licence, attribution and trail attributes, as `segments/trail-attributes` serves them. Counting only the segments on it that the viewer could get from a segment search, it SHALL also carry: their sport groups in the app's order, how many segment groups they form, and, for each sport group whose segments on it are all served one-way and that isn't alpine, the way it goes.

#### Scenario: Multi-use trail
- **WHEN** a viewer gets the view of Ann's, which holds ride, run and hike segments in 3 segment groups
- **THEN** it reads Ride, Run, Hike and 3 segment groups

#### Scenario: Proposed segment someone else made
- **WHEN** a path's only segment is proposed by someone else and the viewer has no effort on it
- **THEN** the view lists no sport groups and no segment groups

#### Scenario: One-way downhill trail
- **WHEN** a viewer gets the view of Rush, whose 21 ride segments are all one-way and served with `climb: "downhill"`
- **THEN** it says ride goes one way only, downhill

#### Scenario: One-way for one sport only
- **WHEN** Clarks holds one-way ride segments and two-way run and hike segments
- **THEN** its view says ride goes one way only and says nothing about run or hike

#### Scenario: Path view with attributes
- **WHEN** a viewer gets the view of a path whose record's `trail` holds `surfaces` of 100% `gravel`
- **THEN** the view carries that surface
