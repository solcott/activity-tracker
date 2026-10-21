# Proposal

## Why

US public land agencies (USFS, NPS, BLM) publish trail datasets in the public domain, often more complete than OSM in backcountry areas. Segments from them need no share-alike licence. This change adds them as a second candidate source for the importer, after the split-up `segment-osm-import` item has OSM working end to end. Trailforks data stays out: it is proprietary.

Design, specs and tasks are written once `segment-import-publishing` is applied: this change only adds a source to the pipeline that change completes.

## What Changes

- **Agency trail readers.** The importer reads the agencies' published trail datasets (formats decided in design, likely GeoJSON or shapefiles from their open-data portals) into trails per sport group, from each dataset's allowed-use attributes.
- **Candidates with `source: agency`.** Agency trails are cut and named like OSM trails and staged with `license: CC0-1.0` (public domain), through the same staging and publishing.
- **OSM and agency together.** Where an agency trail and an OSM trail give duplicate candidates, one is kept; which source wins is decided in design.
- **Naming the agency.** The segment credit names the agency, which may add an optional field to `feed.segment`; decided in design.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `segments/trail-candidates`: agency datasets as a second source, and how duplicates across sources are resolved.
- `segments/segment-records`: the agency credit names its agency, if the record gains a field for it.

## Impact

- **Importer:** agency dataset readers and their tag mapping.
- **Lexicons:** possibly an optional attribution field on `feed.segment`, with its `core/lexicon` mirror and AppView indexing.
- **Client:** the agency credit on the segment screen.
- **Dependencies:** `segment-import-publishing`, `segment-attribution`.
