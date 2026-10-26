# Tasks

## 1. Malformed segment tracks give no efforts (AppView)

- [x] 1.1 In `SegmentMatcher.matchTrack`, decode and validate the blob (decodes as `SegmentTrackData`; each piece's `lat`, `lng`, `t` the same length; first `t` not negative; `t` never decreasing) and report an invalid track separately from a failed fetch; verify by `SegmentMatcherTest` cases for times going backwards, a negative first time, mismatched arrays and undecodable JSON, each leaving no efforts and no queued job
- [x] 1.2 Handle an invalid track in one transaction that deletes its efforts, cells and candidate riders, log the track URI and reason, and finish the job through `done()`; verify by a `SegmentMatcherTest` case where a track with an effort is replaced by an invalid blob and the effort, the cells and a candidate rider are gone
- [x] 1.3 Keep the backoff for a failed fetch; verify `aFailedFetchBacksOff` still passes unchanged
- [x] 1.4 Add a `SegmentMatcherTest` case where a piece repeats whole-second times (several samples per second) along a segment and still gets its effort

## 2. One start offset per effort (AppView)

- [x] 2.1 In `replaceEfforts`, compute each pass's offset once, dedupe on segment and offset, and use it for the id and `startOffsetS` without the clamp; verify the existing two-laps and idempotency tests pass and `./gradlew :server-appview:test` is green

## 3. Concurrent-safe TIDs (client)

- [x] 3.1 Replace `Tid.last` with a stdlib `AtomicLong` and advance it with a compare-and-set loop (`max(now, prev + 1)`), keeping `next`'s signature; verify `RecordSerializationTest.tidsAreSortableAndUnique` still passes
- [x] 3.2 Add a `core/lexicon` `jvmTest` that generates TIDs from many threads at once under a fixed `Clock` and asserts every one is distinct, and that a clock set back still gives later TIDs; verify it fails against the old `Tid` and passes with 3.1 (`./gradlew :core-lexicon:jvmTest`)

## 4. Integration

- [x] 4.1 Run `./gradlew check` and confirm it passes (ktfmt, detekt, all targets)
- [x] 4.2 Remove C3 and C11 from `docs/reviews/2026-10-23-codebase-review.md`, listing them under "Fixed since the review" with their commits, and update "Suggested order"; verify the doc no longer lists them as open

## Workflow follow-up

- Commit only when asked, as two commits: the AppView fix (groups 1 and 2) and the TID fix (group 3).
- Archive the change once it is committed.
