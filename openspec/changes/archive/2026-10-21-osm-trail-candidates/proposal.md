# Proposal

## Why

Trail networks have well-known climbs and descents nobody has made a segment of yet. The split-up `segment-osm-import` roadmap item creates segments from OpenStreetMap trail data, publishing each one only once people ride it. This change is its first piece: turning an OSM extract into candidate segment lines, offline, with no network and no AppView. It can be built and tuned on real extracts before anything is staged or published.

## What Changes

- **A new JVM module, `server/segment-importer`**, a command-line tool outside the AppView (which stays an indexer). This change gives it one command, `extract`, which reads a Geofabrik `.osm.pbf` extract and writes the region's candidates to a file. Later changes add staging (`segment-import-publishing`) and other sources (`segment-agency-import`).
- **Trails per sport group.** Named ways and route relations become trails for `ride`, `run`, `hike`, `ski` and `paddle`, by their tags (`highway=path|track|cycleway|bridleway|footway`, `mtb:scale`, `sac_scale`, `route=mtb|bicycle|hiking|foot|running|ski|canoe`, access tags). Roads, sidewalks, private and unnamed ways are left out.
- **Candidates.** Each trail gives its whole length and its stretches between junctions with other named trails or roads, merged until they reach the sport group's minimum length and kept up to 50 km, in each direction the trail may be travelled. Candidates that duplicate one another are dropped.
- **Stable output.** Each candidate has an id that stays the same while its OSM ways and end nodes do, a name, its sport group, a simplified line in the `feed.segment` encoding, its length, `source: osm`, `license: ODbL-1.0`, and the OSM ways it came from.

## Capabilities

### New Capabilities
- `segments/trail-candidates`: How public trail data becomes candidate segment lines: which features qualify for which sport group, how trails are cut into candidates, how candidates are named and identified, and what a candidate holds.

### Modified Capabilities
<!-- none -->

## Impact

- **New module** `server/segment-importer` (`:server-segment-importer`), JVM, using `core/location` (geometry, polyline encoding, simplification, the duplicate test) and `core/lexicon` (sport groups, sources).
- **New dependency:** a JVM OSM PBF reader (`org.openstreetmap.pbf:osmpbf`).
- **Docs:** a README in the module; AGENTS.md's commands list.
- **No AppView, lexicon or client change.**
- **Dependencies:** none beyond the applied `segments-core` and `segment-creation`.
