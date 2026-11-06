# Proposal

## Why

Other trail apps show a trail's high point, low point and maximum grade next to its climb, and athletes use them to judge a trail before they ride it. We checked Longview (Corner Canyon, Draper UT) against MTB Project, onX and USGS 1 m lidar:

- **High and low points.** Our smoothed terrain profile gives 5,303 and 6,021 ft. That is within 2 ft of both apps (5,302/6,020 and 5,302/6,022), so these are cheap to show and trustworthy.
- **Maximum grade.** This is where the apps disagree:
  - MTB Project shows 22%, which matches raw 10 m terrain (23%).
  - onX shows 31°, which is about 60% and clearly wrong.
  - Lidar measured over 50 m gives 13%; our smoothed terrain over 50 m gives 16%.
  - A grade measured over a short distance on unsmoothed terrain mostly reports noise. We should measure it the way the grade tint already does: over about 50 m, on the smoothed heights.

## What Changes

- The AppView's `elevation` job also derives each covered segment's **maximum grade**:
  - It is the steepest absolute grade over about 50 m (25 m either side of each point), measured on the same smoothed 10 m terrain samples it counts gain and loss from.
  - It is stored with the profile.
- `feed.getSegment`'s `elevationProfile` gains an optional `maxGrade`, an integer in tenths of a percent.
  - It is served only alongside the profile.
  - It is optional so older AppViews and segments not yet resampled still validate.
- The AppView's start-up terrain backfill also queues covered segments that have no maximum grade yet, so existing covered segments gain one.
- The segment screen shows the segment's **high point, low point and maximum grade** on a line under gain, loss and average grade, whenever it has a profile:
  - High and low come from the profile's heights.
  - Maximum grade shows only when the AppView sent it.
  - Elevation is in the viewer's units, and grade is a whole percent.
- Not in scope:
  - a maximum grade derived from efforts;
  - high, low or maximum grade on search, list or duplicate-check results;
  - climb categories;
  - splitting the maximum grade into climbing and descending.

## Capabilities

### New Capabilities
<!-- None: these extremes belong to segment elevation. -->

### Modified Capabilities
- `segments/segment-elevation`: covered segments get a maximum grade, measured over 50 m on smoothed terrain. `getSegment` serves it with the profile. Existing segments are recomputed. The segment screen shows high point, low point and maximum grade.

## Impact

- **Lexicon:** `feed.defs#elevationProfile` gains an optional integer `maxGrade`. The `core/lexicon` `ElevationProfile` mirror changes to match.
- **Shared geometry:** `core/location` `ElevationProfile` gains a distance-windowed maximum grade over a height series. The segment presenter's grade-band calculation can reuse it.
- **AppView:**
  - `TerrainTiles.terrainOf` and `TerrainElevation` gain the maximum grade.
  - `SegmentMatcher`'s elevation write stores it.
  - `Tables.kt` gets the new column, and `SegmentViews.segment` serves it.
  - Migration `V55` adds `terrain_max_grade` and queues nothing.
  - `SegmentMatcher.backfillTerrain`'s start-up query widens from segments with no terrain gain to segments with no terrain gain or no maximum grade. It still queues them in one statement, only inside the tiles' coverage, leaving queued jobs in place.
- **Client:**
  - The `SegmentProfile` contract gains the maximum grade, plus high and low.
  - The presenter maps them, and `SegmentUi` shows the new line.
  - New strings in `:ui-resources`, updated samples in `:app-fixtures`, and updated screenshot references.
- **Depends on:** the `segment-elevation-profile` change, now implemented and archived, and the tile-limited start-up terrain backfill on `main`. The profile change's `elevationProfile` and terrain profile requirements are what this change builds on.
- **Data and privacy:** the maximum grade is pure terrain from the public line, so privacy is unchanged.
