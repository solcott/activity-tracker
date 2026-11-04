# Tasks

## 1. Dependency and shared contracts

- [x] 1.1 Add `org.jetbrains.compose.material3.adaptive:adaptive` and `:adaptive-layout` 1.3.0 to `gradle/libs.versions.toml` with the catalog notes from design.md, and to `composeApp` commonMain only. Verify `./gradlew :composeApp:compileKotlinJvm` resolves them and `./gradlew :composeApp:dependencies` shows no material3 downgrade.
- [x] 1.2 Add `interface SettingsPage : Screen` and the `@Immutable` `LocalSettingsPane` (`twoPane`, `shownPage`) to `:app-common-api`. Add `LocalIsPaneRoot` beside `LocalIsNavRoot` in `ui/designsystem` `AppTopBar.kt`, and make `AppTopBar` (common and `AppTopBar.ios.kt`) hide back when it is set, without a large title. Verify with a designsystem UI test for back hidden under `LocalIsPaneRoot`, and `./gradlew :app-common-api:composeStabilityCheck`.
- [x] 1.3 Mark `PrivacyZonesScreen`, `TrainingZonesScreen`, `SportZonesScreen`, `ZonesScreen`, `ThresholdHistoryScreen`, `GearListScreen` and `BlockListScreen` as `SettingsPage`. Verify `./gradlew checkModuleBoundaries` passes and the modules compile.

## 2. List row and section wrappers

- [x] 2.1 Add `AppListSection` and `AppListRow` (title, optional summary, trailing chevron or control, selected, enabled, onClick) to `ui/adaptive`: Material `ListItem` in `nonAppleMain`, and the inset grouped iOS look in `iosMain`. Verify a jvmTest that clicking a row and its selected semantics work, and that detekt `UseAdaptiveComponents` passes on the wrapper files.
- [x] 2.2 Add both to `AdaptiveControlsScreenshots.kt` at `@PreviewComponentVariants`, and to the wrapper list in `.agents/rules/adaptive-ui.md`. Verify with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, then review the new references.

## 3. Activity defaults page

- [x] 3.1 Add `ActivityDefaultsScreen`, `ActivityDefaultsState` (`defaults: VisibilityFields?`, `saving`, `error`) and `ActivityDefaultsEvent` (`Back`) to `feature/settings/api` as a `SettingsPage`. Verify `:feature-settings-api` compiles with its stability check.
- [x] 3.2 Add `ActivityDefaultsPresenter` over `rememberDefaultVisibilitySettings` with its own `UiError`. Move the defaults cases out of `SettingsPresenterTest` into `ActivityDefaultsPresenterTest` (change, failed save keeps the earlier choice, double send writes once). Verify `./gradlew :feature-settings-presenter:jvmTest`.
- [x] 3.3 Move `DefaultVisibility` into `ActivityDefaultsUi` (`AppScaffold`, `AppTopBar(onBack)`, readable scroll). Add `ActivityDefaultsSamples` in `:app-fixtures`, `ActivityDefaultsUiTest`, and `ActivityDefaultsScreenshots.kt`. Verify `./gradlew :feature-settings-ui:jvmTest` and `ScreenshotCoverageTest`.

## 4. Segments & groups page

- [x] 4.1 Add `SegmentsAndGroupsScreen`, its state (the segment, leaderboard, bracket and group fields, the `canUse*` and `saving*` flags, and `error`) and `SegmentsAndGroupsEvent` (moving `SegmentSettingEvent` and `GroupActivitiesChanged` there) to `feature/settings/api` as a `SettingsPage`. Verify the api module compiles with its stability check.
- [x] 4.2 Add `SegmentsAndGroupsPresenter` over `rememberSegmentMatchingSetting` and `rememberGroupActivitiesSetting`. Move their cases from `SettingsPresenterTest` into `SegmentsAndGroupsPresenterTest`: leaderboards offered only while matching is on, brackets gated by the grant, sign-in-again states, grouping choices, and failures reported on the page. Verify `./gradlew :feature-settings-presenter:jvmTest`.
- [x] 4.3 Move `SegmentMatching` and `GroupActivities` into `SegmentsAndGroupsUi`, and fold `GroupActivitiesUiTest` into `SegmentsAndGroupsUiTest`. Add `SegmentsAndGroupsSamples` and `SegmentsAndGroupsScreenshots.kt`. Verify `./gradlew :feature-settings-ui:jvmTest`.

## 5. Settings list

- [x] 5.1 Slim `SettingsState` to units, `saving`, `error`, the summaries (`defaultActivityVisibility`, `privacyZoneCount`, `hidesRouteEnds`, `segmentMatching`) and `canModerate`. Replace `OpenZones` and the other open events with `Open(page)` events for the rows. Verify the api compiles with its stability check.
- [x] 5.2 Make `SettingsPresenter` read the summaries directly from `ActivityDefaults`, `PrivacyZones` and `SegmentPrefsRepository`, and make every row (including Moderation) `popUntil { it is SettingsScreen }` then `goTo`. Trim `SettingsPresenterTest` to units, the summaries (left out until read), moderation gating, sign-out, and a row opened from a stack with a page above Settings replacing that page. Verify `./gradlew :feature-settings-presenter:jvmTest`.
- [x] 5.3 Rebuild `SettingsUi` as `AppListSection`s of `AppListRow`s in the spec's order, with units inline under General and rows marked from `LocalSettingsPane.shownPage`. Add the section, row and summary strings to `:ui-resources`. Update `SettingsSamples` (summaries read and unread, moderator), the UI tests (order, no Moderation for non-moderators, the selected row marked) and `SettingsScreenshots.kt`. Verify `./gradlew :feature-settings-ui:jvmTest`.

## 6. List-detail decoration

- [x] 6.1 Add `SettingsListDetailDecoration` and its factory in `composeApp` `nav/`, delegating to `GestureNavigationDecorationFactory`'s decoration: find the settings run, measure the available width, trim the args, and render `ListDetailPaneScaffold` with the directive fixes from design.md, `ProvideRecordLifecycle(isActive = true)` panes, `movableContentOf`, the auto-shown `CircuitContent(ActivityDefaultsScreen)`, and the `LocalSettingsPane` / `LocalIsPaneRoot` values. Use it in `App.kt`. Verify `./gradlew :composeApp:compileKotlinJvm`.
- [x] 6.2 Add `SettingsListDetailDecorationTest` (composeApp jvmTest, `runComposeUiTest` at a set width) covering `[.., Settings]`, `[.., Settings, Page]`, `[.., Settings, Page, SubPage]` and `[.., Settings, Page, Profile]` below and above 840dp. Check what each pane shows, that the delegate's top is Settings, back from each shape, and a resize across 840dp keeping the list's state. Verify `./gradlew :composeApp:jvmTest --tests "*.SettingsListDetailDecorationTest"`.
- [x] 6.3 Add a two-pane screenshot preview of Settings with a page open, and with the auto-shown page, at an expanded size. Add a paragraph on the decoration and `SettingsPage` to `.agents/rules/adaptive-ui.md`. Verify with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, then review the references.

## 7. Integration checks

- [x] 7.1 Run `./gradlew check`: all targets' tests, lint, ktfmt, detekt, `composeStabilityCheck`, `checkModuleBoundaries`, screenshots and `ContentLoadingConformanceTest`. Verify it passes.
- [x] 7.2 Run `./gradlew :desktopApp:hotRun --auto` and drive it with the hot-reload MCP: wide Settings shows Activity defaults; choosing rows replaces the pane; Blocked → a profile opens full width and back returns to the pane; Moderation is full width; resizing across 840dp keeps the page; the back shortcut works. Verify with screenshots.
- [x] 7.3 Check the iPhone simulator (single pane, edge swipe back), the web app (`:webApp:wasmJsBrowserDevelopmentRun`) and the Android debug build. Verify each matches the narrow- or wide-window scenarios in `specs/ui/settings-layout/spec.md`.

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed, which creates `openspec/specs/ui/settings-layout/spec.md`.
