# Tasks

## 1. Start wording

- [x] 1.1 In `composeApp/src/commonMain/composeResources/values/strings.xml`, change `page_start_value` to "%1$s at %2$s" and add `start_today` ("Today") and `start_yesterday` ("Yesterday"); verify `:composeApp:generateComposeResClass` (or the module compile) succeeds
- [x] 1.2 Extend `startTime()` in `composeApp/.../detail/StartTimeText.kt`: read the start at the activity's offset, or `LocalTimeZone` when it has none (UTC when date-only with no offset); compare its date with `LocalClock.current.now()` in the same zone for Today/Yesterday, else the written date; date-only shows just the day. Verify by the tests in 1.3
- [x] 1.3 Add a composeApp jvmTest (next to `SeamsUiTest`'s start-time case, through `AppPreview` with a set clock, zone and hour clock) covering every scenario in `specs/feed/activity-start-time/spec.md`: today, yesterday, older date, 24-hour clock, viewer in another zone, the midnight boundary at −7:00 (06:59 vs 07:00 UTC), no offset in UTC−6, date-only today and older; update the existing 12/24-hour case for " at ". Verify `./gradlew :composeApp:jvmTest --tests "*StartTime*"` passes
- [x] 1.4 Check the other `startTime()` callers (`CombinePickerUi`, `CombineSuggestionUi`, `SavedSuggestionsUi`) still read sensibly with the new wording and update any UI test that asserts their text; verify `./gradlew :composeApp:jvmTest` passes

## 2. Feed card

- [x] 2.1 Add a helper in `composeApp` that builds `FigureValue.StartTime` from an `ActivityRecord`, with `dateOnly` from `DetailCategory.localMidnight` as `ActivityPage.kt` does; verify by the card test in 2.3
- [x] 2.2 Show the start in `ui/ActivityCard.kt` below the author's name (`bodySmall`, `onSurfaceVariant`) from `effectiveRecord()`, so a viewer served the exact start sees it; verify the feed sample renders "Yesterday at 1:00 AM" (`Fixed.STARTED_AT` at MDT, `Fixed.NOW`)
- [x] 2.3 Add a composeApp jvmTest for the card: the default sample shows "Yesterday at 1:00 AM"; an activity whose `startedAt` is its local midnight shows the day only; verify it passes
- [x] 2.4 Confirm the feed order is untouched (no presenter, repository or AppView change in the diff) and `FeedPresenterTest` still passes

## 3. Screenshots and checks

- [x] 3.1 Run `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, review the changed Feed, Profile and ActivityDetail references (start line placement, " at " wording), and keep them with the change
- [x] 3.2 Run the desktop app (`:desktopApp:hotRun --auto`) and check a feed card and an activity page with compose-hot-reload `get_semantic_tree`: the card's start line and the page's Start time read per the spec
- [x] 3.3 Run `./gradlew check` and verify it passes

## 4. Start in the page header

- [x] 4.1 Add `start: Figure?` to `ActivityPageContent`, fill it in `ActivityPage.build` with the Start time mark, and drop the start from the Timing group; verify `ActivityPageTest`'s narrower-start test asserts `page.start` carries the mark and no stats row is a start time
- [x] 4.2 Show `page.start` under the author in `ActivityDetailUi`'s header and above the hero in `ImportPreviewUi`, through a shared `StartLine` (start text plus `VisibilityBadge` for the mark); give `ActivityDetailSamples.page` a start; verify a UI test finds "Yesterday at 1:00 AM" on the page and no "Start time" row
- [x] 4.3 Run `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, review the ActivityDetail and import references, and run `./gradlew check`
- [x] 4.4 Check the activity page in the desktop app: the start under the author, none in All stats
