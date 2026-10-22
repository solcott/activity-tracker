# Proposal

## Why

US public land agencies publish trail datasets in the public domain, often more complete than OSM in backcountry areas and authoritative about which uses each trail allows. Segments from them need no share-alike licence. This change adds them as a second candidate source for the importer, now that `segment-import-publishing` has OSM working end to end. Trailforks data stays out: it is proprietary.

## What Changes

- **Agency trail readers.** `extract` also reads the US Forest Service's National Forest System trails and the National Park Service's public trails, as GeoJSON text sequences the operator makes with `ogr2ogr` from whatever the agency publishes. Each dataset's allowed-use attributes decide its sport groups. BLM's trail layer is left for a follow-up (see design).
- **One candidates file per region, from every source.** `extract` takes the OSM extract and any agency datasets for the region together and writes one file, so a region is still staged as one run and its duplicates are resolved across sources. The file format moves to version 2.
- **Candidates with `source: agency`.** Agency trails are joined, cut, named and identified like OSM trails, and carry `license: CC0-1.0` and the agency's name. Where an agency candidate and an OSM candidate duplicate each other, the agency's is kept.
- **Naming the agency.** `feed.segment` and `importer.defs#candidate` gain an optional `attribution` (such as "USDA Forest Service"). `publish` writes each candidate's own source, licence and attribution instead of OSM's, and the segment screen's agency credit names the agency.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `segments/trail-candidates`: agency datasets as a second source, how their features qualify, join and get stable ids, and how duplicates across sources are resolved.
- `segments/segment-candidates`: a staged candidate may carry an attribution, returned with ridden candidates.
- `segments/segment-import`: a published segment carries its candidate's source, licence and attribution.
- `segments/segment-records`: a segment may name who to credit, and the agency credit names it.

## Impact

- **Importer:** agency readers and their allowed-use mapping; trail building and cutting take per-source rules; `extract`'s arguments; candidates file version 2; `publish` writes the candidate's source.
- **Lexicons:** optional `attribution` on `feed.segment` and `importer.defs#candidate`, with their `core/lexicon` mirrors.
- **AppView:** a nullable `attribution` column on `segment_candidates` (a Flyway migration), validated, stored and returned. Segments need no migration: the record is stored and served as indexed.
- **Client:** the agency credit names the record's attribution; the seed script can write one.
- **Docs:** the importer README (getting and converting agency data), `scripts/segment-import.sh`.
- **Dependencies:** `segment-import-publishing`, `segment-attribution` (both archived).
