# Proposal

## Why

Athletes want to keep a short list of segments they care about, as Strava's stars allow. This is a step of the segments roadmap that can run alongside `segment-creation` and `segment-leaderboards` now that `segments-core` is applied. Stars are private: only the athlete reads them. The AppView syncs the `self` space they live in, but never stores or serves them, as with privacy zones and full tracks.

## What Changes

- **Private star.** Starring a segment writes a `feed.segmentStar` record (a strong ref to the segment) in the athlete's `self` space, keyed by the segment so each segment has at most one. The client reads stars directly from `self`; the AppView drops them when it syncs the space.
- **Client.**
  - Star and unstar on the segment screen, through the outbox.
  - A "Starred segments" list, opened from the athlete's own profile and resolved through `feed.getSegment`.
- **Out of scope:** alerts or notifications on a starred segment, and showing stars anywhere but the athlete's own app.

## Capabilities

### New Capabilities
- `segments/segment-favorites`: Private stars on segments, where they're stored, who can read them, and how the app shows them.

### Modified Capabilities
- `spaces/appview-syncer`: `feed.segmentStar` joins the records the AppView never stores from a space.

## Impact

- **Lexicons:** `feed/segmentStar.json`, added to the `space/athlete.json` declaration for `self`; the Kotlin mirror and `Nsid` constant (not in `Nsid.indexedCollections`); a `segmentStarRkey` beside `commentHideRkey`.
- **Client:** a `SegmentStars` store in `core/data` (outbox writes, `self` reads); star state in `SegmentScreen`; a new `StarredSegmentsScreen` opened from the own `ProfileScreen`.
- **AppView:** no code; a test pins that a `feed.segmentStar` in `self` stores nothing.
- **Sessions:** a session whose `space:` grant predates the declaration change can't write stars until the athlete signs in again.
- **Dependencies:** `segments-core` (applied).
