# Tasks

## 1. Shared texts and contracts (`feature/segment/api`)

- [x] 1.1 Add `TrailDetailGroup` and `TrailTexts.groups(trail, sport)` (Difficulty, Surface & width, Access & season; empty groups dropped; `rows` unchanged), with the group titles in `strings.xml`. Verify with new `TrailTextsTest` cases for the grouping, the two Trail details scenarios, and an all-groups trail. Add the new names to `SegmentBoardTextNamesTest` or the feature's text-names test.
- [x] 1.2 Add `TrailTexts.summary(trail)`: up to three of surface, width, dogs and season, joined with " · ", or null. Verify with `TrailTextsTest` cases for the folded-summary and no-summary-values scenarios.
- [x] 1.3 Add `SegmentFigure` and `SegmentPageTexts.figures(segment, profile, units)`: length, gain with loss, average grade and maximum grade, each left out when unknown. Add `SegmentPageTexts.heading(segment)`, and narrow `extremes` to the high and low points. Verify with a `SegmentPageTexts` test for the metric and imperial figure scenarios, a segment without elevation, and a lift.
- [x] 1.4 Add `LeaderboardSummary` and `SegmentTrail` beside `MayorSummary`. Add to `SegmentState`: `leaderboard`, `trails`, and list getters that return copies (`figures(units)`, `trailGroups()`). Add `SegmentEvent.OpenTrail(uri)`. Verify that `./gradlew :feature-segment-api:check` passes, strict stability included.
- [x] 1.5 Add `PathState.detailsExpanded`, `summary()` and `trailGroups()`, and `PathEvent.ToggleDetails`. Verify that `:feature-segment-api:check` passes.

## 2. Data and presenters

- [x] 2.1 Give `SegmentRepository.leaderboardPage` a `limit: Int? = null` passed to `GetSegmentLeaderboard.Params.limit`. Verify with a `core/data` jvm test asserting the request carries `limit=1`.
- [x] 2.2 In `SegmentPresenter`, read the leaderboard's first entry and the viewer's entry for active, ranked segments (as the Mayor is read) into `leaderboard`, and null otherwise. Verify with `SegmentPresenterTest` cases: leader and viewer, an empty board, a failed read keeping the card offered, a lift with no read, and the `eBike` board from `screen.board`.
- [x] 2.3 In `SegmentPresenter`, read `pathViews(segment.paths)` into `trails`, skipped when `paths` is empty, and handle `OpenTrail` by pushing `PathScreen(uri, sport)`. Verify with `SegmentPresenterTest` cases: one trail named, no paths means no read, a failed read leaves an empty list, and `OpenTrail` navigates.
- [x] 2.4 In `PathPresenter`, keep `detailsExpanded` retained, starting false, and toggle it on `ToggleDetails`. Verify with `PathPresenterTest`: it opens folded and the toggle unfolds then folds.

## 3. Samples

- [x] 3.1 Update `SegmentSamples`. The loaded sample gets a leaderboard summary, a trail link, trail details across all three groups, a profile and a Mayor. Add samples for a failed board read and for no elevation. Verify the existing UI tests still compile and `ContentLoadingConformanceTest` passes.
- [x] 3.2 Update `PathSamples` with trail details in folded and expanded samples. Verify `ContentLoadingConformanceTest` passes.

## 4. Compose UI (`feature/segment/ui`)

- [x] 4.1 Add a `TrailDetailGroups` composable that draws each group as an `AppCard` with its title and rows (the Website row still opens the browser). Replace `TrailDetails` on both pages. Verify with a UI test asserting the three card titles and the Website link.
- [x] 4.2 Restyle `SegmentUi` in the `segments/segment-page` order:
  - The map item, the heading (kind and status line, name, original name, "on" trail links sending `OpenTrail`), and the chips.
  - The figure row card and the profile card with the high and low points in its heading.
  - The boards row (`FlowRow` of the Leaderboard card and `MayorCard`), the details groups, the names, the efforts, and the footer with the creator, credit and status.

  Verify with UI tests for the order, a missing Mayor, a lift with no boards row, a failed board read where the card still opens the leaderboard, and the trail link opening. Update `SegmentElevationUiTest` and `SegmentLiftUiTest` to the new places.
- [x] 4.3 Restyle the `PathUi` header: the summary line, then the folded details row with a chevron control (send `ToggleDetails`; groups shown when expanded), then the existing buttons. Fix `HEADER_ROWS` and selection scrolling if the item count changes. Verify with `PathUiTest` cases for folded, unfolded and no trail details, and that selection still scrolls to its row.
- [x] 4.4 Add or update the screenshot previews in `SegmentScreenshots.kt` and `PathScreenshots.kt` for the new samples. Verify by running `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, looking at the Segment and Trail references, then `:androidApp:testDebugScreenshotTestDefaultTestSuite`.

## 5. Native iOS (`iosApp`)

- [x] 5.1 Update `TrailDetailsSection.swift` to draw `trailGroups()` as one `Section` per group, titled by the group, in the shared words. Verify with a Swift test that reads the groups from a sample and checks they match Kotlin's.
- [x] 5.2 Restyle `SegmentPage.swift` in the shared order:
  - The map row, then the heading rows with trail links sending `OpenTrail`, and the chips.
  - The figure row (`HStack` of cells), and the profile section headed by the high and low points.
  - The boards row with two `LeaderCardView`s, the detail groups, names, efforts, and the footer.

  Verify with `SegmentSnapshotTests` rerecorded on an iOS 26.0 simulator (light and dark), checking that the new images match the canvas order.
- [x] 5.3 Update `PathPage.swift`: the summary row with the Trail details control toggling `ToggleDetails`, and the group sections when expanded. Verify with the Trail page's snapshot tests (folded and expanded) rerecorded on iOS 26.0.
- [x] 5.4 Build the iOS app (`xcodebuild ... build`, which also runs `checkSwiftExportStubs`). Verify it succeeds and no new stubs are listed for `SegmentState`, `PathState` or the new types.

## 6. Integration

- [x] 6.1 Run `./gradlew check`. Verify it passes: tests, lint, detekt, ktfmt, stability, `checkIosScreenRegistry`, `checkSwiftExportSources` and `checkIosResources` after `generateIosResources`.
- [x] 6.2 Run the desktop app (`:desktopApp:hotRun`) against the local stack. Open an imported trail segment and its Trail page, and verify that the order, the cards, the "on" link and the folding work as the specs say.

## Workflow follow-up

- Archive the change with `/opsx:archive` after review, which adds `segments/segment-page` and merges the deltas into the existing specs.
