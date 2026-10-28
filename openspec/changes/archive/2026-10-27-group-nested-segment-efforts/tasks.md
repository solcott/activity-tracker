# Tasks

## 1. Grouping model and function (`:app-screens`)

- [x] 1.1 Add `SegmentEffortGroup` and `SegmentEffortRow` (`app/screens/.../app/detail/SegmentEffortGroups.kt`) and `groupEfforts(efforts: List<EffortView>)` per design.md; verify `./gradlew :app-screens:composeStabilityCheck` (or root `composeStabilityCheck`) passes
- [x] 1.2 Add jvmTest unit tests for `groupEfforts`: whole trail + two stretches nest (spec "Stretches nested under their trail"), rounded end still nests, child starting 1 s before its parent is listed after it (spec "Stretch timed a second before its trail"), end-to-end stretches are separate groups, efforts overlapping by a few seconds at a junction are separate groups (spec "Segments meeting at a junction"), efforts sharing a stretch group (spec "Sharing a stretch of trail"), equal spans are siblings, overlapping siblings at one depth, a shorter effort starting together nests (spec "Shorter effort starting together"), two laps, span fractions (0.59–1.0 case), guides/lastChild over 3 levels, label stripping for ": ", " - ", via-parent base and a different trail; verify they pass with `./gradlew :app-presenters:jvmTest --tests "*.GroupEffortsTest"` (`:app-screens` has no test source set; the presenters' jvmTest sees it)

## 2. State and presenter

- [x] 2.1 Change `ActivityDetailPage.segments` to `ImmutableList<SegmentEffortGroup>` and update its doc; have `rememberSegments` (`app/presenters/.../app/detail/ActivitySegments.kt`) return `groupEfforts(...)`; update `ActivityDetailSegmentsTest` and other presenter tests reading `segments`; verify `./gradlew :app-presenters:jvmTest` passes

## 3. UI and fixtures

- [x] 3.1 Update `ActivityDetailSamples.kt` to grouped state and add a sample mirroring the Corner Canyon list (Rattler whole + 2 stretches, Rush parent + section + overlapping sibling, a lone effort) built with `groupEfforts` and `fakeEffort`/`fakeSegmentBasic` (give `fakeEffort` a `startOffset` parameter); verify `:app-fixtures` compiles
- [x] 3.2 Rewrite `ActivitySegmentsUi.kt` to draw groups with dividers, depth indent with guide lines, `EffortRow` with the row label, and span bars per design.md; verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and the detail UI tests pass
- [x] 3.3 Regenerate screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, review the new/changed images, and verify `:androidApp:testDebugScreenshotTestDefaultTestSuite` passes

## 4. Integration

- [x] 4.1 Run `./gradlew check` and verify it passes
- [x] 4.2 Run the desktop app (`:desktopApp:hotRun --auto`), open an activity with overlapping efforts or the new sample, and verify by screenshot in light and dark and at narrow width that groups, nesting, guides and bars render and tapping a nested row opens its segment

## Workflow follow-up

- Archive the change after review.
