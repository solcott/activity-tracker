# Proposal

## Why

The activity detail page shows the route on a MapLibre map. The map has no pan or zoom, because it sits in a scrolling page and a sideways media row. So an athlete can't look closer at a turn, a loop or the streets around their route, and the map is never bigger than its tile.

## What Changes

- The detail page's map stays static where it is. Tapping it, or the expand button in its corner, opens the route full screen.
- A new full-screen route map screen can be panned and zoomed, kept north-up. It has a recenter button that fits the whole route again, and a back button to the detail page.
- The full-screen map draws the same line as the detail page: the same breaks, start, end and break markers, and privacy trimming. The owner sees their untrimmed track with ends marked where others' routes start and end. Other viewers see the trimmed polyline.
- Web is unchanged. Its map is still the static canvas preview, with no expand button and no tap target, until MapLibre Compose has a wasmJs target.
- Feed cards and the import preview are unchanged.

## Capabilities

### New Capabilities

- `feed/route-map-viewer`: the enlarged, pannable and zoomable view of an activity's route that opens from its detail page.

### Modified Capabilities

- `feed/route-markers`: "The map marks the start and end" now names the enlarged map among the route maps that carry markers.

## Impact

- `app/screens`: a new `RouteMapScreen(uri)` with its state and events, and an `OpenMap` event on `ActivityDetailEvent`.
- `app/presenters`: a `RouteMapPresenter` that loads the activity and, for the owner, reuses `rememberOwnRoute`. `ActivityDetailPresenter` navigates on `OpenMap`.
- `composeApp`:
  - `ActivityMap` takes an `onExpand` callback and an `interactive` flag. MapLibre stays inside the nonWeb actual, and the wasmJs actual ignores both.
  - `ActivityDetailUi` passes `onExpand`.
  - There is a new `RouteMapUi`, and new strings.
- `BACKLOG.md`: the note on the tap-to-open map becomes done.
- No lexicon, AppView or stored-data changes.
