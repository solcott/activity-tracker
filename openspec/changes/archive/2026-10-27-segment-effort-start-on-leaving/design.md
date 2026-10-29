# Design

## Context

`SegmentGeometry.passes` (`core/location`) finds passes in three steps:

1. It finds a run of samples within `START_RADIUS_M` of the start and takes the closest one.
2. It calls `follow` from there to validate the corridor and reach the end circle.
3. `closestToEnd` picks the end sample, and `timeAt` interpolates each endpoint on whichever neighbouring stretch runs closer to the point.

Step 3 is the faulty part (see proposal.md, Why). When the closest sample is just after a stop or a recording gap, the closer stretch is often the stationary one. The time then lands inside the stop.

`SegmentMatcher` is the only caller. It calls `passes` for efforts (`replaceEfforts`, which uses `start`/`end`) and for candidate riders (`replaceCandidateRiders`, which only checks that a pass exists). Effort ids use `start.roundToLong()`.

## Goals / Non-Goals

**Goals:**
- Time a pass between the start line and the end line. Never count a stop or recording gap before the start line or after the end line.
- Keep sub-sample accuracy when samples are sparse and the athlete is moving, like `sparseSamplesInterpolateTheEndpoints` today.
- Leave unchanged which passes are found: their count, direction, corridor, corner-cut and lap handling.

**Non-Goals:**
- Moving time. A stop past the start line or before the end line still counts.
- Changing radii, the corridor, or segment geometry from the importer.
- Matching another service's segment endpoints.

## Decisions

### Time crossings of lines across the segment, not the closest sample

The start line goes through `ref.first()`, at right angles to the first reference stretch (`ref[0] → ref[1]`). The end line goes through `ref.last()`, at right angles to the last stretch. For a sample p, its signed position along a line's direction is the dot product of `local(p, point)` with the stretch's unit vector. Here `point` is the line's start or end point, and `local` already exists. Negative means short of the line; positive means past it.

- **Start**: the last stretch `(k, k+1)` whose signed position goes from below 0 to 0 or above. The search covers the start run's samples, plus the sample before the run and the one after it. Then interpolate the time where it hits 0.
- **End**: the first such stretch for the end line, from the sample before `follow` arrives in the end circle onwards, while samples stay within `END_RADIUS_M`. Ending at the first crossing replaces `closestToEnd`'s "keep getting closer" walk for timing.
- **Both are bounded by the track's progress along the line.** `follow` reports two sample indices along with the finish:
  - `leftStart`: the first sample past the line's second reference point (about 20 m along). The start search stops at that sample.
  - `arrived`: the first sample in the end circle after the last stretch is reached. The end search begins on the stretch into that sample.
  - Without these bounds, tight switchbacks that stay inside a 25 m circle could recross a line: a later leg near the start would move the start late, and an earlier leg near the end would end the pass early. A first version, which walked back over the samples near the end, did exactly that.

Alternatives considered:

- **Last near-closest sample, forward-only interpolation** (the explore-mode idea). It handles stops, but loses up to a whole sample interval on sparse tracks. It breaks the existing 40 m test (start 8.0 s instead of 4.0 s), and the result still depends on GPS drift through the "near-closest" tolerance.
- **Drop stationary samples before matching.** That changes the track that passes validate against, and needs a speed filter tuned per sport. It's more invasive for the same result.

### Slow crossing stretches snap outward from the stop

A crossing stretch whose speed (`distance / Δt`) is below `STOPPED_SPEED_M_PER_S = 1.0` spans a stop or a gap. Its interpolated time can't be trusted. The start takes the stretch's later sample time, and the end takes its earlier sample time.

For a slow but moving athlete (hike-a-bike under 1 m/s), the error is at most that one stretch, and it errs in their favour. 1.0 m/s sits below any sport's moving pace and above GPS drift.

Alternatives considered:

- **A Δt threshold (for example, over 5 s).** A sparse but moving track (smart recording every 8 s) would then lose interpolation for no reason. Speed is what separates a stop from a sparse sample.

### Fallbacks keep today's pass set

When no forward crossing exists in the window, use the old sample time with no interpolation: the start run's closest sample, or the end sample from `closestToEnd`. This happens when a piece starts inside the start circle already past the line, or ends inside the end circle short of the line. Validation still runs from the closest start sample exactly as today, so `follow` and the loop rule (`i = end`) stay as they are, and the pass list is identical apart from times.

Times stay `Double` seconds in `SegmentPass`, so callers are unaffected.

### Rematch all tracks with a SQL migration

Add `V43__rematch_segment_tracks.sql`, portable SQL, the next free version:

```sql
INSERT INTO segment_match_jobs (kind, subject_uri, enqueued_at, attempts)
SELECT 'track', st.uri, 0, 0 FROM segment_tracks st
WHERE NOT EXISTS (SELECT 1 FROM segment_match_jobs j WHERE j.kind = 'track' AND j.subject_uri = st.uri);
```

- `enqueued_at = 0` makes the jobs due at once, oldest first, at the matcher's normal batch pace.
- `trackToMatch` re-checks usability, so queuing tracks whose matching is off is harmless: their jobs no-op.
- The `NOT EXISTS` keeps the primary key safe against jobs already queued.

Alternatives considered:

- **A Kotlin `JavaMigration`.** No record JSON needs reading, so plain SQL is enough.
- **Extending `SegmentMatcher.backfill`.** It only queues tracks with no cells, and making it unconditional would rematch on every start.

## Risks / Trade-offs

- **[Risk]** Effort ids change for efforts whose start moves to a different rounded second, so anything that kept an effort id goes stale. → Nothing outside `segment_efforts` stores effort ids (efforts are derived and served by query), and `replaceEfforts` deletes and re-inserts per activity.
- **[Risk]** The rematch fetches every segment track blob once, which loads the athletes' PDSes. → The jobs run at the existing batch size and back off on failure. Volume is one fetch per track, the same as the first backfill.
- **[Risk]** A start line at right angles to the first stretch is a poor fit when the segment starts on a tight bend. → The search window is the start run (25 m), and the fallback is the old closest-sample time, so the worst case is today's behaviour minus the stop bug.
- **[Trade-off]** GPS drift during a stop *past* the start line can make the track cross back over the line. The last forward crossing is then a drift crossing inside the stop, and the stop is partly excluded. → This errs in the athlete's favour and is bounded by the stop.

## Migration Plan

1. Ship the geometry change and V43 together. Flyway runs V43 on start, and the matcher drains the queued jobs.
2. Rollback: revert the code and queue the same rematch again in a later migration. Never edit V43 once it's committed.
