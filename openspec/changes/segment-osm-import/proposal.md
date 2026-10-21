# Proposal

## Why

Athletes create segments where they ride, but trail networks have well-known climbs and descents nobody has created yet. This step of the segments roadmap creates segments automatically from OpenStreetMap trail data, while keeping the "proposed until ridden" rule: an imported segment is published only once people actually ride it, so the map doesn't fill with segments nobody uses.

Design, specs and tasks are written once `segment-creation` is applied: the importer relies on its duplicate check and segment statuses.

## What Changes

- **A separate importer**, a JVM module or script outside the AppView (the AppView stays a pure indexer):
  - reads Geofabrik PBF extracts (osmium or the osm4j/osmosis libraries);
  - builds named trails from `highway=path|track|cycleway`, `mtb:scale`, `sac_scale` and `route=mtb|hiking|bicycle|ski|canoe` relations;
  - splits trails at junctions and picks segments and climbs by length and grade.
- **Staged, then published when ridden.** Candidates stay in a staging store. The importer publishes one (`putRecord`) from a service-account repo, with its own DID, only once usage shows people ride it, and only if `feed.checkSegment` finds no duplicate.
- **Sources and licences.** Records carry `source: osm` and `license: ODbL-1.0`, or `source: agency` for public-domain USFS/NPS/BLM data. Trailforks data is proprietary and not used.
- **Attribution.** The client shows OSM attribution on imported segments.
- **One region first.**

## Capabilities

### New Capabilities
- `segments/osm-import`: How trail data becomes candidate segments, when a candidate is published, from which account, and how its source and licence are shown.

### Modified Capabilities
- `segments/segment-records`: Segments from the service account and their attribution.

## Impact

- **New module or script** for the importer, its staging store, and the service-account credentials.
- **AppView:** a way for the importer to measure usage of unpublished candidates (efforts or matched polylines), without serving private data; may need a privileged query or a direct read, decided in design.
- **Client:** attribution on the segment screen for `osm` and `agency` sources.
- **Ops:** the service account on the PDS, and docs in the local-stack skill.
- **Dependencies:** `segments-core`, `segment-creation`.
