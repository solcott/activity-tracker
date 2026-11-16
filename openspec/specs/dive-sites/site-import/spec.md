# dive-sites/site-import Specification

## Purpose

How the segment importer seeds dive sites from OpenStreetMap: which features qualify, how they are keyed, and how they are published without doubling existing sites.

## Requirements

### Requirement: Named OSM dive sites are extracted
`extract-sites` SHALL read an OSM extract and write a sites file with one entry per named node, way or relation tagged `sport=scuba_diving` or `sport=free_diving` that isn't a shop, amenity, club, building or healthcare facility, and whose name has none of the words center, centre, platform, training, school or chamber (facilities mapped with the sport tag alone; a site named for a resort or a jetty is kept). A way or relation SHALL be placed at its centroid. Each entry SHALL have `source` `osm` and `license` `ODbL-1.0`, and a radius from what the element is, the first that applies: for a way or relation, its farthest known point from the centroid plus 50 m, between 100 m and 500 m; for a wreck (`historic`, `seamark:type` or `scuba_diving:type` `wreck`), 100 m; for a reef or wall (`natural=reef`, `scuba_diving:type` `reef` or `wall`), 200 m; otherwise 150 m.

#### Scenario: Wreck site node
- **WHEN** an extract has a node tagged `sport=scuba_diving` named "SS Thistlegorm"
- **THEN** the sites file holds it at the node's position with a 100 m radius, as a wreck

#### Scenario: Mapped reef outline
- **WHEN** a reef is mapped as a way whose farthest node is 222 m from its centroid
- **THEN** its site has a 272 m radius

#### Scenario: Dive shop
- **WHEN** a node is tagged `shop=scuba_diving`
- **THEN** it gives no site

#### Scenario: Facility named as such
- **WHEN** a node tagged only `sport=free_diving` is named "Free diving platform", 32 m from a node named "Blue Hole"
- **THEN** the platform gives no site, and Blue Hole does

### Requirement: Site ids are stable
An extracted site's id SHALL be derived only from its OSM element type and id, so re-extracting gives the same id while the element exists.

#### Scenario: Re-extract
- **WHEN** an operator extracts the same region from a newer extract
- **THEN** each unchanged site keeps its id

### Requirement: Only sites that duplicate nothing are published
`publish-sites` SHALL check the file's sites against live sites through `importer.checkDiveSites`, in pages of up to 500, and publish as the importer account each one that duplicates nothing and has no record under its id. It SHALL never change or delete a published site. Only the importer SHALL be able to make the check. A published site is `active` from indexing (`dive-sites/site-records`), so search lists it before anyone dives it.

#### Scenario: Site already placed by divers
- **WHEN** an OSM site is 30 m from an athlete's active site
- **THEN** `publish-sites` doesn't publish it, and reports it as a duplicate

#### Scenario: Published site is searchable
- **WHEN** `publish-sites` publishes a site no diver has visited
- **THEN** `feed.searchDiveSites` and `feed.searchDiveSiteNames` list it to every viewer

#### Scenario: Run twice
- **WHEN** `publish-sites` runs again on the same file
- **THEN** it writes nothing new and changes no published site
