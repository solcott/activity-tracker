# Proposal

## Why

On segment browse you can't tell which line on the map is the row you're reading without opening its page. The map and list already share a selection, but only from the map's side: tapping a row always opens it. This change lets a row show its line without leaving the screen.

## What Changes

- With a pointer (desktop, Android with a mouse), hovering a row highlights its line on the map, drawn as the selected line is. Hovering a run's line on the map marks its row without scrolling the list. Clicking a row still opens its page, and clicking a line still selects it.
- On touch (Android, iOS), the first tap on a row selects it: its line is highlighted and the row marked. Tapping the selected row again opens its page. **BREAKING** (behaviour): one tap on a row no longer opens it on phones and tablets.
- When a row is selected and its line doesn't cross the visible area (a result found Everywhere, or the map panned away), the map moves to show the whole line. This replaces "the map SHALL NOT move by itself" for that case. Browsing an area never moves the map, since every row's line already crosses it.
- Maps of many segments can be asked to frame a box, not only a centre and zoom.
- Web has no map and is unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/segment-browse`: the shared selection gains row selection on touch, pointer hover both ways, and moving the map to a selected row's line; the name search scope's "the map SHALL NOT move by itself" gives way for a selected row; the native iOS list selects on the first tap.

## Impact

- `feature/segment/api` (`SegmentBrowseEvent` gains a row tap), `feature/segment/presenter` (`SegmentBrowsePresenter`: select or open, frame an off-map line), `feature/segment/ui` (`SegmentBrowseUi`: hover state, row marking, row click by platform).
- `app/common/api`: `SegmentsMap.of` takes a hovered run; `MapCameraRequest` can carry a box to frame.
- `app/common/ui`: the `SegmentsMap` composable takes the hovered run and reports hover over lines (MapLibre box query on pointer moves); `SegmentsPreview`.
- iOS: `SegmentBrowseView` sends the row tap, `SegmentsMapView` frames a box. iPad pointer hover is left for later.
- Tests: `SegmentsMapTest`, the browse presenter and UI tests, samples and screenshots if row looks change, iOS snapshots.
- No lexicon, AppView or data changes.
