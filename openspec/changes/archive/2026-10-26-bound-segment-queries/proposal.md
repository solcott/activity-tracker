# Proposal

## Why

`feed.searchSegments` is open to anyone, signed in or not, and keeps reading candidate segments until it fills a page or runs out (review finding E1). A box crowded with segments whose stored box overlaps it but whose line doesn't cross it makes one request read every one of them in one transaction. `feed.getSegment` builds a view of every effort the viewer has on the segment just to keep the fastest (E3), so its cost grows with how often they've ridden it. Both should cost a bounded amount per request before the AppView is public.

## What Changes

- `feed.searchSegments` reads at most a fixed number of candidate segments per request. When it stops before filling a page, it returns what it found, which may be fewer than the limit or none, with a cursor to carry on from. A search ends only when a page comes back without a cursor.
- The `searchSegments` lexicon's description says so, so clients keep paging while there's a cursor instead of treating a short page as the end.
- `feed.getSegment` reads only the viewer's single fastest effort it may see, instead of building views of all of them. The response doesn't change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-records`: "Segments can be found by area" now bounds the work of one request and allows a short or empty page with a cursor.

## Impact

- `server/appview`: `SegmentViews.search` and `SegmentViews.segment`, with tests in `SegmentsTest`.
- `lexicons/app/activitytracker/feed/searchSegments.json` and the `SearchSegments` KDoc in `core/lexicon`: description only, no schema change.
- Clients: no app screen pages through search today (`SegmentRepository.search` has no caller outside its test), so nothing in the app changes. A future caller must follow the cursor.
