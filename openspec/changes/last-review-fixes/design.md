# Design

## Context

The three fixes share no code. The proposal says why each is wanted.

**P8.** `TrackMath.trimPieces` (`core/location/RouteTrim.kt`) works as follows:
- It marks each sample hidden or shown: inside a `PrivacyCircle`, or within `endsMeters` of either end measured along the track.
- Where two consecutive samples differ, it cuts at the boundary, found by bisection (`crossing`).
- Two shown samples in a row are always joined.
- `trim()` flattens the pieces into one line, so a polyline draws a straight line across a zone anyway. `PrivacySettings.pieces` keeps the pieces apart, which is what a segment track holds.
- The hidden ends are measured along the track, which only grows, so a line between two shown samples can never lie inside them. Only circles can be crossed between samples.

**E2.** `ActivityStreamsViews.streams()` handles each visible part in turn:
- It reads `activity_stream_data` by CID, one transaction per part.
- On a miss, it calls `StreamSource.fetch`, decodes the result, bounds it, and stores it on success.
- `StreamSource.fetch` returns null for several unrelated reasons: no PDS resolved, a non-200 answer, a blob over `MAX_DATA_BYTES`, or a space blob that couldn't be had. A network failure throws, and `runSuspendCatching` turns it into null.
- `StreamsIndex` deletes cache rows whose CID no streams row names.
- There are at most four parts, one per tier.

**E4.** `publish` pages through `getRiddenSegmentCandidates` (500 a page). For each candidate it makes these calls:
1. `checkSegment(sport, line)`.
2. If that finds no duplicate, `getRecord`, to see whether the repo already holds it.
3. If not, `createRecord`.

The `getRecord` step stays. The spec has `publish` leave alone a segment the AppView hasn't indexed yet, and `getRidden` leaves out only indexed ones. The AppView already holds every staged candidate's line (`segment_candidates`). `SegmentViews.check` runs `duplicatesOf(sport, line)` and filters the results with `segmentServed(viewer)`.

## Goals / Non-Goals

**Goals:**
- A segment-track piece never spans a zone, wherever the samples fall.
- No streams blob is fetched more than once per pause while it keeps failing, and never again once its content is known to be bad.
- A `publish` run's AppView calls grow with its pages, not its duplicates.

**Non-Goals:**
- Rewriting existing segment tracks or polylines now. They pick up the new cut the next time they're trimmed (zones saved, activity edited or combined). The trim's revision marker isn't bumped, so nothing re-trims everything.
- Telling clients that a part was left out. `parts` already lists only the parts served, as today.
- Keeping a candidate's duplicate status in the AppView. A duplicate can stop being one, so the check stays a live test at publish time.
- Changing `checkSegment` for athletes' clients.

## Decisions

**P8: test every line between samples against each circle, and cut where the line is inside.**

For each pair of consecutive samples A and B, and each circle neither of them is inside:
1. Project A, B and the circle's centre onto a local flat plane around the centre (equirectangular: metres east and north).
2. Find the fraction `t*` of the way along A to B that comes closest to the centre.
3. If the point at `t*` is inside the circle by `PrivacyCircle.contains` (the true distance, the same test samples get), bisect `[0, t*]` for the entry and `[t*, 1]` for the exit, reusing `crossing` with the same `hidden` predicate.
4. Finish the current piece with the entry point and start a new piece at the exit point.

Several circles on one line are taken in order of their entry fractions. Overlapping circles merge into one hidden stretch.

- Lines between samples are at most a few km long, so the flat projection's error is far below the 200 m minimum radius. Confirming with `contains` keeps a line just touching the circle consistent with the sample test.
- A line between a hidden sample and a shown one can also cross a *different* circle, and the spec covers that line too. So the loop works per line, not per shown pair: each line's hidden stretches are the union of the stretches inside each circle it crosses and the hidden end at a hidden sample. The shown parts between them are emitted, and a new piece starts after each hidden stretch.
- *Alternative:* add extra samples along long lines so every gap is shorter than the smallest radius. That's simpler to reason about, but it adds points to every polyline and still misses a line that only clips the edge of a circle.

**E2: failures in a table of their own, keyed by where the blob came from.**

A new table `activity_stream_fetch_failures` (V40):
- Key: `(blob_cid, author_did, origin)`. Missing and unreachable depend on the host, so a failure for one author's copy says nothing about another's.
- `kind`: `content` or `unavailable`.
- `failures`, the count.
- `retry_at`: epoch millis, null for `content`.

Rules:
- A `content` row for a CID, from any source, means the data is never fetched again. A CID names its bytes, so they'll be just as bad from anywhere.
- An `unavailable` row is skipped until `retry_at`, which is now plus `min(1 h, 1 min × 2^(failures − 1))`.
- A successful fetch deletes the CID's rows.
- `StreamsIndex`'s cleanup deletes failure rows for CIDs no streams row names, beside the cache cleanup.

To tell the cases apart, `StreamSource.fetch` keeps its signature but stops dropping a blob that's too large:
- `null` means missing (no PDS, a non-200 answer, or the space has no such blob), counted as `unavailable`.
- A thrown exception means the host couldn't be reached, counted as `unavailable`.
- Bytes are classified by `ActivityStreamsViews`. Over `MAX_DATA_BYTES` counts as `content`; the PDS path still reads at most one byte past the limit, so a large blob is never held whole. An undecodable body also counts as `content`.

- *Alternative:* a sealed result (`Bytes`, `TooLarge`, `Missing`). That says the same thing with a new type, and every test fake of `StreamSource` would have to change.

- *Alternative:* a nullable `stream_data` column in `activity_stream_data`. That mixes two meanings in one row, needs a nullability change that has to be portable between H2 and Postgres, and loses the per-source key.
- *Alternative:* remember failures in memory only. That's lost on restart, and the review's point is the repeated outbound fetch, which a restart would bring back.

**E2: fetch the parts at once, and share fetches in flight.**
- `streams()` reads the cached rows and the failure rows for all parts in one transaction.
- It then fetches the remaining parts inside `coroutineScope { … async … awaitAll() }`, keeping the parts' widest-first order for `mergeStreams`.
- A process-wide map from `(cid, author, origin)` to the `Deferred` of a fetch in flight lets concurrent requests await one fetch. The entry is removed when the fetch finishes.
- Every viewer awaiting it has already passed `visibleStreams` for that part, so sharing the bytes admits no one new.
- *Alternative:* a general request coalescer such as a caching library. It's not needed for one map, and nothing else here uses one.

**E4: an importer-only procedure that checks staged candidates by id.**

`app.activitytracker.importer.checkSegmentCandidates`:
- Input: `{ region, ids: string[] }`, with at most `SegmentCandidates.MAX_BATCH` ids.
- Output: `{ candidates: [{ id, duplicates: at-uri[] }] }`.

How it works:
- It's a procedure (POST) because 500 ids don't fit in a query string, like `checkSegment`.
- It refuses non-importers with `NotImporter` through `requireImporter`, as the other importer routes do, and an unknown region or too many ids with `InvalidRequest`.
- In one transaction it reads the named candidates of that region, decodes each stored line, and runs `duplicatesOf` with the `segmentServed(importer)` filter, as `check` does.
- An id that isn't staged in the region is left out of the output. `publish` doesn't publish a candidate the check didn't return, which covers a candidate a concurrent `stage` finished away.

Then `publish` makes one check call per page. It walks the page in order, with the same `exists`/`create` steps for candidates with no duplicates. It reports candidates that weren't found as `gone`, beside `present`.
- *Alternative:* send `{sport, line}` pairs in a batch `checkSegment`. That sends up to 500 lines of up to 50 km back to the AppView that already holds them, and widens a client-facing procedure.
- *Alternative:* add `duplicates` to `getRiddenSegmentCandidates`' output. That puts the duplicate test on a read that `getRidden` documents as "only what was staged", and every page would pay for it, even when listing for another purpose.
- *Alternative:* accept the cost, since runs are manual. That was considered and rejected when this change was scoped.

## Risks / Trade-offs

- [P8: a near-tangent line could split into a tiny piece of one or two points] → Pieces too short to match nothing are harmless; `SegmentGeometry.passes` needs a real overlap. A test covers a line passing just outside a circle staying whole.
- [P8: polylines gain two points per crossed zone] → They look the same, and `polylineGaps` is unaffected, because `RouteBreaks` breaks by time and distance, not by cut points.
- [E2: a blob that becomes available within the pause is served late, by up to an hour] → The pause starts at a minute and only grows while failures continue. A later successful fetch clears it.
- [E2: the in-flight map grows if a fetch hangs] → The HTTP client's timeouts bound each fetch, and the entry is removed in `finally`.
- [E2: a `content` row is reached only through a CID that no longer changes] → Correct by construction. A new upload has a new CID.
- [E4: 500 duplicate tests in one request] → Each test reads only segments in the line's end cells, as indexing does. A page stays well within the importer's 60 s timeout. The page size can drop if a test shows otherwise.

## Migration Plan

- `V40__activity_stream_fetch_failures.sql` creates the table, with portable types: `VARCHAR` and `BIGINT`, with `retry_at` nullable. No backfill is needed; the table starts empty.
- The new lexicon procedure is additive. An older importer keeps calling `checkSegment` and still works.
- Rollback: an older AppView ignores the new table, and the importer falls back to an older build.
