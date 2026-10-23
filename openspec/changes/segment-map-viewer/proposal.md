# Proposal

## Why

The segment screen shows the segment's line on a static MapLibre map inside a scrolling list. An athlete can't zoom in to see where a segment starts and finishes, which turns it takes, or the trails around it. The activity page already solves this with a full-screen map (`feed/route-map-viewer`), and segments should behave the same way.

## What Changes

- The segment screen's map stays static where it is. Tapping it, or the expand button in its corner, opens the segment's line full screen.
- A new full-screen segment map can be panned and zoomed, kept north-up. It has a recenter button that frames the whole line again, and a top bar with the segment's name and back.
- The full-screen map draws the same line and start and finish markers as the segment screen.
- The segment's data credit (OpenStreetMap with its link, the agency, or the licence, as `segments/segment-records` requires wherever a segment's line is drawn) is overlaid on the full-screen map. An athlete's segment shows no data credit there, as on the segment screen.
- If the segment can't be loaded, the full-screen map shows a load error in its place, and back still works.
- Web is unchanged. Its segment map is still the static canvas preview, with no expand button and no tap target, until MapLibre Compose has a wasmJs target.

## Capabilities

### New Capabilities

- `segments/segment-map-viewer`: the enlarged, pannable and zoomable view of a segment's line that opens from its segment screen, with the segment's data credit on it.

### Modified Capabilities

None. `segments/segment-records` already requires the credit wherever a segment's line is drawn; the new capability says where it sits on the enlarged map.

## Impact

- `app/screens`: a new `SegmentMapScreen(uri)` with its state and events, and an `OpenMap` event on `SegmentEvent`.
- `app/presenters`: a `SegmentMapPresenter` that loads the segment again through `SegmentRepository.segmentOutcome`. The line conversion (`mapLine`) and credit (`creditOf`) helpers move out of `SegmentPresenter` so both presenters share them. `SegmentPresenter` navigates on `OpenMap`.
- `composeApp`:
  - `SegmentUi` passes `onExpand` to `ActivityMap`.
  - The segment `Credit` composable moves out of `SegmentUi` so both screens use it.
  - A new `SegmentMapUi` draws `ActivityMap(interactive = true)` with the credit overlaid.
  - `ActivityMap` takes an optional `overlayTop`, so the segment map frames the line below the credit.
- `TopLevelDestination` hides the tab bar over the segment map, as over the activity's route map.
- No lexicon, AppView or stored-data changes.
