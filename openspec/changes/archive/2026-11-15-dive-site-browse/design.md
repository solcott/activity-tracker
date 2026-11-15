# Design

## Context

- Segment browse (`segments/segment-browse`, `feature/segment` `SegmentBrowse*`) is the model: a presenter that infers "has a map" from `ViewportChanged`, an area `ContentState` that follows the viewport, paged name results with `rememberRetainedPaging`, a start from the first location fix or the latest activity, and `SegmentsMap` (MapLibre on `nonWebMain`, a canvas stand-in under inspection, nothing on web). The native iOS view draws the same state over its own map.
- `feed.searchDiveSites` takes a box of at most 1 degree and pages by `indexedAt`, 100 at most per page.
- `DiveSitePresenter` already turns a site's centre and radius into a closed polyline outline (`siteOutline`) that the route maps draw.

## Decisions

### 1. Its own mode and screen, not sites on the segment map
Sites and segments have different rows (no sport group or length), different selection (a circle, not a line) and different name searches. A third mode keeps segment browse unchanged. The user chose this.

### 2. `feed.searchDiveSiteNames`, mirroring `feed.searchSegmentNames`
Params `q` (required, max 100), the optional box (all four or none, 1 degree) and centre (both or none), `limit`, `cursor`. Same filters as `search` (`diveSiteServed`, `unhidden`, `listedTo`), plus `lower(current_name) LIKE %q%` through `NameMatch`, a rank `Case` (whole, prefix, word, anywhere), then distance from the centre, then `uri`, with an offset cursor. The box filters the centre directly, so pages are never short. The distance reuses segment search's `BoxDistance`, generalised to take the box columns, with the site's centre as a zero-size box.

### 3. Sites drawn as outlines on `SegmentsMap`
Each site becomes a `MapSegmentLine` whose polyline is its outline (`siteOutline` in `core/location`, which the site page already draws). That keeps one map wrapper on every platform, its tap selection (the outline nearest the tap within a finger's width) and its stand-in for screenshots. A tap inside a large circle when zoomed right in doesn't select it; the list still opens it. A circle layer is a later change if that matters.

### 4. Area browse follows cursors up to a cap
As segment browse does: follow `searchDiveSites` cursors until none or 300 sites, sort by distance from the map's centre to each site's centre, and say when capped.

### 5. Shared start logic moves to `:app-common-presenter`
The map browse controls (scope, viewport, selection, camera request, start, location permission, the first fix within 5 s or the latest own start, and the name search's box) move out of `SegmentBrowsePresenter` into `MapBrowse` (`app/browse`), which both presenters hold in `rememberRetained`. Segment browse keeps its sport group itself. `NameScope`, `BrowseStart` and `BrowseList` stay in `:feature-segment-api`, which `:feature-divesite-api` exposes, so the native views and Swift aliases stay as they are.
