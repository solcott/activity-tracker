# Proposal

## Why

Three findings from the 2026-10-23 review (`docs/reviews/2026-10-23-codebase-review.md`) are still open. Each is small, and none needed a change of its own:

- **P8.** Privacy zones are tested only at the recorded samples. With sparse sampling, two samples in a row can both lie outside a zone while the line between them cuts through it. That line is kept.
  - Polylines already draw a straight line from where the track entered a zone to where it left, so they look the same either way.
  - A segment track has no such excuse: the spec says its pieces are "never joined across what was left out". The line through the zone joins them, and the matcher could find an effort on a segment inside the zone.
- **E2.** `getActivityStreams` fetches each streams part's blob one after another and caches only successes. Every view of an activity whose blob is missing, undecodable or on an unreachable PDS repeats the outbound fetch. A blob is named by its content (CID), so a fetch that failed on its content will fail the same way every time.
- **E4.** `publish` lists the region's ridden candidates that it hasn't published. A candidate that duplicates a live segment is never published, so every run lists it again and checks it with its own `checkSegment` call, one at a time. In a region where athletes made their own segments on popular trails, that's hundreds of sequential calls per run that always give the same answer.

## What Changes

- **Segment tracks (P8).** A line between two shown samples that passes through a zone is cut where it enters and leaves the zone. The track splits into separate pieces there, as it does when a sample falls inside. Polylines get the same cut points and look unchanged.
- **Streams (E2).**
  - The AppView fetches an activity's streams parts at the same time.
  - It remembers a failed fetch by CID. A blob whose content isn't valid stream data is never fetched again. A blob that was missing or couldn't be reached is fetched again only after a growing pause.
  - Two viewers asking for the same uncached blob at once cause one fetch.
- **Importer (E4).** A new importer-only procedure, `importer.checkSegmentCandidates`, checks up to 500 staged candidates by id in one call, with the same duplicate test as `checkSegment`. `publish` calls it once per page of ridden candidates, instead of once per candidate. The candidate lines are already in the AppView, so the importer sends only ids.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-track`: "A segment track leaves out privacy zones" gains a scenario for a line between samples that passes through a zone.
- `feed/detail-visibility`: two requirements added. Streams data that can't be fetched isn't fetched again on every view, and the parts are fetched together, once per blob at a time.
- `segments/segment-import`: "Only ridden candidates that duplicate nothing are published" now says a page of candidates is checked in one call.

## Impact

- `core/location`: `RouteTrim.kt` (`trimPieces`), with tests in `RouteTrimTest`. The change reaches both polylines and segment tracks through `PrivacySettings` in `core/data`.
- `server/appview`:
  - `api/ActivityStreamsViews.kt` (parallel parts, failure memory, one fetch per CID at a time).
  - A Flyway migration for the failure columns.
  - The new route and its handler beside `SegmentCandidateViews`.
  - Tests in `AppViewTest`, or the streams and candidate test classes beside it.
- `lexicons/app/activitytracker/importer/checkSegmentCandidates.json`, its mirror in `core/lexicon` (`Queries.kt`) and `Nsid`.
- `server/segment-importer`: `Publish.kt`, with tests in its publish tests and `FakeServices`.
- Existing segment tracks aren't rewritten by this change. They're cut the new way the next time they're trimmed (zones saved, activity edited). No client UI changes.
