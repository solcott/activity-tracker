# Proposal

## Why

A single activity's map joins every position to the next, so where the GPS stopped (a dead device, a tunnel, a shuttle, the recording paused while the athlete moved) it draws a straight line between the last point and the next, across ground that was never covered. Combined activities already break their map line between members recorded one after another (`feed.activity#polylineGaps`), and the charts now break at gaps in their samples (`chart-gaps`); a single recording should read the same way.

## What Changes

- A single activity's map line breaks where no position was recorded for longer than the gap limit (2 minutes, or four of the track's usual position intervals when that's longer) **and** the positions either side are more than 200 m apart. A stop where the athlete stood still (a café, a red light) stays one line.
- The break is written to the activity record's `polylineGaps`, as a combined activity's are, so the feed card, the detail page and every viewer see it. At most 10 breaks are kept (the lexicon's limit), the 10 with the positions furthest apart.
- Privacy trimming is unchanged for a single activity: the whole route is trimmed as one, then broken.
- The owner's detail map, drawn from their full track, breaks the same way, also on activities saved before this change.
- The import preview's map shows the breaks.
- Activities already saved keep their stored polyline: feed cards and other viewers see breaks only once an activity is saved, imported or retrimmed after this change.

## Capabilities

### New Capabilities

- `feed/map-breaks`: where an activity's map line breaks.

### Modified Capabilities

None.

## Impact

- `core/data`: a single activity's route segmentation beside `CombinedRoute`; `RouteTrim` returns a broken polyline for single activities; `ActivityDraft.toRecord`, `ActivityRepository` and `RouteTrimmer` write `polylineGaps`.
- `app/screens`/`app/presenters`: `ImportPreview` carries the breaks; `OwnRoute` and `ActivityPage.fromDraft` break the owner's line.
- `composeApp`: the import preview passes the breaks to `ActivityMap`.
- Lexicon: `feed.activity#polylineGaps`'s description widens from combined activities to any break; no schema change. No AppView change.
