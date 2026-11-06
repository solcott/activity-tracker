# Design

## Context

`segment-elevation-profile` (implemented and archived) made the AppView's `elevation` job sample terrain every 10 m along a segment's line. Smoothing is ±25 m (`ElevationProfile.smooth`), gain and loss use a 3 m threshold, and the job stores a 128-point profile in `segments.terrain_profile`. `getSegment` serves the profile as `elevationProfile { heights }`, only when the elevation source is terrain. The segment presenter turns it into `SegmentProfile`, with grades measured over about ±25 m between profile points, and `SegmentUi` shows the gain, loss and average grade line above the chart. The motivation is in proposal.md; the behaviour is in the spec delta.

## Goals / Non-Goals

**Goals:**
- Measure max grade on the AppView, at full sample resolution.
- Take high and low from the profile with no lexicon field.
- Make the backfill reuse the existing `elevation` job.

**Non-Goals:**
- Changing the grade tint's calculation.
- Serving the extremes on `segmentView`.

## Decisions

**Max grade comes from the AppView's 10 m samples, not the client's 128 points.**
- The profile's spacing is distance / 127: about 41 m on Longview's 5.2 km and about 160 m on a 20 km segment.
- A ±25 m window on those points widens to at least one spacing each side. That understates short steep pitches on long segments.
- The AppView already holds the smoothed 10 m heights and their along-line distances (`atM`) in `terrainOf`, so measuring there costs nothing.
- *Alternative:* compute on the client from the profile. That needs no lexicon change, but it is wrong on long segments and inconsistent across segment lengths.

**The window is by distance, using each sample's actual position.**
- `ElevationProfile.maxGrade(heights, atM, halfWidthM = 25.0)` takes, for each sample, the farthest samples within 25 m either side, narrowed at the ends.
- The grade is |Δh| / Δd for that pair. The function returns the maximum in percent, or null for fewer than 2 samples.
- It lives in `core/location` next to `smooth` and `even`, so it is tested once and is available to any target.
- Resampling leaves the last stretch shorter than 10 m, so using `atM` rather than assuming 10 m keeps the end exact.

**Stored as integer tenths of a percent.**
- Lexicons have no floats. Tenths keep enough precision for the screen (whole percent) and any later use.
- The column is `segments.terrain_max_grade INTEGER NULL`, written in the same update as `terrain_profile`, and null whenever the profile is.
- In the lexicon it is `elevationProfile.maxGrade`: an optional integer, minimum 0, described as tenths of a percent.
- *Alternative:* store `maxGrade` on `segmentView`. Rejected: search and list results don't need it, and the profile change already chose `getSegment` alone.

**High and low come from the profile's heights on the client.**
- The profile is a linear resample of the smoothed heights, so its min and max are within a few decimeters of the samples' extremes. The Longview check matched both apps within 2 ft.
- `SegmentProfile` gains `highM`, `lowM` and `maxGradePercent: Double?`. The first two are derived in the presenter, next to the grades.

**The extremes go on a line of their own under gain, loss and average grade.**
- New string `segment_elevation_extremes`: "%1$s high · %2$s low · %3$s max grade", with a variant without max grade. Elevation uses `Format.elevation`.
- It shows whenever `state.profile` is non-null, so it appears and disappears with the chart.

**Backfill through the start-up terrain backfill, not the migration.**
- V55 only adds the column.
- At start, `SegmentMatcher.backfillTerrain` queues, in one `INSERT … SELECT … WHERE NOT EXISTS` (`SegmentJobs.enqueueElevations`), an `elevation` job for each segment with no terrain gain whose box touches the tiles' coverage. Its condition widens to `terrain_gain_m IS NULL OR terrain_max_grade IS NULL`.
- Covered segments get their max grade on their next sample. A covered segment always gets a max grade from that job, so the condition stops matching it and later starts don't re-queue it. Uncovered segments outside the tiles are never queued, as now.
- *Alternative:* queue every segment from V55, as V54 did. Rejected: it re-queues segments outside the tiles, and doesn't heal a segment whose job failed.

## Risks / Trade-offs

- **Smoothed 10 m terrain overstates steep pitches against lidar** (16% against 13% on Longview). → Accepted. It is still far closer than raw sampling, and the spec defines the measure as smoothed terrain.
- **The first start after this change re-samples every covered segment** (about 158k locally). → One statement queues them and the matcher drains them in the background, as for the smoothing backfill. Segments outside the tiles aren't queued.
- **A covered segment whose line is too short for two samples would have no max grade and be re-queued every start.** → `maxGrade` returns null only for fewer than two heights. Any line with terrain gain has at least its start and end samples, so it always gets one.
- **Profiles served before resampling have no `maxGrade`.** → The field is optional, and the screen shows high and low without it.

## Migration Plan

1. Deploy the AppView with V55. On start, the terrain backfill queues covered segments without a max grade; they resample in the background, and `maxGrade` appears as their jobs drain.
2. Ship clients. Older clients ignore the new field.
3. Rollback: the column and field are additive. An older AppView ignores the column, and an older client ignores the field.
