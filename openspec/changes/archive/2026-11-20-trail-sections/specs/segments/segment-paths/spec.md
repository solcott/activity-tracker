# Spec Delta

## MODIFIED Requirements

### Requirement: A path is a public importer record
A `feed.path` record SHALL hold a name, a kind (`trail` or `road`), one or more line pieces as encoded polylines, a source, a licence, an optional attribution, optionally the sport groups it is open to, optionally its sections, and optionally the import regions whose files list it, which only the importer reads. The AppView SHALL index paths only from the segment importer's repo and ignore a path in any other repo or in any space.

#### Scenario: Importer's trail
- **WHEN** the segment importer publishes a `feed.path` of kind `trail` named "Long Trail"
- **THEN** the AppView indexes it

#### Scenario: Athlete's path
- **WHEN** an athlete publishes a `feed.path` in their own repo
- **THEN** the AppView doesn't index it

### Requirement: A path can be looked up with its segments
`feed.getPath` SHALL return a path's view, every segment on it that the viewer could get from a segment search, as segment views, and the path's sections that hold any of those segments. It SHALL answer `NotFound` for a path that isn't indexed. `feed.getPaths` SHALL return the views of up to 25 given paths, leaving out those not indexed.

#### Scenario: Opening a path
- **WHEN** a viewer gets Ann's, which holds its whole-trail segments and two stretches, each for three sports both ways
- **THEN** the AppView returns Ann's and its 18 segment views

#### Scenario: Unknown path
- **WHEN** a viewer gets a path URI the AppView hasn't indexed
- **THEN** it answers `NotFound`

## ADDED Requirements

### Requirement: A path names its sections' segments
A path's sections SHALL each hold a name and the record keys of `feed.segment` records in the segment importer's repo. A section SHALL name only that repo's segments, and a record key that names no indexed segment SHALL be ignored. The AppView SHALL replace a path's sections whenever the path is indexed again, and drop them with the path. A record without sections SHALL have none.

#### Scenario: Branch of a sectioned trail
- **WHEN** the importer publishes Rush Trail with a section "Through the Trees Trail" naming four of its segments' record keys
- **THEN** the AppView holds that section for Rush Trail with those four segments

#### Scenario: Sections refreshed
- **WHEN** the importer rewrites Rush Trail with one key moved from Through the Trees Trail to Exit Stage Left
- **THEN** the AppView holds the key only in Exit Stage Left

### Requirement: A path's sections are served for what the viewer sees
In `feed.getPath`, each section SHALL hold its name and the URIs of its segments among those returned, in the order the path record lists its sections. A section holding none of the returned segments SHALL be left out. A segment SHALL be in at most one section of a path, the first that names it. Segment views SHALL be unchanged.

#### Scenario: Archived member
- **WHEN** a section names three segments, and one of them is archived
- **THEN** the section is returned with the other two URIs

#### Scenario: Nothing visible in a section
- **WHEN** none of a section's segments is returned to the viewer
- **THEN** the section isn't returned
