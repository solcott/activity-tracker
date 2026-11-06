# dive-sites/site-records Specification

## Purpose

Dive sites as public places: the `feed.diveSite` record, how the AppView validates and indexes it, the status it derives, how duplicates are caught, how many an athlete may create, and how sites are found by area.

## Requirements

### Requirement: A dive site is a public record
A dive site SHALL be a `feed.diveSite` record in its creator's repo, never in a space. It SHALL hold a name, a centre position, a radius in whole metres, its source (`athlete`, `osm` or `agency`) and the licence of its data. It MAY name who to credit for its data and the dive it was placed from.

#### Scenario: Site written
- **WHEN** an athlete publishes a site named "Blue Hole" from their dive
- **THEN** their repo gains a `feed.diveSite` with that name, a centre, a radius, `source` `athlete` and `license` `CC0-1.0`

#### Scenario: Site in a space
- **WHEN** a `feed.diveSite` appears in an athlete's tier space
- **THEN** the AppView ignores it

### Requirement: Only valid sites are indexed
The AppView SHALL index a site only if its name is non-empty after trimming and at most 100 graphemes, its centre is a valid position, its radius is between 25 m and 500 m, and its source is known. It SHALL drop any other site record.

#### Scenario: Radius too large
- **WHEN** a `feed.diveSite` has a radius of 2000 m
- **THEN** the AppView doesn't index it

#### Scenario: Blank name
- **WHEN** a site's name is only spaces
- **THEN** the AppView doesn't index it

### Requirement: Every site has a status
The AppView SHALL give each indexed site one status, `proposed`, `active`, `duplicate` or `archived`, derived by the AppView and never read from the record. A `proposed` site SHALL become `active` once visits come from at least a configured number of distinct athletes, 3 by default, counting visits of every visibility but revealing only the status. A site still `proposed` 180 days after first indexed SHALL become `archived`.

#### Scenario: Third diver
- **WHEN** a proposed site gets a visit from a third distinct athlete, whose dive is Only me
- **THEN** the site becomes `active`, and no view reveals who counted

#### Scenario: Never dived
- **WHEN** a proposed site has had visits from only its creator for 180 days
- **THEN** it becomes `archived`

### Requirement: Duplicate sites are detected on indexing
When the AppView indexes a new or moved site, it SHALL mark it `duplicate` of the earliest-indexed `proposed` or `active` site whose centre is within 50 m of its own. A duplicate SHALL get no visits and SHALL be re-checked when the site it duplicates is deleted, archived or moved away.

#### Scenario: Same reef twice
- **WHEN** a second athlete publishes a site 30 m from an active site
- **THEN** the new site is `duplicate` of the existing one

#### Scenario: Neighbouring reefs
- **WHEN** a site is published 200 m from an active site
- **THEN** it isn't a duplicate

### Requirement: Site creation is limited per athlete
The AppView SHALL index at most 10 new sites per creator in any 24 hours and SHALL drop further new sites from that creator in that window. Re-writing an indexed site SHALL NOT count, and the segment importer's account SHALL NOT be limited.

#### Scenario: Eleventh site in a day
- **WHEN** an athlete writes an eleventh new site within 24 hours
- **THEN** the AppView doesn't index it

### Requirement: A site can be checked before publishing
`feed.checkDiveSite` SHALL take a centre position from a signed-in athlete and return every `proposed` or `active` site it would duplicate, and how many more sites the athlete may create in the current 24 hours. A viewer who isn't signed in SHALL be refused.

#### Scenario: Check near an existing site
- **WHEN** an athlete checks a position 20 m from an active site
- **THEN** the AppView returns that site and the athlete's remaining allowance

### Requirement: Sites are looked up and found by area
`feed.getDiveSite` SHALL return a site's view by URI, whatever its status, with its current and original names, centre, radius, source, licence, attribution, status and, for a duplicate, the site it duplicates. `feed.searchDiveSites` SHALL return the sites whose centre lies in a bounding box, at most 100 per page with a cursor: `active` ones to everyone, `proposed` ones only to their creator and athletes with a visit, never `duplicate` or `archived` ones. Handed-off URIs serve their successor.

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
