# Proposal

## Why

`segments-core` defines segments and matches efforts on them, but the only way to make one is a dev script. This change is the third step of the segments roadmap (after `segment-track-consent` and `segments-core`). It lets athletes create segments from their own activities while keeping out the duplicate and superfluous segments that clutter Strava. `segment-leaderboards` and `segment-osm-import` build on the dedup and the segment statuses it adds.

Design, specs and tasks are written once `segments-core` is applied, since this change builds on its record, tables and `SegmentGeometry`.

## What Changes

- **Create from your own activity.** On an activity's map, the athlete picks a start and an end on their own route, names the segment and publishes a `feed.segment` record with `source: athlete` and `derivedFrom` set to the activity. There's no drawing a segment freehand.
  - A segment can't start or end inside the creator's privacy zones.
  - A minimum length per sport group, and a per-athlete rate limit.
- **Duplicate check before publishing.** A new `feed.checkSegment` query returns near-duplicates of a proposed line: same direction, length ratio within bounds, and a discrete Fréchet or Hausdorff distance on resampled points under a threshold. The creation screen offers the existing segment instead.
- **Duplicate check on indexing.** The indexer runs the same test, so a duplicate written straight to a PDS is stored with `duplicateOf` and kept off leaderboards.
- **Proposed until ridden.** A new segment starts `proposed` and becomes `active` once N distinct athletes have efforts on it. Segments that stay unridden are `archived`. Statuses: `proposed`, `active`, `duplicate`, `archived`.
- **Retire the stopgap.** `docker/scripts/seed-segment.sh` stays for local testing only; the app becomes the way to create segments.

## Capabilities

### New Capabilities
- `segments/segment-creation`: Creating a segment from one's own activity, the creation limits, and the duplicate check offered before publishing.

### Modified Capabilities
- `segments/segment-records`: Segment statuses (`proposed`, `active`, `duplicate`, `archived`), indexer-side dedup with `duplicateOf`, promotion after N distinct athletes, and archiving.

## Impact

- **Lexicons:** a `feed.checkSegment` query; status (and `duplicateOf`) on the segment view.
- **Shared geometry:** dedup distance (Fréchet/Hausdorff on resampled points) in `core/location`'s `SegmentGeometry`, used by the client preview and the AppView.
- **AppView:** status columns and a migration; dedup on indexing; a promotion and archive pass, likely in the `SegmentMatcher` loop; the rate limit.
- **Client:** a create-segment flow from the activity detail map (start/end pickers, name, sport group), the duplicate prompt, and `SegmentRepository` writes through the outbox.
- **Dependencies:** `segments-core`.
