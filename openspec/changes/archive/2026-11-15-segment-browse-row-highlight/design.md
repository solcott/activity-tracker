# Design

## Context

Segment browse (`SegmentBrowseScreen`, see proposal.md - Why) keeps one selection: `SegmentBrowseState.selected`, set by `SegmentBrowseEvent.Select` from a tap on the map. The shared rule for how lines are drawn is `SegmentsMap.of(lines, selected)` (`:app-common-api`): roles `Lift < Run < Selected`, sorted so the selected run is on top. The Compose map (`SegmentsMap.nonWeb.kt`, MapLibre Compose 0.20) draws lifts, the runs' casing, the runs, and a separate `segments-selected` layer, each under a `key(json)` because MapLibre Compose can't replace a source's data. Its taps go through `MapInteractions` `click` with a box query on `RUNS_LAYER` (`TAP_SLOP`). The iOS map (`SegmentsMapView`) draws the same `SegmentsMap` and selects through `SegmentsMap.nearest`. Camera moves are `MapCameraRequest(id, lat, lng, zoom)`, taken once per id by both maps. Web has no map (`interactiveMapSupported = false`).

Two facts make moving the map safe: a name search reads the viewport once, when its first page loads (`browse.nameViewport`), so a map move doesn't restart it; and in area mode every run in the list crosses the viewport, so the map never needs to move there.

## Goals / Non-Goals

**Goals:**
- One highlight rule both UIs ask: a hovered run is drawn as a selected one.
- The touch rule (select, then open on a second tap) lives in the presenter, so Compose and iOS share it.
- Framing a box uses the existing `MapFrame.fit`, not a map library's own fit.

**Non-Goals:**
- iPad pointer hover (`.onHover`): the shared rule is ready for it, but no Swift hover in this change.
- A distinct hover style, or a hover state retained across leaving the screen.
- Moving the map when a line is tapped on the map, or when area browsing selects.

## Decisions

**Hover is UI state; selection stays in the presenter.** The hovered run is a `remember { mutableStateOf<String?>(null) }` in `SegmentBrowseUi`, set by row hover (`Modifier.hoverable` interaction source, or `onPointerEvent` Enter/Exit) and by the map's `onHover`. It's like a scroll position: nothing to retain, nothing to test in the presenter, and no event on every pointer crossing. Alternative: a `Hover(uri)` event and a `hovered` field in `SegmentBrowseState`, so iOS could read it; rejected for now since iOS has no hover in scope, and the native view can hold its own `@State` later and pass it to the shared rule.

**`SegmentsMap.of(lines, selected, hovered)`.** A run whose URI is `selected` or `hovered` takes `MapSegmentRole.Selected`; a lift stays `Lift` whatever is hovered. `hovered` joins `key`. `hovered` defaults to null so the existing `SegmentBrowseState.segmentsMap` and iOS call keep working. Alternative: a new `Hovered` role; rejected, since hover looks the same as selection and a fourth role would need a style in every map.

**The shared `SegmentsMap` composable takes `hovered` and `onHover`.** `selectedJson` becomes the runs whose URI is selected or hovered; the layer stays one small keyed layer, so a hover rebuilds a source of one or two lines. The `wasmJs` actual and `SegmentsPreview` take the parameters (the preview draws the hovered run highlighted). A `segmentAt(mapState, offset, slop)` helper holds the box query, used by both the click callback and hover.

**Map hover through Compose pointer events.** MapLibre Compose routes pointer events through Compose (`interaction/internal/PointerRouter.kt`) and draws the desktop map with skiko inside the Compose tree, so a `Modifier.pointerInput` on the map can watch `PointerEventType.Move` and `Exit` on the Initial pass without consuming them. Moves are coalesced: a `Channel(CONFLATED)` or `snapshotFlow` of the last pointer offset, `collectLatest` running one box query at a time with a smaller `HOVER_SLOP`. Exit clears the hover. Only `PointerType.Mouse` moves count, so a finger dragging the map on Android doesn't hover. Alternative: a hover callback in `MapInteractions`; 0.20 has none. Checked in 0.20's sources: every gesture is one Compose `pointerInput` (`interaction/internal/InputModifier.kt`, Main and Final passes, on all platforms), and the desktop map is a Skia texture drawn in the tree (`desktop/bridge/SkiaTexturePresenter.kt`), so an outer modifier's Initial pass sees each move first; the running desktop app confirms it (task 7.1).

**`SegmentBrowseEvent.TapRow(uri)` decides in the presenter.** `BrowseControls` handles it: when `uri == selected`, the presenter opens `SegmentScreen` (like `Open`); otherwise it sets `selected = uri` and, if the row's line has no point inside `viewport`, sets a framing camera request. The UI sends `TapRow` from a row on Android (`LocalUiPlatform` is Android, a real behaviour difference) and `Open` on desktop; iOS sends `TapRow` always. Alternative: the UI decides open-or-select from `state.isSelected`; rejected so the rule isn't written twice (Kotlin and Swift).

**Off-map test in the presenter.** The row's `SegmentView` gives its polyline (`mapSegmentLine()`); decode once and check whether any point lies within `viewport`'s box (1e-7 degrees). A line that crosses the box between two points outside it counts as off-map; the move then simply frames a line that was partly visible, which is harmless and rare at browse zooms. The decoded bounds are the box to frame.

**`MapCameraRequest` can carry a box.** Add an optional `bounds: MapBounds?` (the type `MapFrame.fit` takes). When set, each map converts it with `MapFrame.fit(bounds, width, height)` at its own size in its own units: the Compose map from its measured size in dp, `SegmentsMapView` from its geometry in points, and the existing `MapFrame` to MapKit conversion. `lat`, `lng` and `zoom` stay for the location requests. Alternative: the presenter computes a zoom from the viewport's span; rejected because the presenter doesn't know the map's size or the margin, and `MapFrame.fit` is already the shared rule for route maps.

**Strings.** Row click labels for accessibility: "Show on map" for a row that isn't selected on touch, "Open" for the selected one, as `SegmentBrowseTexts` `AppText`s with strings in `:ui-resources` (and the iOS catalog through `generateIosResources`), added to the feature's text-names test.

## Risks / Trade-offs

- [Pointer moves may not reach a modifier over the map on some platform] → Check first on desktop with hot reload, before the rest; if they don't arrive, ship row-to-map hover only and record map-to-row hover as a follow-up.
- [A hover rebuilds a MapLibre source on every row crossing] → The source holds only the selected and hovered lines; if it flickers, keep two keyed layers (selected, hovered) so only the hover one rebuilds.
- [Touch users lose one-tap opening] → The second tap opens; the selected row's click label says "Open", and screen readers hear "Show on map" first.
- [Framing a long segment zooms out past the 1 degree search limit] → Only name searches move the map, and their list ignores `tooWide`; area browsing resumes once the athlete zooms in, as today.
- [Swift export of a nullable `MapBounds` on `MapCameraRequest`] → It's a plain contract class in `:app-common-api`; the stub check in the iOS build would name it if it weren't carried.
