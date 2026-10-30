# Proposal

## Why

`feature-modules-batch-1` moved the six features other features depend on least, so search, login, map, media, report, moderation and feedfilter now live in feature modules. The other eleven feature packages still share the layer modules (`:app-screens`, `:app-presenters`, `composeApp`). This change moves the second batch: feed, edit, gear, segment and settings. Detail and profile open their screens, and record shares their gear picker, so these five have to move before batch 3 (profile, record, detail) can.

## What Changes

- **Five features move to feature modules**, each into `feature/<name>/{api,presenter,ui}` with its presenter tests and, new in this batch, its UI tests:
  - `gear`: `GearScreen`, `GearListScreen`, `GearCollectionScreen`, `EditGearScreen`, `EditGearCollectionScreen`, `IgnoredSensorsScreen`, their six presenters and their UIs (with `GearComponentsUi`, `LinkedSensorRow`, `GearCollectionsField`);
  - `edit`: `EditActivityScreen`, `EditActivityPresenter` (with `EditVisibility`, `LeaderboardExclusion`), `EditActivityUi`;
  - `segment`: `SegmentScreen`, `SegmentMapScreen`, `SegmentLeaderboardScreen`, `SegmentMayorsScreen`, `StarredSegmentsScreen`, `CreateSegmentScreen`, `ProposeSegmentNameScreen`, their seven presenters (with `BoardTypes`, `SegmentDisplay`) and their UIs (with `SegmentCredit`, `SegmentNamesUi`);
  - `settings`: `SettingsScreen`, `ZonesScreen`, `SportZonesScreen`, `TrainingZonesScreen`, `ThresholdHistoryScreen`, `PrivacyZonesScreen`, their six presenters (with `DefaultVisibilitySettings`, `SegmentMatchingSetting`) and their UIs (with `ThresholdHistoryChart`, `ThresholdHistoryText`);
  - `feed`: `FeedScreen`, `UnsentChangesScreen`, `FeedPresenter`, `FeedQuery`, `UnsentChangesPresenter`, `FeedUi`, `UnsentChangesUi`.
- **Types that other features share move to the common modules**, keeping their packages:
  - `HistoryDate` (now in `ThresholdHistoryScreen.kt`) to `:app-common-api`. `:app-common-ui`'s date labels and segment efforts already read it.
  - Record's `GearField` to `:app-common-api` and `GearChoice.kt` (`GearChoice`, `GearChoices`) to `:app-common-presenter`. Edit uses both, and record keeps using them.
- **Shared test fixtures move to `:app-common-presenter-testing`:** `SessionTestFixture`, `TestZones` and `VisibilityFieldsTesting`. Gear, settings, edit and feed tests build on them. The fixture's two record-only producers (`typeChoices`, `newDeviceChoices`) stay in `:app-presenters`' tests until record moves.
- **Modules that open these features' screens** depend on their `-api` modules: `:app-presenters` (detail opens edit, gear, segment and training zones; profile opens starred segments and settings), `:app-fixtures`, composeApp (`TopLevelDestination`, `SessionPreviewSubUis`, its UI tests), webApp, desktopApp and androidApp's screenshot tests.
- **Three presenter modules depend on `:app-screens` for now**, as search did, for screens of features batch 3 moves:
  - feed opens `RecordScreen`, `ManualActivityScreen` and `ImportActivityScreen`;
  - segment opens `ActivityDetailScreen` and `ProfileScreen`;
  - settings opens `BlockListScreen`.
- **CLAUDE.md** and the `compose-screens` rule list the moved features.

App behavior doesn't change: the same screens, strings and screenshots.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
<!-- None. dev/module-boundaries already states the rules these moves follow. This change applies them to five more features without changing a requirement, so it sets skip_specs. -->

## Impact

- **Gradle:** `settings.gradle.kts` gains 15 modules (5 features × 3), bringing feature modules to 36. No convention plugin or boundary-rule change is expected.
- **Moved code:** about 1,800 lines of contracts, 3,600 of presenters and 5,800 of UI, plus 21 presenter test files and 7 UI test files. That is about four times batch 1. Packages stay the same, so imports in other files don't change; only build scripts do.
- **`internal` declarations become public** where they now cross a module, such as `TestZones` and any feature-internal declaration a moved test or another feature reads.
- **CI:** batch 1's 18 modules showed no CI slowdown (14m 5s for a mostly uncached `check`, against the foundation's cold 25m 35s). This change measures again with 15 more.
- **No changes** to lexicons, the AppView, data-layer code or the module rules.
