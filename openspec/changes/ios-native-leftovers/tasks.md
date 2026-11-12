# Tasks

## 1. Deprecation

- [ ] 1.1 `UploadsPresenter` uses `adding` for the expanded batches. Verify `:feature-uploads-presenter:jvmTest` passes and the compile shows no deprecation warning there.

## 2. Kotlin rules (no visible change)

- [ ] 2.1 `LineChart.steps` and `ChartDot` (additive, defaults), `LineChart.of` takes them. Verify `LineChartTest`.
- [ ] 2.2 `ThresholdHistoryTexts` (`:feature-settings-api`) with the texts and `chart(section, detailed)`; the short and long dates as `AppText`; the Compose page and `HistoryDates` resolve them. Verify a `ThresholdHistoryTextsTest`, `SharedTextNamesTest`, and the threshold history screenshot tests unchanged.
- [ ] 2.3 `SportTypeSearch` in `:app-common-api`, `matchesTypeQuery` asks it; `TypePickerState.groups(query, source)` and `groups(matches)`; the Compose sheet asks it. Verify `ActivityTypeSearchTest`, a `SportTypeSearchTest`, the feed filter UI tests and screenshots unchanged.

## 3. Native views

- [ ] 3.1 `LineChartView` draws steps and dots; a gallery entry with a step chart. Verify `DesignSnapshotTests` (existing charts unchanged, the new one looked at).
- [ ] 3.2 `ThresholdHistoryView` with aliases, samples, `#Preview`s, the registry entry, `ScreenRegistryTests`, and `ThresholdHistorySnapshotTests` over every sample and variant, light and dark. Verify the snapshots on iOS 26.0, each image looked at.
- [ ] 3.3 `TypePickerSheet` with search and disclosure; snapshots for picking, a search and an expanded sport. Verify `FeedFilterSnapshotTests` on iOS 26.0, each image looked at.

## 4. Reliable snapshot runs

- [ ] 4.1 Reproduce the load-only failures and find their cause from the failing images; record it in the design. Verify by naming the cause with its evidence.
- [ ] 4.2 Fix it in `Snapshots.swift` (or the suites). Verify two full `xcodebuild test` runs in a row on iOS 26.0 (`D6328F65`) pass.

## 5. The whole change together

- [ ] 5.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [ ] 5.2 `xcodebuild test` on iOS 18.4 (`958C0DC3`). Verify it passes, snapshots skipped.
- [ ] 5.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 6. See it

- [ ] 6.1 On the signed-in iPhone 17 Pro `E426A46D` on the local stack: threshold history (expand, collapse, show all, the charts, light and dark) and the feed's type sheet (search, a collapsed sport expanded, apply). Verify by noting what was seen; nothing written, or anything written put back; record what couldn't be seen under "Not verified".

## 7. Docs

- [ ] 7.1 The roadmap: a Phases bullet, "Phase 4 findings (leftovers)", and the open items closed (threshold history, the type sheet). Verify `openspec validate ios-native-leftovers --strict` passes.
- [ ] 7.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 7.3 A "Not verified" section in this file listing what wasn't seen and why.

## Workflow follow-up

- Archive with `/opsx:archive`, syncing the specs, once everything above is done and verified.
