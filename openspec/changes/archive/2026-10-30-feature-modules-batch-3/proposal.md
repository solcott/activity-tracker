# Proposal

## Why

After batch 2, twelve features live in feature modules. Three remain in the layer modules (`:app-screens`, `:app-presenters`, `composeApp`): profile, record and detail. Every moved feature that opens one of their screens still depends on `:app-screens` for it, and the shared modules `api`-depend on it too. Moving these three empties the layer modules, so this change also deletes them and the special cases the build carries for them. Afterwards a feature's screens, presenters and UIs can only live in feature modules.

## What Changes

- **The activity page moves to the shared modules.** Detail renders it for a saved activity, and record's import screen for a preview. That shared page is what ties record and detail to each other today. Keeping their packages:
  - its contract, `ActivityPageContent.kt` and `ActivityCharts.kt`, plus `DetailSection` (now in `ActivityDetailScreen.kt`), to `:app-common-api`;
  - the presenter code that builds it, `ActivityPage.kt` with `ChartsBuilder`, `MetricCards`, `StructureRows` and `DiveFigures`, to `:app-common-presenter`;
  - `GearTimePreview` and `DepthPreviewPoint` (now in `ImportActivityScreen.kt`) to `:app-common-api`, since `:app-common-ui`'s charts already draw them.
- **Three features move to feature modules**, each into `feature/<name>/{api,presenter,ui}` with its presenter and UI tests:
  - `profile`: `ProfileScreen`, `EditProfileScreen`, `FollowListScreen`, `FollowRequestsScreen`, `BlockListScreen`, their five presenters, and their UIs with the platform avatar compression;
  - `record`: `RecordScreen`, `ManualActivityScreen`, `ImportActivityScreen` (with `NewDevicesFields`, `ActivityTypeField`), their presenters and helpers (`NewDevices`, `DiveBatches`, `AddingRecording`, `OwnActivities`, `ImportDraft`, `SavedSuggestions`, `ActivityTypeChoice`), and their UIs;
  - `detail`: `ActivityDetailScreen`, `CombinePickerScreen` and `SegmentEffortGroups`, `ActivityDetailPresenter`, `CombinePickerPresenter` and their helpers, and their UIs.
- **Features that reached profile, record and detail through `:app-screens`** depend on their `-api` modules instead:
  - search, segment and settings open profile's screens;
  - segment opens detail's;
  - feed opens record's.

  `:app-common-presenter`'s activity-list sink depends on the detail and profile APIs, as it already does on media's.
- **`:app-screens` and `:app-presenters` are deleted**, with every dependency on them. Their entries in `checkModuleBoundaries` and its TestKit tests go too, as do the conformance test's source roots and the docs that describe the layer split. `:app-fixtures` lists each `-api` module directly. composeApp, webApp and desktopApp list each feature's presenter and UI module for their Metro graphs.
- **dev/module-boundaries**: "Feature module kinds" now applies to every feature, not only moved ones, since no layer module is left to hold one.

App behavior doesn't change: the same screens, strings and screenshots.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `dev/module-boundaries`: "Feature module kinds" applies to every feature's screens, presenters and UIs, and no shared or app module holds one.

## Impact

- **Gradle:** 9 new modules (3 features × 3) and 2 removed. That makes 45 feature modules, with no layer modules left.
- **Moved code:** about 2,700 lines of contracts, 4,800 of presenters and 4,900 of UI, plus about 40 presenter test files and 4 UI test files. That's the largest batch, because detail is the app's biggest screen. Packages stay the same, so imports in other files don't change; build scripts do.
- **Build logic:** `ModuleBoundaries.kt` and its tests drop `:app-screens` and `:app-presenters`. No new rule.
- **Docs:** README's module table, CLAUDE.md, the `compose-screens` and `build-logic` rules, the `gradle-runner` agent and the `new-screen` skill stop describing the layer modules.
- **CI:** measured as in batches 1 and 2.
- **No changes** to lexicons, the AppView or the data layer.
