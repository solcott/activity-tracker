# Proposal

## Why

`segments-core` defines segments and matches efforts on them, but the only way to make one is a dev script. This change is the third step of the segments roadmap. It lets athletes create segments from their own activities while keeping out duplicate and unused segments that would clutter the map. `segment-leaderboards` and `segment-osm-import` build on the duplicate check and the segment statuses it adds.

## What Changes

- **Create from your own activity.** On one of their own activities with a track, an athlete picks a start and an end along the route, names the segment and publishes a public `feed.segment`.
  - The line comes from the activity's track as cut by the athlete's privacy zones and hide-start/end distance, so it never enters a zone. It can't span a gap in the track.
  - The segment's sport group comes from the activity's sport. Activities without one (indoor, lift-served skiing) can't make segments.
  - Any visibility works, but before publishing the app says the line becomes public. `derivedFrom` names the activity only when the activity and its Map category are both Public, so a non-public activity's URI never appears in a public record.
- **Duplicate check before publishing.** A new `feed.checkSegment` procedure returns the existing segments that a proposed line duplicates, plus how many more segments the athlete may create today. The app offers the existing segment instead and won't publish a duplicate.
- **Statuses.** The AppView gives each segment a status: `proposed`, `active`, `duplicate` or `archived`, shown in segment views.
  - A new segment starts `proposed`. It becomes `active` once 3 distinct athletes (configurable, the creator included) have efforts on it.
  - A segment that duplicates an earlier one becomes `duplicate`, with `duplicateOf` pointing at that one, even when written straight to a PDS. It gets no efforts.
  - A segment still `proposed` 180 days after it was first indexed becomes `archived`. It gets no new efforts.
- **Less clutter.** `feed.searchSegments` returns active segments to everyone, but proposed ones only to their creator and to athletes with an effort on them. Duplicate and archived segments never appear in search.
- **Limits.** Minimum lengths per sport group (500 m for `ride`, 200 m for the rest) and at most 10 new segments per athlete per 24 hours. The AppView enforces both.
- **The seed script stays** for local testing. It is no longer the only way to create a segment.

## Capabilities

### New Capabilities
- `segments/segment-creation`: Creating a segment in the app from one's own activity: which activities and stretches qualify, the duplicate check and daily allowance before publishing, and what the public record may reveal.

### Modified Capabilities
- `segments/segment-records`: Segment statuses and how a segment moves between them, duplicate detection on indexing, per-sport-group minimum lengths, the daily creation limit, `checkSegment`, and status-aware `getSegment` and `searchSegments`.
- `segments/segment-efforts`: Only proposed and active segments get new efforts, and the activity and segment pages show a segment's status.

## Impact

- **Lexicons:** a `feed.checkSegment` procedure; `status` and `duplicateOf` on `segmentView`, and `status` on `segmentViewBasic`. All additions are optional, so older clients keep working.
- **Shared geometry:** a duplicate test (discrete Fréchet distance on resampled lines, after cheap length and endpoint checks) in `core/location`'s `SegmentGeometry`, used by the AppView.
- **AppView:**
  - a `V30__segment_status.sql` migration adding `status`, `duplicate_of` and `first_indexed_at` to `segments`;
  - the duplicate check and the daily limit in `SegmentRecordIndex`, with duplicates re-checked when the segment they duplicate changes or goes;
  - promotion after each match, and a periodic archive pass in `SegmentMatcher`;
  - a status-aware `searchSegments`, the status in views, and the `checkSegment` route;
  - a `SEGMENT_ACTIVE_ATHLETES` setting in `AppViewConfig`.
- **Client:**
  - a new `CreateSegmentScreen` (via the `new-screen` skill), opened from the owner's activity detail screen. It has a range picker along the route, a highlighted preview on `ActivityMap`, name and duplicate prompts, and the public-line warning;
  - `SegmentRepository.check` and a create write through the outbox;
  - status shown on the segment screen and on activity efforts.
- **Docs:** AGENTS.md's segments wording and `.agents/rules/appview.md` (status is derived, segments are never written by the AppView).
- **Dependencies:** `segments-core` (applied).
