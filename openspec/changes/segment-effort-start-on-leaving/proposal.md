# Proposal

## Why

An athlete who stops at a segment's start has part of that stop counted in their effort. On a 2026-10-27 ride, a 144 s stop 8 m from the start of "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail" (the device paused recording, so the track had a gap there) added 49 s to a 492 s effort. Pass detection picks the sample closest to the start, then interpolates the start time on whichever neighbouring stretch runs closer to the start point. Here that was the stretch across the stop, so the start landed halfway through it.

The same can happen with continuous recording. GPS drift during a stop can make any sample in it the closest, and a stop near the end circle can stretch the finish the same way. Riders wait at trailheads all the time, so efforts and leaderboards come out slower than the ride, and the error varies.

## What Changes

- **An effort is timed from start line to finish line.** Each line runs across the segment through its start or end point. The effort starts at the last forward crossing of the start line before the track follows the segment, and ends at the first crossing of the end line after that. This replaces the time at the sample closest to each point.
- **Stops and recording gaps at a line aren't interpolated across.** When the crossing stretch is slower than walking pace, the start takes the stretch's later sample and the end its earlier one. A stop short of the start or past the finish never counts. A stop just past the start does, as it would on any timed course.
- Which tracks match and which segments they match doesn't change. Only the start and end times of a pass do.
- **Existing efforts are recomputed.** A one-off migration queues a `track` job for every segment track. Each job re-runs matching under the new rule and replaces its activity's efforts. Segment statuses and candidate riders are recomputed along the way, through the existing paths.
- Pass detection doesn't change otherwise: the 25 m radii, the 30 m corridor, direction, corner cutting, laps and the effort id (the rounded start offset) all stay as they are. The start offset of a pass that began after a stop changes, so its effort id changes. The `track` job already replaces efforts wholesale, so nothing is left behind.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-efforts`: the requirement "An effort's time is measured from the track" changes. Efforts are timed from the last forward crossing of the start line to the first crossing of the end line, and stops and recording gaps at a line aren't interpolated across. New scenarios cover a stop short of the start, a recording gap at the start, a stop just past the start, a stop at the end and sparse samples.

## Impact

- `core/location` `SegmentGeometry.passes`, `timeAt` and their tests (`SegmentGeometryTest`). This shared geometry is used only by the AppView's `SegmentMatcher`, for efforts and candidate riders. No client calls `passes` today.
- `server/appview`: a new Flyway migration (the next `V<n>`) that queues `track` jobs. It needs no code changes in `SegmentMatcher`.
- Data: every effort is rewritten once, after deploy, at the matcher's normal pace. Efforts that started after a stop get shorter and have new ids. Leaderboard positions can shift.
- No lexicon, API or client UI changes.
