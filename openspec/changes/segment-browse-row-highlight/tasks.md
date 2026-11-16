# Tasks

## 1. Spike: pointer moves over the map

- [x] 1.1 In the desktop app (`./gradlew :desktopApp:hotRun --auto`), put a temporary `Modifier.pointerInput` watching `PointerEventType.Move`/`Exit` (Initial pass, not consumed) on the segment browse map and confirm moves arrive while panning still works; record the result in design.md (if they don't arrive, drop map-to-row hover from this change and note it as a follow-up), then remove the probe

## 2. Shared highlight rule and framing camera (`:app-common-api`)

- [x] 2.1 `SegmentsMap.of(lines, selected, hovered = null)`: a run that is selected or hovered takes `MapSegmentRole.Selected`, a lift stays `Lift`, `hovered` joins `key`; cover it in `SegmentsMapTest` (hovered run on top, hovered lift unchanged, key differs) and pass `./gradlew :app-common-api:jvmTest --tests "*.SegmentsMapTest"`
- [x] 2.2 Add optional `bounds: MapBounds?` to `MapCameraRequest` (a `fit` factory) and a function giving the `MapFrame` a request asks for at a map size (`MapFrame.fit` when bounds are set, else lat/lng/zoom); cover both in a common test and pass `:app-common-api:jvmTest`

## 3. Presenter: tap a row to select, then open (`feature/segment`)

- [x] 3.1 Add `SegmentBrowseEvent.TapRow(uri)` to `SegmentBrowseScreen.kt` with its doc, and a `SegmentBrowseState.segmentsMap(hovered)` alongside the existing getter
- [x] 3.2 Handle `TapRow` in `SegmentBrowsePresenter`: the selected row opens `SegmentScreen`; another row is selected, and when none of its line's points lie in the viewport a new framing `camera` request is set; add cases to `SegmentBrowsePresenterTest` (first tap selects, second opens, an off-viewport name result issues one framing request, an area row issues none, the name results don't reload) and pass `./gradlew :feature-segment-presenter:jvmTest`
- [x] 3.3 Run `SegmentBrowseThroughHostTest` and `SearchTabSegmentsTest` (`./gradlew :composeApp:jvmTest --tests "*SegmentBrowseThroughHostTest" --tests "*SearchTabSegmentsTest"`) and update any that assumed a row tap opens on Android

## 4. Compose map: hover and framing (`:app-common-ui`)

- [x] 4.1 Give the `SegmentsMap` composable `hovered` and `onHover`; the nonWeb map highlights selected and hovered runs in its small keyed layer, the `wasmJs` actual takes and ignores them, `SegmentsPreview` draws the hovered run highlighted; extract the box query into one helper used by click and hover
- [x] 4.2 Report hover from mouse pointer moves (coalesced, one query at a time, a smaller slop than a tap, cleared on exit; touch drags ignored), unless 1.1 ruled it out
- [x] 4.3 Take a framing camera request by fitting its bounds to the map's measured size; verify `./gradlew :app-common-ui:jvmTest` and that the desktop app still compiles (`:desktopApp:compileKotlinJvm`)

## 5. Browse UI: hover and row taps (`feature/segment/ui`)

- [x] 5.1 Hold the hovered run in `SegmentBrowseUi`, set it from row hover and the map's `onHover`, pass it to the map, and mark a row that is selected or hovered; hover never scrolls the list
- [x] 5.2 Row click by platform: `TapRow` on Android, `Open` on desktop; click labels "Show on map" and "Open" as `SegmentBrowseTexts` `AppText`s with strings in `:ui-resources`, run `./gradlew generateIosResources`, and add the names to the feature's text-names test
- [x] 5.3 Extend `SegmentBrowseUiTest`: a selected row and a hovered row are marked, the Android look sends `TapRow`, the desktop look sends `Open`; pass `./gradlew :feature-segment-ui:jvmTest`
- [x] 5.4 Rerun the screenshot suite (`./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite`); if browse references changed, update them with `updateDebugScreenshotTestDefaultTestSuite` after looking at them

## 6. iOS: row taps and framing

- [x] 6.1 `SegmentBrowseView` sends `TapRow` from a row (with the click labels as accessibility hints), and `SegmentsMapView` takes a framing request by fitting its bounds at the map's size; build with `xcodebuild ... build` (the stub check passes)
- [x] 6.2 Add Swift tests for the frame a bounds request gives (`InteractiveMapsTextTests`), rerun `InteractiveMapsSnapshotTests` on an iOS 26.0 simulator and update references only if they changed

## 7. Integration

- [x] 7.1 Desktop by hand with hot reload and the compose-hot-reload MCP: hovering a row highlights its line, hovering a line marks its row without scrolling, clicking a row opens it, a lift marks nothing
- [x] 7.2 Android (`./gradlew :androidApp:installDebug`): a row tap highlights, a second opens, an Everywhere result far away moves the map and the list keeps its order; iOS simulator: the same
- [x] 7.3 `./gradlew check` passes

## 8. Found while testing

- [x] 8.1 The live iOS map kept a line's old style when its role changed (no amber highlight on tap or row select, a bug that predates this change): `SegmentsMapView` keys each line by URI and role (`mapIdentity`) so a selected run is drawn anew on top; verified on the iPhone 17 Pro simulator

## Workflow follow-up

- Archive the change once reviewed (`/opsx:archive`), syncing the delta into `segments/segment-browse`.
