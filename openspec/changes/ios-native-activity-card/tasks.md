# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on a simulator made for this change (iOS 26.0) (design decision 1): `ImportBatchSamples`' items cast to `ActivityView` with its fields typed, `ImportBatchEvent.Activity(event:)` built, `Revealed`'s `contains` and sink, a nested list of contract classes, an offset time through a scratch `TextSource`. Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (activity card)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `AppText.Time`'s offset and `TextSource.time`'s, the Compose resolver formatting in it, and `StartText` with `startTimeValue` in `:app-common-api`; `startTime` resolves it (decision 4). Verify a `commonTest` over the start-time spec's cases, `StartTimeUiTest`, and `:app-common-ui:jvmTest`.
- [x] 2.2 `ModerationTexts`, `CardStatsText`, `VisibilityTexts.badge`/`badgeDescription`, `narrowerCategories` moved, `RouteShape`, and `ActivityCardContent` (decisions 3 and 5); the Compose card, `ModerationNotice`, `blurLabel`, `warnLabel`, `CategoryLock`, `diveCardStats`, `togetherWith` ask them. Verify `commonTest`s over every branch, `SharedTextNamesTest`, and `:app-common-ui:jvmTest`.
- [x] 2.3 `ImportBatchTexts.months` in `:feature-feed-api`; `ImportBatchUi` asks it (decision 7). Verify a `commonTest` and `:feature-feed-ui:jvmTest`.
- [x] 2.4 `ActivityCardSamples` and `ImportBatchSamples.variants` in `:app-fixtures` (decision 8). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed.

## 3. Design system

- [x] 3.1 `CatalogText.time` with an offset. Verify an `ActivityCardTextTests` case (and Compose's `AppTextStringUiTest`) formats 16:00 UTC at -420 minutes as 9:00 AM in any zone.
- [x] 3.2 `PagedList`'s `sections:` (decision 7). Verify `PagedListTests` (the end of the last section asks for more, a long sectioned list doesn't), the batch page's snapshots, and that the existing paged pages' snapshots are unchanged.

## 4. The native card and page

- [x] 4.1 Type aliases (one block) and the samples in `Samples.swift`. Verify `ActivityCardTextTests` reads every card sample.
- [x] 4.2 `RouteSketch` (decision 5). Verify `ActivityCardTextTests` reads a shape with a break inside its tile, and the `broken` snapshot shows it.
- [x] 4.3 `MediaStrip` and `ActivityCardView` (decision 6). Verify `ActivityCardTextTests` resolve each sample's author, start, title, stats, notices, locks and counts against the shared card's words.
- [x] 4.4 `ImportBatchView`, registered. Verify `ScreenRegistryTests` names `ImportBatchScreen` and `ActivityCardTextTests` reads the months.
- [x] 4.5 Previews and `ActivityCardSnapshotTests` (decision 8). Verify they record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at, light and dark.

## 5. The whole change together

- [ ] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [ ] 5.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 5.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:app-common-api` or `:feature-feed-api`.

## 6. Walk the page (only when no other session is walking)

- [ ] 6.1 On a signed-in iPhone 17 Pro (iOS 26.0), as alice on the local stack: two GPX files imported together as Only me, the import card on the feed opened, the native batch page read against the shared card (author, start, title, stats, route, counts), a card opening its activity and back, the author line not opening (the athlete's own batch). Verify by noting what was seen, and delete the walk's activities after.
- [ ] 6.2 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [ ] 7.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (activity card)", the open items (the card, the feed's blockers, the shared fit follow-up, the start time). Verify `openspec validate ios-native-activity-card --strict` passes.
- [ ] 7.2 `.agents/rules/compose-screens.md` (the card, its content, `RouteShape`, `StartText`, `PagedList`'s sections) and `adaptive-ui.md`'s native list. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 7.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 6.

## Workflow follow-up

- Commit in small commits by concern on this session's branch; never push.
- Archive with `/opsx:archive`, syncing the specs, once the tasks are done.

## Probe results (1.1)

On `Activity Card iPhone 17` (iOS 26.0), a throwaway Swift test over the shared samples: `ImportBatchSamples`' erased items (2 of 2)
cast to `ActivityView`, which already has a Swift type because `ActivityListEvent`'s cases name it; `likeCount` and `commentCount` read
as `Int64`, `viewer?.like`, `author.avatar`, `labels` and `media` as nil on these samples, `visibility` as `"public"`, `record.utcOffset`
as `-360`; `FeedSamples.activities.elements(as: ActivityView.self)` read the first activity's `group` (1 partner, Carol);
`ImportBatchEvent.Activity(event: ActivityListEvent.ToggleLike(activity:))` built and printed its activity; `Revealed.contains(key:)` and
`eventSink` were callable. The nested list (a route's pieces) and an offset time weren't probed with throwaway code: both are this
change's own Kotlin, checked by `ActivityCardTextTests` and `AppStringsTests` instead (3.1, 4.2).
