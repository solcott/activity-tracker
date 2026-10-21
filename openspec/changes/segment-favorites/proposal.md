# Proposal

## Why

Athletes want to keep a short list of segments they care about, as Strava's stars allow. This is a step of the segments roadmap that can run alongside `segment-creation` and `segment-leaderboards` once `segments-core` is applied. Stars are private: nobody, including the AppView, should learn which segments an athlete follows.

Design, specs and tasks are written once `segments-core` is applied.

## What Changes

- **Private star.** Starring a segment writes a `feed.segmentStar` record (a strong ref to the segment) in the athlete's `self` space. The client reads stars directly from `self`; the AppView never indexes them.
- **Client.**
  - Star and unstar on the segment screen, through the outbox.
  - A "Starred" list of segments, resolved through `feed.getSegment`.
- **Out of scope:** alerts or notifications on a starred segment.

## Capabilities

### New Capabilities
- `segments/segment-favorites`: Private stars on segments, where they're stored, and who can read them.

### Modified Capabilities
- None expected.

## Impact

- **Lexicons:** `feed/segmentStar.json`, added to the `space/athlete.json` declaration for `self`; the Kotlin mirror (not in `Nsid.indexedCollections`).
- **Client:** star state in `SegmentScreen`; a starred-segments list screen; `SegmentRepository` reads and writes in `self`.
- **AppView:** none. The indexer's `self` collections stay as they are.
- **Dependencies:** `segments-core`.
