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

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 5.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:app-common-api` or `:feature-feed-api`.

## 6. Walk the page (only when no other session is walking)

- [x] 6.1 On a signed-in iPhone 17 Pro (iOS 26.0), as alice on the local stack: two GPX files imported together as Only me, the import card on the feed opened, the native batch page read against the shared card (author, start, title, stats, route, counts), a card opening its activity and back, the author line not opening (the athlete's own batch). Verify by noting what was seen, and delete the walk's activities after.
- [x] 6.2 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [x] 7.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (activity card)", the open items (the card, the feed's blockers, the shared fit follow-up, the start time). Verify `openspec validate ios-native-activity-card --strict` passes.
- [x] 7.2 `.agents/rules/compose-screens.md` (the card, its content, `RouteShape`, `StartText`, `PagedList`'s sections) and `adaptive-ui.md`'s native list. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 6.

## Walk (iPhone 17 Pro on iOS 26.0, signed in as alice on the local stack; this change's Debug build, after merging the migration branch)

No other session was walking (the linked sessions were idle), and the stack and tunnel answered.

- **6.1, iPhone.** Five GPX tracks of March to July 2025 ("Walk test card N, delete me"), zipped into one file in the simulator's On My
  iPhone storage (the document picker offers no multi-select of loose files there), were imported as one batch, Only me with every detail
  Only me: "5 activities to import", then "Import saved". After a refresh the shared feed showed "Imported 5 activities", "Run 5 ·
  14.33 mi", "Mar 2025 – Jul 2025" and four route previews. Tapping it pushed the native page, titled "Imported by alice": a heading per
  month (Jul 2025 to Mar 2025), and each card with alice's initial, the Only me badge, "July 10, 2025 at 8:00 AM" (the shared activity
  page read the same start), the run icon and title, 2.87 mi, 19:40 and 6:52 /mi (the activity page: 2.87 mi, 19:40, 6:52 /mi), the
  route line with its start and end markers on its grey tile, and "0 likes · 0 comments", with no Like (her own). Scrolling crossed the
  month sections. A card's tap opened the shared activity page and back returned to the page as it was. Each of the five was deleted
  from its activity page (⋯ → Delete → Delete); the feed then read as before the walk, without the import card. The zip and folder were
  removed from the simulator afterwards.
- **6.2, iOS 18.4** (`Activity Card iPhone 16 Pro`, made for this change). The build launched to the native sign-in.

## Not verified

- **Like on a device**: the walk's batch was alice's own, so the card offered no Like; liking another athlete's batch needs a second
  account with a batch import that alice may see. Liking rests on the `liked` snapshot, the text tests and the shared presenter.
- **The author line's tap**: on a batch page it opens nothing (the whole page is the athlete's), so the borderless author button was only
  seen in snapshots of other cards, never tapped on a device.
- **Photos, blur and reveal, a takedown, a warning, a group, a dive and locks on a device**: the local account's activities have none of
  these; they rest on the card snapshots and `ActivityCardTextTests`.
- **A second page of a batch**: five activities fit one page; asking for more rests on `PagedListTests`.
- **The page after a delete**: the batch page keeps listing an activity deleted from its activity page until it is reopened, as the
  shared page does (its pages aren't cached and the presenter doesn't hear of the delete).
- **VoiceOver**: the card's default action, the badge's and locks' spoken lines and the icon's label weren't heard.
- **The page on iOS 18 signed in, and on an iPad**: the 18.4 simulator isn't signed in and the shared iPad wasn't used; the Swift tests ran
  on 18.4 with the snapshots skipped.

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
