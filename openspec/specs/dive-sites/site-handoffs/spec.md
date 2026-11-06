# dive-sites/site-handoffs Specification

## Purpose

Keeps a dive site other athletes dive on the map after its creator deletes it, by the segment handoff rules, so its name and visits come back under the importer.

## Requirements

### Requirement: Sites are handed off as segments are
Dive sites SHALL be handed off by the rules of `segments/segment-handoffs`, with a site in place of a segment and visits in place of efforts. A handoff SHALL hold the site's centre, radius, current and original names and name history. The importer SHALL republish it as a `feed.diveSite` keyed by the handoff id, with `source` `athlete`, `license` `CC0-1.0` and no attribution. The site duplicate test takes the place of the segment one.

#### Scenario: Active site deleted
- **WHEN** alice's active site "Blue Hole" is removed because she left
- **THEN** the AppView holds a handoff with its centre, radius and names, and `importer.getHandoffs` lists it as a site

#### Scenario: Proposed site deleted
- **WHEN** alice's proposed site is removed
- **THEN** no handoff is kept

#### Scenario: Republished site
- **WHEN** the operator runs `handoff` with "Blue Hole" pending and no live site within 50 m
- **THEN** the importer's repo holds a `feed.diveSite` under the handoff id with its centre, radius and original name

### Requirement: A republished site is active at once
A site indexed from the importer's repo under a handoff id SHALL be `active` when it duplicates nothing, take the handoff's current name and history, and have its visits recomputed from usable segment tracks.

#### Scenario: Name and visits back
- **WHEN** "Blue Hole", renamed "Shark Alley" by adoption, is republished and indexed
- **THEN** it is `active`, its current name is "Shark Alley", and divers whose tracks are still usable have their visits back

### Requirement: An old site URI resolves to its successor
`feed.getDiveSite` for a handed-off site's old URI SHALL return its successor's view once it has one, and `NotFound` until then.

#### Scenario: Dive page link
- **WHEN** a viewer follows an old link to alice's site after republication
- **THEN** they get the republished site's view
