# Spec Delta

## Purpose

How the segment importer seeds dive sites from OpenStreetMap: which features qualify, how they are keyed, and how they are published without doubling existing sites.

## ADDED Requirements

### Requirement: Named OSM dive sites are extracted
`extract-sites` SHALL read an OSM extract and write a sites file with one entry per named node, way or relation tagged `sport=scuba_diving` or `sport=free_diving` that isn't a shop, amenity, club or building. A way or relation SHALL be placed at its centroid. Each entry SHALL have a 150 m radius, `source` `osm` and `license` `ODbL-1.0`.

#### Scenario: Wreck site node
- **WHEN** an extract has a node tagged `sport=scuba_diving` named "SS Thistlegorm"
- **THEN** the sites file holds it at the node's position with a 150 m radius

#### Scenario: Dive shop
- **WHEN** a node is tagged `shop=scuba_diving`
- **THEN** it gives no site

### Requirement: Site ids are stable
An extracted site's id SHALL be derived only from its OSM element type and id, so re-extracting gives the same id while the element exists.

#### Scenario: Re-extract
- **WHEN** an operator extracts the same region from a newer extract
- **THEN** each unchanged site keeps its id

### Requirement: Only sites that duplicate nothing are published
`publish-sites` SHALL check the file's sites against live sites through `importer.checkDiveSites`, in pages of up to 500, and publish as the importer account each one that duplicates nothing and has no record under its id. It SHALL never change or delete a published site. Only the importer SHALL be able to make the check.

#### Scenario: Site already placed by divers
- **WHEN** an OSM site is 30 m from an athlete's active site
- **THEN** `publish-sites` doesn't publish it, and reports it as a duplicate

#### Scenario: Run twice
- **WHEN** `publish-sites` runs again on the same file
- **THEN** it writes nothing new and changes no published site
