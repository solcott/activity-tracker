# Tasks

## 1. Marker points

- [x] 1.1 Add `RouteMarkers.of(pieces)` to `composeApp/ui` (start, end and both ends of each break), with tests for one piece, two pieces, a one-point piece and no points; verify `./gradlew :composeApp:jvmTest --tests "*.RouteMarkersTest"` passes

## 2. Drawing them

- [x] 2.1 Draw the markers in `RoutePreview`'s canvas (break rings, then start, then end)
- [x] 2.2 Add a source and `CircleLayer` per marker kind to `ActivityMap`'s MapLibre map, above the route
- [x] 2.3 Render a route with a break through `RoutePreview` on desktop and verify the start, end and break markers appear and differ; record the finding in this task
  - Finding: a throwaway jvmTest (not kept) drew `RoutePreview` headlessly on desktop Skia for a 300° arc broken once. The viewer's map shows a green start and red end at the line's ends and a white ring in the route's colour at each end of the break; the owner's map, given `RouteEnds` 25° in from each end, draws the whole arc with the start and end markers moved there.

## 3. Privacy

- [x] 3.1 Add `PrivacySettings.shownEnds` (tests: no zones, zones at both ends, hidden ends, all hidden, the same ends as the published polyline) and `ActivityRepository.privacySettings()`; give `OwnRoute` and the detail state `ownRouteEnds` and pass them to `ActivityMap`; verify `./gradlew :core-data:jvmTest :app-presenters:jvmTest` passes

## 4. Checks

- [x] 4.1 Run `./gradlew check composeStabilityCheck` and verify it passes
