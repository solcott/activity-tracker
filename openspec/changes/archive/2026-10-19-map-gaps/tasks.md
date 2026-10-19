# Tasks

## 1. Breaking a single route

- [x] 1.1 Add `RouteBreaks.segments` (time over the gap limit and over 200 m apart; at most 10 breaks, the furthest apart) to `core/data/activity`, with tests for a dead GPS, a café stop, a short dropout, sparse 60 s logging and 14 gaps; verify `./gradlew :core-data:jvmTest --tests "*.RouteBreaksTest"` passes
- [x] 1.2 Make `RouteTrim` return a broken polyline for single activities (trimmed whole, then broken), `ActivityDraft.toRecord` take a `SegmentedPolyline`, and `ActivityRepository` and `RouteTrimmer` write `polylineGaps` for single activities; verify `./gradlew :core-data:jvmTest` passes, with a test that a saved draft with a far-apart gap writes one break

## 2. Owner's map and import preview

- [x] 2.1 Break `OwnRoute`'s line and `ActivityPage.fromDraft`'s record for single activities, add `polylineGaps` to `ImportPreview` and pass it to the preview's `ActivityMap`; verify `./gradlew :app-presenters:jvmTest` passes
- [x] 2.2 Widen `feed.activity#polylineGaps`'s description (JSON and `ActivityRecord`) from combined activities to any break; verify `./gradlew :core-lexicon:jvmTest` passes
- [x] 2.3 Render a route with a far-apart gap through `RoutePreview` on desktop and verify it draws two lines; record the finding in this task
  - Finding: a throwaway jvmTest (not kept) drew `RoutePreview` headlessly on desktop Skia from `ActivityDraft.polylineOf` of a 1 s loop with a third missing (6 minutes, about 3 km) and a 20-minute stop in one place. Unbroken (`gaps = null`) it draws the old straight chord across the missing third; with the new breaks (`gaps = [32]`) it draws an open arc with nothing across, and the stop stays joined.

## 3. Checks

- [x] 3.1 Run `./gradlew check composeStabilityCheck` and verify it passes
