# Proposal

## Why

Athletes picking a segment or trail can't tell whether it's paved or rocky, how hard it is, whether it's lit at night, or whether their dog may come. That information is already in the data the segment importer reads: OSM tags such as `surface`, `sac_scale`, `mtb:scale` and `piste:difficulty`, and the agency datasets' surface, trail class, grade and width fields. The importer drops all of it today, so no segment or path record carries it.

## What Changes

- The importer reads trail attributes from OSM ways and route relations and from Forest Service, Park Service and BLM features, and maps them onto one small vocabulary per attribute:
  - **Surface:** each surface's share of the length whose surface is known, and the roughest smoothness.
  - **Difficulty:** SAC hiking scale, MTB scale, IMBA rating, MTB uphill scale, piste difficulty, piste grooming, and the agency trail class.
  - **Visibility, width and grade:** trail visibility, typical width, and the agency's typical grade.
  - **Access and other:** lit, dogs, horses, wheelchair access, open season, operator and website.
- Each attribute is combined over the ways a candidate or path covers by its own rule. An attribute with no data is left out, never guessed.
- Candidates carry these attributes through the candidates file, staging (`importer.putSegmentCandidates`) and checks (`importer.checkSegmentCandidates`). `publish` writes them on the `feed.segment`, and `refine` keeps them current on segments already published.
- The paths file carries a path's attributes, and `publish-paths` writes them on the `feed.path` and replaces them when they change.
- New optional `trail` object (`feed.defs#trailAttributes`) on `feed.segment`, `feed.path`, `importer.defs#candidate`, the segment view and the path view. The AppView serves it only for records in the segment importer's repo, as with `oneWay` and `climb`.
- The Segment page and the Trail page show a Trail details section on Android, desktop, web and iOS (native). It shows only the attributes a segment or path has, with a link to the website when it is a valid http(s) URL.
- The candidates file format goes from version 5 to 6 and the paths file from 2 to 3. Older files are refused, as on every earlier format bump, so each region must be extracted again.
- OSM `ref` is not shown. OSM `incline` is not read, since segment pages already chart grade from elevation.

## Capabilities

### New Capabilities
- `segments/trail-attributes`: which source tags and fields become which trail attributes, how they're normalized and combined across ways, how the AppView serves them, and how the apps show them on the Segment and Trail pages.

### Modified Capabilities
- `segments/trail-candidates`: a candidate also holds its trail attributes.
- `segments/segment-candidates`: staged candidates carry their trail attributes, and checks return them.
- `segments/segment-import`: published segments carry their candidate's trail attributes, and `refine` may also update them.
- `segments/path-import`: the paths file holds each path's attributes, and `publish-paths` replaces them when they differ.
- `segments/segment-paths`: a `feed.path` record and the path view carry trail attributes.

## Impact

- **Lexicons:** `feed/defs.json` (new `trailAttributes`), `feed/segment.json`, `feed/path.json`, `importer/defs.json`, plus the segment and path views, all mirrored in `core/lexicon` (`Records.kt`, `Views.kt`, `Queries.kt`).
- **Importer** (`server/segment-importer`):
  - `TrailTags` and `AgencyRules` read the new tags and fields.
  - `OsmReader` keeps the relation tags these attributes need.
  - `CandidateCutter` and `Paths` combine attributes over the ways each candidate or path covers.
  - `Extract`, `Stage`, `Publish`, `Refine` and `PublishPaths` carry the attributes through.
  - Both file format versions go up.
- **AppView** (`server/appview`): a Flyway migration adds a `trail` column to `segment_candidates`. `SegmentCandidateViews`, `SegmentViews` and path views serve the attributes, and `PathIndex` keeps them.
- **Apps:**
  - `:feature-segment-api`, `-presenter` and `-ui` gain the Trail details section and its texts.
  - New strings go in `:ui-resources`, and `generateIosResources` regenerates the iOS copies.
  - The iOS Segment and Trail screens get a native Trail details section.
  - `:app-fixtures` gains sample states, and screenshot references are updated.
- **Operations:** after deploying, each region must be re-extracted, staged, refined and its paths published again to backfill the attributes on segments and paths already published.
