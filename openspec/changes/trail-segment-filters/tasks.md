# Tasks

## 1. The filter rule

- [x] 1.1 Add `PathSort`, `LengthBand` (imperial and metric bounds), `WayChoice` and `PathFilter` to `:feature-segment-api` with `apply` (which re-sorts rows and places each section by its first row, so `itemsOf` stays as it is), `options` and `pruned` as design.md describes; verify with a `PathFilterTest` in `feature/segment/ui`'s `jvmTest` (where the fixtures are) covering every scenario of `segments/path-filters` for search, the difficulty, direction and length filters, sorting and sections, and that `PathPresenterTest`'s existing section tests still pass under Longest
- [x] 1.2 Fold case and accents for the search, moving `stripDiacritics` from `:app-common-ui` into `:core-data` (`data.text`, with an iOS actual) so the shared filter can use it; verify that "Potato", "POTATO" and "pötato" match in `PathFilterTest`
- [x] 1.3 Add the search, chip, sort, no-matches and clear strings to `strings.xml` and their `PathTexts` getters, run `generateIosResources`, and verify `SharedTextNamesTest` and `checkIosResources` pass

## 2. Presenter and state

- [x] 2.1 Add `query`, `filter`, the options, `noMatches` and the chip getters to `PathState`, and replace `ShowAllSports`/`offersAllSports` with `SportChanged` and the toggle, sort and clear events; verify that `:feature-segment-api` compiles and `checkSwiftExportSources` passes
- [x] 2.2 In `PathPresenter`, inject `UnitsRepository`, hold a retained `PathFilter`, filter the content and `mapLines`, prune on a sport change, and clear a hidden selection; verify in `PathPresenterTest` that a filter narrows rows and lines, that the selection clears when its row is hidden, that choices survive returning from a segment, that a new presenter starts clear with the screen's sport, and that switching sport drops a level no row has
- [x] 2.3 Replace remaining `ShowAllSports` uses (fixtures, links tests, iOS) and add `PathSamples` for filtered, sorted and no-matches states; verify `ContentLoadingConformanceTest` and `:app-links:jvmTest` pass

## 3. Shared UI

- [x] 3.1 In `PathUi.kt`, add the search field, the sport chips, the Difficulty, Direction and Length menu chips (text menu items: `AppMenuItem` has no slot for a swatch), and the Sort chip above the list on phone and wide layouts, showing each only when its options narrow the list; verify with UI tests in `feature/segment/ui` that choosing a level narrows the rows and that chips hide when they wouldn't narrow
- [x] 3.2 Draw the no-matches state with Clear filters, apart from the empty state; verify with a UI test that clearing restores the rows and keeps the sport
- [x] 3.3 Update the screenshot references (`:androidApp:updateDebugScreenshotTestDefaultTestSuite`), review the Trail page ones, and verify the suite passes; check on the desktop app (`hotRun`) that filtering a real trail redraws the map, in light and dark

## 4. iOS

- [x] 4.1 In `PathPage.swift`, add `.searchable` bound to the query, the toolbar filter `Menu` (sport picker, level, way and band toggles, sort picker) with its filled icon while any filter is on, and the no-matches message with Clear filters as a list row (as the empty state is); verify with an `xcodebuild` build that has no stub-check failures
- [x] 4.2 Add iOS samples for the filtered and no-matches states, record snapshots on the iOS 26.0 simulator, review them, and verify the suites pass on a second run

## 5. Integration

- [x] 5.1 Run `./gradlew check` and verify that it passes
