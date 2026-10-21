# Proposal

## Why

`segment-track-consent` gave the AppView consented, zone-trimmed copies of athletes' tracks, but nothing uses them yet. This change is the second step of the segments roadmap. It defines what a segment is, matches each consenting athlete's activities against the segments they pass, and shows the resulting efforts on activity and segment pages. Creating segments in the app (`segment-creation`), leaderboards (`segment-leaderboards`), favorites and OSM import come later and build on these records and efforts.

## What Changes

- **Segment record.** A new public `feed.segment` record in an athlete's repo holds:
  - a name;
  - a sport group (ride, run, hike, ski or paddle);
  - the line, as a precision-6 encoded polyline;
  - its source (an athlete or a dataset) and the data's licence;
  - optionally, the activity it was traced from.
- **No way to create one in the app yet.** A dev script (`docker/scripts/seed-segment.sh`) writes segments from a GPX file for local testing. Creating segments in the app comes in `segment-creation`.
- **AppView indexing.** The AppView indexes segments into `segments`, with a cell index for nearby lookups. It drops segments that are too short, too long or malformed.
- **Matching.** A background matcher in the AppView compares each usable segment track with the segments it passes, and records an effort for each pass that follows a segment from start to end.
  - Segment tracks are usable only while the athlete's matching is on (`usableSegmentTracks()`).
  - The sport groups must match.
  - Activities that are members of a combined activity are skipped.
  - Matching reruns when a segment track, a segment or the activity changes. It also runs for existing tracks when a new segment appears.
- **Who sees an effort.** An effort is visible at the narrowest of three visibilities: the activity's, its Map category's and its Pace/Speed category's. Any viewer admitted to that tier sees it, and everyone else sees nothing of it.
  - Turning matching off, deleting the activity or its segment track, or deleting the segment removes its efforts.
- **New queries:**
  - `feed.getSegment`: a segment and the viewer's own best effort on it.
  - `feed.searchSegments`: segments in a bounding box, optionally for one sport group.
  - `feed.getActivitySegments`: the efforts on an activity.
  - `feed.getSegmentEfforts`: one athlete's efforts on a segment, fastest first.
- **Client.**
  - The activity page lists the activity's segment efforts with their times, marking an athlete's best ("PR").
  - A segment screen shows the segment's line, distance, sport and the viewer's own efforts.

## Capabilities

### New Capabilities
- `segments/segment-records`: What a segment is: its record, validation and where it lives. Also how the AppView indexes it and serves it by URI and by area.
- `segments/segment-efforts`: How efforts are matched from consenting athletes' segment tracks, when they're recomputed or removed, and who may see each one. Also how activity and segment pages show them.

### Modified Capabilities
- `segments/segment-track`: The AppView now reads segment-track data to match it, still only while matching is on. Turning matching off also drops the efforts derived from it.

## Impact

- **Lexicons:**
  - new `feed/segment.json`, plus a `segment` view and an `effort` view in `feed/defs.json`;
  - four query lexicons;
  - the Kotlin mirror in `core/lexicon`: records, `Nsid` (`feed.segment` joins `indexedCollections`), queries, and a `SegmentSport` mapping from sport and sub-sport to sport group.
- **Shared geometry:** `core/location` gains the segment geometry (resampling, distance to a line, pass detection). The AppView takes a dependency on `:core-location`.
- **AppView:**
  - a `V29__segments.sql` migration with tables `segments`, `segment_cells`, `segment_efforts` and `segment_match_jobs`;
  - an indexer branch for segments;
  - a `segments/` package with the matcher loop, modelled on `SpaceSyncer`'s;
  - `SpaceBlobs` reads of segment-track data from `self`;
  - views, routes and tier filtering through `visibleTo`.
- **Client:**
  - a segments section on the activity detail screen (`app/screens`, `app/presenters`, `composeApp`);
  - a new `SegmentScreen` (via the `new-screen` skill);
  - a `SegmentRepository` in `core/data`.
- **Dev tooling:** `docker/scripts/seed-segment.sh`.
- **Docs:** `.agents/rules/appview.md` (efforts are derived private data, served by tier), `AGENTS.md`'s data-flow paragraph, and the local-stack skill (the seed script).
