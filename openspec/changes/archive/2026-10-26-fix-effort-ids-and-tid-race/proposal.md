# Proposal

## Why

The 2026-10-23 codebase review put two correctness bugs first. In the AppView, the segment matcher deduplicates efforts on a pass's rounded start but builds the effort id from that start clamped to 0. So a segment track whose times go negative gives two passes the same id, the insert fails on the primary key, and the `track` job retries for about an hour before giving up, leaving the activity with no efforts (C3). Segment-track blobs are untrusted input, and the AppView checks nothing about their times beyond the array lengths. On the client, `Tid.next` reads and writes its last timestamp without synchronization, so two threads in the same microsecond can get the same rkey, and one record's `putRecord` silently overwrites the other's (C11).

## What Changes

- **A malformed segment track gives no efforts.** The AppView treats a segment-track blob that doesn't decode, whose arrays don't line up, or whose times are negative or go backwards as an unusable track. It removes the activity's efforts, the track's cells and its candidate riders, and finishes the job instead of retrying it. Times that repeat (whole seconds at more than 1 Hz) stay valid. A blob that can't be fetched is still retried, as before.
- **Effort ids can't collide.** The matcher computes each pass's start offset once and uses it for both deduplication and the id.
- **Client record keys are unique under concurrency.** `Tid.next` advances its last timestamp with an atomic compare-and-set, so concurrent callers always get distinct, increasing TIDs.

Out of scope: the client-side origin of negative times. Imports and recordings are sorted by time, so the client doesn't produce them in practice, and the AppView fix stands without it.

## Capabilities

### New Capabilities
- `data/record-keys`: How the client generates record keys: TIDs that are unique and increasing within the app, including when generated concurrently.

### Modified Capabilities
- `segments/segment-efforts`: "Efforts come only from usable segment tracks" now says a track whose data is malformed isn't usable, and that it removes the efforts and candidate riders an earlier version of the track gave.

## Impact

- **server/appview** (`segments/SegmentMatcher.kt`): validation of the decoded blob in `matchTrack`, an invalid-track path that clears efforts, cells and riders, and a single start offset for dedupe and id. No schema, migration, lexicon or route changes.
- **core/lexicon** (`Tid.kt`): `kotlin.concurrent.atomics.AtomicLong` from the stdlib (opt-in `ExperimentalAtomicApi`). No new dependency and no signature change.
- **Tests**: `SegmentMatcherTest` for malformed tracks and colliding starts; a concurrent-uniqueness test for `Tid` in `core/lexicon`'s `jvmTest`.
