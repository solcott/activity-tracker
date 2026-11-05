# Spec Delta

## MODIFIED Requirements

### Requirement: Sites are looked up and found by area
`feed.getDiveSite` SHALL return a site's view by URI, whatever its status, with its current and original names, centre, radius, source, licence, attribution, status and, for a duplicate, the site it duplicates. For the URI of a handed-off site that has a successor, it SHALL return the successor's view (`dive-sites/site-handoffs`). `feed.searchDiveSites` SHALL return the sites whose centre lies in a bounding box, at most 100 per page with a cursor: `active` ones to everyone, `proposed` ones only to their creator and athletes with a visit, never `duplicate` or `archived` ones.

#### Scenario: Searching a bay
- **WHEN** a viewer searches a bay holding one active and one proposed site they haven't visited
- **THEN** only the active site is returned

#### Scenario: Unknown site
- **WHEN** a viewer asks for a site URI the AppView hasn't indexed
- **THEN** the AppView answers `NotFound`

#### Scenario: Handed-off site
- **WHEN** a viewer asks for the URI of a deleted site the importer has republished
- **THEN** the view is the republished site's, under its own URI

### Requirement: Editing or deleting a site updates it everywhere
Re-writing a site record SHALL replace its indexed version and recompute its visits when its centre or radius changed. Deleting it SHALL remove the site, its visits, its name proposals, their votes and its name history, except what a handoff keeps for an `active` site that isn't the importer's (`dive-sites/site-handoffs`).

#### Scenario: Site moved
- **WHEN** a creator moves their site's centre 80 m
- **THEN** its visits are recomputed against the new centre and radius

#### Scenario: Active site deleted
- **WHEN** the creator of an active site deletes its record
- **THEN** the AppView serves neither it nor its visits, and holds a handoff with its centre, radius and names
