# Tasks

## 1. Lexicon and AppView

- [x] 1.1 Add `leaders` (`#segmentLeader`, reusing `feed.defs#leaderboardEntry` for the leader and the viewer) to `lexicons/app/activitytracker/feed/getPath.json` and mirror it in `core/lexicon`'s `GetPath`. Verify with `./gradlew :core-lexicon:jvmTest` and with the `lexicon-auditor` agent on `feed.getPath`.
- [x] 1.2 Add `leadersOf(viewer, segments)` to `SegmentViews` over `boardsOf`, `boardOf`, `bests` and `entries`, and call it from `path` for active, ranked segments only. Verify with new `SegmentLeaderboardsTest` cases: a leader and the viewer's own entry are present, a proposed segment has none, and a signed-out viewer has no own entry. Lifts lie on no path (`segmentView.paths`), and `path` passes only ranked kinds anyway.
- [x] 1.3 Assert in `SegmentLeaderboardsTest` that each `getPath` leader equals the first entry and the `own` entry of `getSegmentLeaderboard` for the same segment, including when the fastest athlete hides from leaderboards, has an excluded activity, or is blocked by the viewer. Verify with `./gradlew :server-appview:test --tests "*SegmentLeaderboardsTest" --tests "*SegmentPathsTest"`.

## 2. Shared contract and words

- [x] 2.1 Add `PathContent.leaders` (the lexicon's `GetPath.SegmentLeader`, which Swift reads as it reads `LeaderboardEntry`) and `leaderOf(group)`. Map them in `PathPresenter.contentOf`. Verify with a presenter test in `feature/segment/presenter` jvmTest that a served leader reaches the shown group's row, and with `./gradlew composeStabilityCheck`.
- [x] 2.2 Add `PathTexts.leaderTime`, `leaderName` and `yours`, reusing `SegmentBoardTexts.time` and `segment_leaderboard_yours`. Verify with `PathLeaderTextsTest` (`feature/segment/ui` jvmTest), for example "You: 14:02 · #12".
- [x] 2.3 Make `SegmentBrowseTexts` leave out a lone "Both ways" when the group's name shows ↔. Verify with `SegmentBrowseTextsTest` cases for a two-way stretch, a whole trail and mixed directions.
- [x] 2.4 Give `PathSamples` (`:app-fixtures`) leaders for the loaded, sections, selected and trail samples: one row with the viewer ranked, one led by the viewer, and one row with no leader. Verify that `./gradlew :composeApp:jvmTest --tests "*ContentLoadingConformanceTest"` still passes.

## 3. Compose row

- [x] 3.1 Add an optional `trailing` slot and an `extra` line to `SelectableRow`. Put the swatch on the name's first line and start wrapped lines under the name, with a tabular, end-aligned time column. Browse callers stay unchanged. Verify with `./gradlew :feature-segment-ui:jvmTest` and `SegmentBrowseUiTest`.
- [x] 3.2 Pass the leader column and the viewer's line from `PathUi`'s `GroupRow`. Verify with `PathUiTest` cases: the time and handle show, "You: … · #…" shows only when ranked, and a row with no leader has no column.
- [x] 3.3 Re-record the Android screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`. Look at the Path, SegmentBrowse and DiveSiteBrowse phone, large-text and dark images, including a long name wrapping beside the column, before keeping them.

## 4. iOS row

- [x] 4.1 Pass the time column as `AppItemRow`'s trailing view in `PathPage.swift`'s `row` and add the viewer's line. Align the leading swatch to the title's first line, in `AppItemRow` if needed. Verify with an `xcodebuild` build of the iosApp scheme.
- [x] 4.2 Re-record the `InteractiveMapsSnapshotTests` `path-sample.*` snapshots on the iOS 26.0 simulator, running the suite twice so the second run compares clean. Look at the light, dark and wrapped-name rows before keeping them.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and verify it passes.
- [x] 5.2 Open a Trail page with ranked segments on the Android emulator and confirm that the rows show leaders, the viewer's place where ranked, and no column for unranked rows.

## Workflow follow-up

- Archive the change once it is reviewed and committed.
