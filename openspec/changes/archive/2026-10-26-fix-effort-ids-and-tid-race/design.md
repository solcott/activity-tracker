# Design

## Context

**Segment matching.** The indexer stores a segment track's row with only the blob's CID. `SegmentMatcher.matchTrack` fetches and decodes the blob (`SegmentTrackData`) inside a `track` job, and the existing `require(lat.size == t.size ...)` there is the only check on its contents. Any exception from the job, a bad blob or a failed fetch alike, goes to `failed()`: the job backs off from 30 s, doubling, and is dropped once the next delay would pass 1 h (eight attempts). Each attempt's transaction rolls back, so the efforts, cells and candidate riders of the track's previous version stay. `replaceEfforts` dedupes passes on `pass.start.roundToLong()` and builds the id (`"<activity> <segment> <offset>"`, the primary key) from that value `coerceAtLeast(0)`, so two negative starts that dedupe apart share an id.

Segment-track times are whole seconds since the first point of the full track (`segmentTrackData`), so a piece sampled faster than 1 Hz repeats times.

**TIDs.** `Tid` is a common-code `object` holding a plain `var last`. Thirteen callers in `core/data` run on multi-threaded dispatchers (JVM, Android, Kotlin/Native). The repo is on Kotlin 2.4.21 and uses no atomics library.

## Goals / Non-Goals

**Goals:**
- A blob the matcher can never match is handled once, with nothing left behind from earlier versions.
- One value decides both whether two passes are the same effort and the effort's id.
- `Tid.next` stays a plain, non-suspending function with the same signature.

**Non-Goals:**
- Validating anything else about a track (coordinates out of range, implausible speeds, gaps). Coordinates go through `SegmentCells`/`SegmentGeometry` as before, and implausible efforts are already kept off leaderboards by the speed cap.
- Making `Tid` unique across processes; the random clock id already covers that.

## Decisions

**Validate in `matchTrack`, after decoding.** The blob first exists there; the indexer never fetches it. A track is invalid when the JSON doesn't decode as `SegmentTrackData`, a piece's `lat`, `lng` and `t` differ in length, a piece's first `t` is negative, or a piece's `t` decreases. The check runs per piece: times may restart between pieces only if each piece is non-decreasing on its own, which matches how pieces are cut from one track. *Alternative:* strictly increasing times. Rejected, because sub-second sampling repeats whole seconds in honest tracks. *Alternative:* drop the offending samples and match the rest. Rejected: it guesses at what a malformed track meant, and an honest client never sends one.

**An invalid track goes through the same path as a track that gives no efforts.** `matchTrack` signals invalid data separately from other failures (a dedicated exception or a null result from decoding), and handles it in a transaction that deletes the track's efforts, cells and candidate riders, as `trackToMatch`'s `none()` does plus the cells. The job then completes normally through `done()`. A failed fetch (`blobs.fetch` returning null or throwing) keeps the current backoff, since it may succeed later. *Alternative:* keep throwing and let the backoff give up. Rejected: an hour of pointless fetches, and the stale efforts stay.

**Clearing the cells too.** Without cells the track no longer gets re-queued when a segment or candidate is created nearby, which is right since it can't match. The cost is that startup `backfill()` re-queues every usable track with no cells, so an invalid track is fetched and rejected once per AppView start. Tracks that legitimately have no cells already behave this way, so no marker column or migration is added.

**One start offset.** `replaceEfforts` maps each pass to `offset = pass.start.roundToLong()` once, dedupes on `(segment, offset)` and uses that same offset for the id and `startOffsetS`. With validation, offsets are never negative, so the clamp goes. Two passes starting within the same rounded second on one segment remain one effort, as now.

**Stdlib atomics for `Tid`.** `last` becomes a `kotlin.concurrent.atomics.AtomicLong`. `next` loops: load `prev`, compute `max(nowMicros, prev + 1)`, and return once `compareAndSet(prev, micros)` succeeds. This is common code on every target, needs only `@OptIn(ExperimentalAtomicApi::class)`, and adds no dependency. *Alternative:* atomicfu, as the review suggested. Rejected: a new plugin and dependency for what the stdlib now has. *Alternative:* a coroutine `Mutex`. Rejected: it would make `next` suspend for 13 callers.

## Risks / Trade-offs

- [A client bug that produces a backwards time in one piece now loses all of that activity's efforts, not just the bad pass.] → Imports and recordings are sorted by time, so this isn't expected. The rejection logs the track URI and reason at `info`, so it shows up if it happens.
- [`ExperimentalAtomicApi` could change.] → It's one call site in one file, and the API's shape (load/compareAndSet) is stable across the versions that have it.
- [The concurrency test could pass by luck against a broken implementation.] → It uses a fixed `Clock`, so every thread asks for the same microsecond and the old code collides on almost every run.

## Migration Plan

No schema or data migration. Tracks that already exhausted their retries stay without efforts until their next update or the next AppView start, when `backfill()` re-queues them (they have no cells) and they are matched or rejected under the new rules.
