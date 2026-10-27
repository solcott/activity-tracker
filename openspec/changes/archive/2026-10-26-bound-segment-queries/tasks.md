# Tasks

## 1. Bound segment search

- [x] 1.1 In `SegmentViews.search`, read candidates in batches of an internal `searchBatch` (default `Paging.MAX_LIMIT`) and stop after `searchBatches` (default 3), keeping the existing cursor rules (last shown when more than `limit` cross, last read when the batches ran out, null when exhausted); add both as internal constructor parameters and update the class and function KDoc; verify `./gradlew :server-appview:test --tests "*.SegmentsTest"` still passes
- [x] 1.2 Add a `SegmentsTest` case that indexes, through the `Indexer`, more decoy segments than a small cap examines (a bounding box and cells overlapping the search box, a line that misses it) and then one crossing segment, and searches through a `SegmentViews` built over the same database with `searchBatch` and `searchBatches` set small: the first page is empty with a cursor, and following cursors returns the crossing segment exactly once and ends without a cursor; verify the test passes
- [x] 1.3 Say in `lexicons/app/activitytracker/feed/searchSegments.json`'s description, and in the `SearchSegments` KDoc in `core/lexicon`, that a page may hold fewer segments than `limit`, or none, with a cursor, and that only a missing cursor ends the search; verify `./gradlew :core-lexicon:jvmTest` passes and the JSON still parses

## 2. Read only the viewer's best in getSegment

- [x] 2.1 In `SegmentViews.segment`, get the viewer's best with `effortViews(..., order = elapsed_s ASC, id ASC, limit = 1)` instead of building every view and taking the minimum; verify `getSegmentServesTheSegmentAndTheViewersBest` still passes
- [x] 2.2 Extend that test, or add one, so the viewer has several efforts and the fastest isn't the first begun, and the view's best is the fastest (an athlete always sees their own efforts, so none of theirs can be hidden from them); verify it passes

## 3. Verify and record

- [x] 3.1 Run `./gradlew check` and confirm it passes
- [x] 3.2 Move E1 and E3 in `docs/reviews/2026-10-23-codebase-review.md` to "Fixed since the review" with the commit hashes, once committed; verify the Efficiency table no longer lists them

## Workflow follow-up

- Commit only when asked, as small commits by concern: search bounds with the lexicon note (group 1), `getSegment` (group 2).
- Archive the change once it is committed.
