# Tasks

## 1. The rating rule

- [x] 1.1 Add `TrailScales` (which scales fit a sport group) and `TrailRating` with `of(trail, sport)` and `label` to `:app-common-api`, mapping IMBA, MTB scale, SAC and piste as `segments/trail-ratings` says; verify with a `TrailRatingTest` covering every scenario of that spec and every scale value
- [x] 1.2 Make `TrailTexts`' `ScaleFilter` ask `TrailScales`, and verify `TrailTextsTest` and `SegmentPageTextsTest` still pass unchanged
- [x] 1.3 Add the `trail_rating_*` and legend strings to `strings.xml`, run `generateIosResources`, and verify `SharedTextNamesTest` covers the new names

## 2. Map model and presenters

- [x] 2.1 Add `rating` to `MapSegmentLine` and `MapSegment`, key `SegmentsMap` on it, and add `SegmentsMap.legend` (`RatingLegendEntry`); verify in `SegmentsMapTest` that the legend lists only drawn levels in order, Unrated last, and is empty with no rated run
- [x] 2.2 Fill the rating in `SegmentBrowsePresenter` (paths from their trail and the chosen sport, groups from their sport group) and `PathPresenter` (segments rated, path pieces and lifts unrated); verify in the presenters' tests that a sport chip change re-rates the lines
- [x] 2.3 Add `BrowseRow.rating(sport)` and the Trail page row's rating getters in `:feature-segment-api`; verify with a test that the row and its map line agree
- [x] 2.4 Give `SegmentBrowseSamples` and `PathSamples` rated trails of every level plus unrated and lift lines; verify `ContentLoadingConformanceTest` still passes

## 3. Compose maps, legend and rows

- [x] 3.1 Add `TrailRatingColors` to `:ui-designsystem` (line, casing, unrated, light and dark); verify with a unit test that every line colour reaches 3:1 against its casing
- [x] 3.2 Draw runs in `SegmentsMap.nonWeb.kt` from one source with a `level` property, casing and line by `match`, and the Extreme centre line; draw the same in the canvas stand-in; verify on the desktop app (`hotRun`) over a rated area, light and dark
- [x] 3.3 Add `RatingLegend` and `RatingSwatch` to `:app-common-ui`, place the legend on the browse and Trail page maps and the swatch on their rows (web included), with the level as the swatch's content description; verify with UI tests in `feature/segment/ui` that the legend and swatch show and read their level
- [x] 3.4 Run `:androidApp:updateDebugScreenshotTestDefaultTestSuite`, review the browse, Trail page and component references, and verify the screenshot suite passes

## 4. iOS

- [x] 4.1 Add the catalog colours `TrailRating0Color`…`TrailRating4Color`, the casings and `TrailUnratedColor` with light and dark appearances, and verify with a test (`TrailRatingCatalogTest`, `:ui-designsystem` jvmTest, reading the catalog) that their values match `TrailRatingColors`
- [x] 4.2 Stroke by level in `SegmentsMapView` (map and sketch), add `RatingLegendView` and `RatingSwatch` to `Design/`, and use them on `SegmentBrowseView` and `PathPage`; add them to `DesignGallery`
- [x] 4.3 Record and review snapshots on the iOS 26.0 simulator for the design gallery, browse and the Trail page, and verify the suites pass on a second run

## 5. Integration

- [x] 5.1 Run `./gradlew check` and the iOS build, and verify both pass
