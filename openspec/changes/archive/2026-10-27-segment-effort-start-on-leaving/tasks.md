# Tasks

## 1. Line-crossing timing in SegmentGeometry

- [x] 1.1 In `core/location/src/commonTest/.../SegmentGeometryTest.kt`, add failing tests for the spec's scenarios. Verify that they fail against the current `passes`.
  - stop short of the start: 1 Hz samples held 8 m short for 144 s, then riding
  - recording gap across the start line: one sample 8 m short, the next 144 s later 7 m past
  - stop just past the start: counts
  - stop inside the end circle after crossing the end line: excluded
  - slow but moving crossing under 1 m/s: snaps to the later sample
- [x] 1.2 Add the signed along-line position helper and `STOPPED_SPEED_M_PER_S = 1.0` to `SegmentGeometry` (design: Decisions). Replace `timeAt` with a start-line crossing (the last forward crossing in the start run, including the stretches entering and leaving it) and an end-line crossing (the first crossing from the stretch arriving in the end circle). A slow stretch snaps outward, and with no crossing, fall back to the closest-sample time. Leave `follow`, the run detection and the loop rule as they are. Verify that 1.1's tests pass.
- [x] 1.3 Check that pass detection is unchanged: run `./gradlew :core-location:jvmTest` and verify every existing `SegmentGeometryTest` passes unmodified, including `onePassWithInterpolatedTimes`, `twoLapsOfALoop` and `sparseSamplesInterpolateTheEndpoints`.
- [x] 1.4 Update the KDoc on `passes` and `SegmentPass` to describe start-line and end-line timing and the stop rule. Verify with `./gradlew :core-location:detekt` (or `check` for the module) and `./gradlew format`.

## 2. Rematch existing efforts

- [x] 2.1 Add `server/appview/src/main/resources/db/migration/V43__rematch_segment_tracks.sql`, which queues a `track` job for every `segment_tracks` row not already queued, in portable SQL as in design.md. Verify `MigrationVersionsTest` passes.
- [x] 2.2 Add a test in `SegmentMatcherTest` that rematches through the queue. Seed an effort with a stale `elapsed_s` and run `DatabaseFactory.migrate`, or the same insert. Verify `runDue()` rewrites the effort with the new timing, then run `./gradlew :server-appview:test --tests "*.SegmentMatcherTest"`.
- [x] 2.3 Add a `SegmentMatcherTest` case that sends a track with a stop at a segment's start through the matcher. Verify the stored `start_offset_s` and `elapsed_s` exclude the stop.

## 3. Integration check

- [x] 3.1 Run `./gradlew :server-appview:test` and `./gradlew :core-location:allTests`, or `check` for both modules, and verify they're green.
- [x] 3.2 On the local stack (`scripts/dev.sh`), restart the AppView so V43 runs, and let the matcher drain. Verify the 2026-10-27 ride's "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail" effort has `start_offset_s` about 4956 and `elapsed_s` about 492. Its Tom Sawyer Section effort should also no longer start at 4907.

## 4. Switchbacks near the ends

- [x] 4.1 Add `SegmentGeometryTest` cases for stacked switchbacks that recross the start line just past the start and cross the end line just before the end. Verify both fail against the walk-back version (start 35.2 s instead of 20.0 s, end 24.0 s instead of 39.2 s).
- [x] 4.2 Have `follow` report `leftStart` and `arrived`, and bound the start and end crossing searches by them (design: Decisions). Verify 4.1's tests pass along with every existing `SegmentGeometryTest`.
- [x] 4.3 Run `./gradlew :core-location:check :server-appview:check` and verify both are green.
- [x] 4.4 On the local stack, restart the AppView on the new code, queue a `track` job for every segment track again, let the matcher drain, and verify the 2026-10-27 ride's "via Exit Stage Left" effort still has `start_offset_s` 4956 and `elapsed_s` 492.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed.
