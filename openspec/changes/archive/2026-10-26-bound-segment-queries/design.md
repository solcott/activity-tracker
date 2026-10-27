# Design

## Context

`SegmentViews.search` narrows candidates with the 0.01° cells and the stored bounding box in SQL, then checks each candidate's line against the box in Kotlin (`crosses`). It reads candidates in keyset pages of `limit` rows (oldest indexed first, `(indexed_at, uri)`) inside one `db.tx`, and loops until it has `limit` crossing segments or a page comes back short. Nothing caps the loop, so the rows read grow with the number of candidates that pass the SQL filters but fail `crosses`. The route takes no auth, and the box is capped at 1° a side, which in a dense region can hold many thousands of segments. The cursor logic already handles stopping early: `next` is the last row read when the loop ends before the candidates run out.

`SegmentViews.segment` finds the viewer's best with `effortViews(viewer, segment and author = viewer)` and then `minByOrNull { elapsed }`. `effortViews` reads every matching effort, then runs one `MIN(elapsed_s)` query per `(author, segment)` pair to set `best`. Here that's one pair, but every effort row is read and turned into a view.

## Goals / Non-Goals

**Goals:**
- The rows one search request reads are bounded by a constant, whatever the box holds.
- The cursor stays correct when a request stops early: following it returns every matching segment once, in order.
- `getSegment` reads one effort row for the viewer's best, whatever their effort count.

**Non-Goals:**
- Rate limiting or auth on search. The AppView has no rate limiter, and bounding each request is what this finding needs.
- A spatial index (PostGIS). The cells and box stay the filter, since H2 has no spatial index.
- Other effort queries. `getSegmentEfforts` already pages with a limit, and `getActivitySegments` reads one activity's efforts.

## Decisions

**Read candidates in fixed batches, at most a few per request.** Search reads candidates in batches of `Paging.MAX_LIMIT` (100) rows, whatever `limit` the caller asked for, and stops after 3 batches. That's at most 300 rows per request, enough to fill a page in any ordinary box. The batch size no longer depends on `limit`, so a caller can't make the loop read more rows by asking for small pages. The existing ending logic is kept: when more than `limit` segments cross, the cursor is the last one shown; when the batches ran out early it's the last row read; when the candidates are exhausted it's null. The batch size and count are internal constructor parameters with those defaults, so a test can set them small without indexing hundreds of segments. *Alternative:* stop after a time budget. Rejected: it isn't deterministic, and tests couldn't pin it down. *Alternative:* return a page as soon as one batch is read. Rejected: ordinary searches would need more round trips for no gain.

**A short or empty page with a cursor is normal.** Clients already must follow cursors. The lexicon's description now says a page may hold fewer segments than `limit`, or none, while a cursor is returned, and that only a missing cursor ends the search. The schema is unchanged, so this isn't a breaking wire change. No app screen calls search today, so nothing in the app changes.

**`getSegment` asks for the fastest with order and limit.** It calls `effortViews` with `order = elapsed_s ASC, id ASC` and `limit = 1`, and takes the single row. Visibility (`effortsVisibleTo`, `served` on the segment) is still applied in SQL, so the row is the fastest the viewer may see. `best` on it is still computed the same way and is true by construction. Among efforts with equal elapsed time it now picks the lowest id rather than the earliest start. The spec doesn't fix which one, and `getSegmentEfforts` already orders ties by id.

## Risks / Trade-offs

- [A client that treats a short page as the end misses segments] → The lexicon description states the contract, and no client pages search yet. The spec scenario pins it.
- [3 × 100 rows is still work for an unauthenticated caller] → It's constant per request, and each row's line check is cheap. The constants can be tuned without a spec change.
- [Ties in `getSegment` now resolve by id instead of start] → Both are equally fast efforts; nothing reads which one is shown.
