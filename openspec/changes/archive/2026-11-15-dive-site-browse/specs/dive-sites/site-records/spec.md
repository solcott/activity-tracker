# Spec Delta

## ADDED Requirements

### Requirement: Sites can be found by name
`feed.searchDiveSiteNames` SHALL return the sites whose current name contains a query, ignoring case, at most 100 per page with a cursor, serving exactly the sites `feed.searchDiveSites` would serve the same viewer: `active` ones to everyone, `proposed` ones only to their creator and athletes with a visit, never `duplicate`, `archived`, hidden or taken-down ones. It SHALL order them by how well the name matches (the whole name, then its start, then a word's start, then anywhere), then nearest a given centre, else by name. A box, when given, SHALL hold the site's centre and be at most 1 degree a side; a box or centre given in part SHALL be `InvalidRequest`. A blank query SHALL return nothing.

#### Scenario: Current name
- **WHEN** a site recorded as "Reef 7" has adopted the name "Shark Alley"
- **THEN** searching "shark" finds it and searching "reef 7" doesn't

#### Scenario: Prefix before anywhere
- **WHEN** a viewer searches "hole" and "Hole in the Wall" and "Blue Hole" match
- **THEN** "Hole in the Wall" comes before "Blue Hole"

#### Scenario: Proposed site
- **WHEN** a proposed site's name matches
- **THEN** its creator and athletes with a visit to it get it, and other viewers don't
