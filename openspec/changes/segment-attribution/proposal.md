# Proposal

## Why

Every `feed.segment` records where its line came from (`source`) and under which licence (`license`), but the segment screen shows neither. Segments imported from OpenStreetMap (`segment-import-publishing`) may be shown only with OSM's attribution, which the ODbL requires, and agency segments (`segment-agency-import`) should credit their public source too. This change is the first step of the split-up `segment-osm-import` roadmap item. It needs nothing else from the import, so it can land first.

## What Changes

- **Credit on the segment screen.** Below "By @handle", the segment screen credits the data the line came from:
  - `osm`: "© OpenStreetMap contributors", linking to OSM's copyright page, and the ODbL;
  - `agency`: a credit to public agency trail data;
  - `athlete`: nothing more than today;
  - any other source: its licence identifier.
- **Seed script.** `docker/scripts/seed-segment.sh` can write a segment with `source: osm` or `agency` and the matching licence, so the credit can be checked locally before any import exists.

No lexicon or AppView change: `segmentView.record` already carries `source` and `license`.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `segments/segment-records`: a segment's source is credited wherever the app draws its line.

## Impact

- **`:app-screens`:** `SegmentState` gains the credit to show.
- **`:app-presenters`:** `SegmentPresenter` derives it from the record; `SegmentPresenterTest` covers each source.
- **`composeApp`:** `SegmentUi` shows it, with the OSM link opened through the platform's URI handler; new strings.
- **Dev:** `docker/scripts/seed-segment.sh` gains a source option.
- **Dependencies:** none beyond the applied `segments-core`.
