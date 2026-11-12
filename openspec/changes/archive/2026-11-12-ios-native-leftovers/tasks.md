# Tasks

## 1. Deprecation

- [x] 1.1 `UploadsPresenter` uses `adding` for the expanded batches. Verify `:feature-uploads-presenter:jvmTest` passes and the compile shows no deprecation warning there.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `LineChart.steps` and `ChartDot` (additive, defaults), `LineChart.of` takes them. Verify `LineChartTest`.
- [x] 2.2 `ThresholdHistoryTexts` (`:feature-settings-api`) with the texts and `chart(section, detailed)`; the short and long dates as `AppText`; the Compose page and `HistoryDates` resolve them. Verify a `ThresholdHistoryTextsTest`, `SharedTextNamesTest`, and the threshold history screenshot tests unchanged.
- [x] 2.3 `SportTypeSearch` in `:app-common-api`, `matchesTypeQuery` asks it; `TypePickerState.groups(query, source)` and `groups(matches)`; the Compose sheet asks it. Verify `ActivityTypeSearchTest`, a `SportTypeSearchTest`, the feed filter UI tests and screenshots unchanged.

## 3. Native views

- [x] 3.1 `LineChartView` draws steps and dots; a gallery entry with a step chart. Verify `DesignSnapshotTests` (existing charts unchanged, the new one looked at).
- [x] 3.2 `ThresholdHistoryView` with aliases, samples, `#Preview`s, the registry entry, `ScreenRegistryTests`, and `ThresholdHistorySnapshotTests` over every sample and variant, light and dark. Verify the snapshots on iOS 26.0, each image looked at.
- [x] 3.3 `TypePickerSheet` with search and disclosure; snapshots for picking, a search and an expanded sport. Verify `FeedFilterSnapshotTests` on iOS 26.0, each image looked at.

## 4. Reliable snapshot runs

- [x] 4.1 Reproduce the load-only failures and find their cause from the failing images; record it in the design. Verify by naming the cause with its evidence.
- [x] 4.2 Fix it in `Snapshots.swift` (or the suites). Verify two full `xcodebuild test` runs in a row on iOS 26.0 (`D6328F65`) pass.

## 5. The whole change together

- [x] 5.1 `./gradlew format` and `./gradlew check -Pkotlin.incremental=false`. Verify both pass.
- [x] 5.2 `xcodebuild test` on iOS 18.4 (`958C0DC3`). Verify it passes, snapshots skipped.
- [x] 5.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 6. See it

- [x] 6.1 On the signed-in iPhone 17 Pro `E426A46D` on the local stack: threshold history (expand, collapse, show all, the charts, light and dark) and the feed's type sheet (search, a collapsed sport expanded, apply). Verify by noting what was seen; nothing written, or anything written put back; record what couldn't be seen under "Not verified".

## 7. Docs

- [x] 7.1 The roadmap: a Phases bullet, "Phase 4 findings (leftovers)", and the open items closed (threshold history, the type sheet). Verify `openspec validate ios-native-leftovers --strict` passes.
- [x] 7.2 `.agents/rules/compose-screens.md` and `adaptive-ui.md` where a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 A "Not verified" section in this file listing what wasn't seen and why.

## Walk (iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

- **The type sheet (6.1).** Feed > All types opened the native sheet with an empty search, the quick types, and every sport with
  sub-sports collapsed (Run, Ride, Handcycle). Run's disclosure showed Trail, Treadmill and Track Run without touching the draft;
  picking Trail Run marked Run and Running with the part mark. Typing "gravel" left only Cycling > Gravel Ride, no quick types and an
  unmarked header; picking it and closing the search brought the full list back with Run still disclosed and both picks kept. Apply
  set the chip to "Gravel Ride + 1 more" and the feed to "No activities match these filters." Reopening showed it collapsed again with
  an empty search and part marks on Run and Ride; Cancel kept the filter, and Clear filters put the feed back to All types, Any time.
- **Threshold history (6.1).** alice had no thresholds, so Training zones offered no history. Threshold power was saved as 230 W, then
  250 W, through the native default zones form; Threshold history then opened natively: the help, Collapse all in the bar, the Default
  group with "1 threshold · 2 values", "250 W", "+20 W since Nov 12", the step chart (hollow 230, filled 250 with their labels, run on
  to Today, the lowest and highest on the axis, "Nov 12, 2026" and "Today" under it) and both values with "Entered by you" and Current.
  Collapse all turned the group into its tile with the sparkline and the bar into Expand all; the group's header expanded it again.
  Remove on 250 W (from its menu) put back 230 W, "Only value" and no chart; removing 230 W left "No thresholds yet" and no bar button.
- **Put back:** threshold power is Not set again, Threshold history and the zones' visibility row are gone from Training zones as
  before, and the feed's filters are cleared. The iPhone is left on Training zones in the You tab, running this change's Debug build.
- **Tunnel:** `scripts/dev.sh status` reported the named tunnel not running, but a `cloudflared` tunnel was running and both device
  hosts answered (200), so the walk used the local stack as usual; the status check doesn't recognise that tunnel process.

## Not verified

- Show all / Show fewer, an undated value, a sport's group, a pace threshold and a removal failing, on a device: alice's history had
  one threshold with two dated values. The snapshots draw each (`allRows`, `charted`, `pace`, `collapsed`, `removing`, `removeFailed`).
- Threshold history beside Settings' list on an iPad, dark mode and VoiceOver on a device: the snapshots draw the iPad layout and dark
  mode; the chart reads as one element with the shared description, unverified with VoiceOver.
- Scrubbing the step chart's readout on a device (the gallery snapshot draws it), and iOS 18 signed in (the roadmap's open item).

## Workflow follow-up

- Archive with `/opsx:archive`, syncing the specs, once everything above is done and verified.

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed (after the lint fixes in `f4c81c35`).
- `xcodebuild test` on iOS 26.0 (`D6328F65`): two full runs in a row passed, 385 tests each (runs 5 and 6), after the settling draw;
  before it, a full run failed `GearEditorsSnapshotTests` `nothingToAdd.dark` with the suites passing alone.
- `xcodebuild test` on iOS 18.4 (`958C0DC3`): passed, 385 tests, snapshots skipped.
- Release for the simulator: built, the stub check passed.
- `openspec validate ios-native-leftovers --strict`, `scripts/check-agent-sync.sh`, `scripts/check-public-safety.sh`: passed.
