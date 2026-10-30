# Design

## Context

See proposal.md for the motivation. Batch 1 left this in place:
- Each feature build script is one `plugins { }` line (`activitytracker.feature.{api,presenter,ui}`) plus any extra dependency. The `ui` convention already gives `jvmTest` Compose UI tests and `:app-fixtures`, but no feature has used it yet.
- `checkModuleBoundaries` treats `:app-screens` as an API module and `:app-presenters` as a presenter module, and exempts the app shells.
- `:app-common-presenter-testing` holds `ActivityTestBase`, `TestRoutes`, `TestSegments` and the presenter test helpers. It depends on `:core-data` and Ktor's mock engine, but not on `:app-common-presenter`.
- Pieces of these five features that other features use already moved to the common modules: `ActivityDetailsFields` and `DetailsForm` (`app.edit`), `GearLabels` (`app.gear`), `SegmentEffortsUi` (`app.segment`), `ZoneNames` and `HistoryDates` (`app.settings`).

The five features' files and what they reach outside themselves for (surveyed for this change; common-module imports left out):

| Feature | Contracts → `-api` | Presenters → `-presenter` | UIs → `-ui` | Reaches outside itself for |
|---|---|---|---|---|
| gear | 6 screens | 6 presenters | 9 files | nothing |
| edit | `EditActivityScreen` | `EditActivityPresenter`, `EditVisibility`, `LeaderboardExclusion` | `EditActivityUi` | record's `GearField` (contract), `GearChoice`, `GearChoices` (presenter) |
| segment | 7 screens | 7 presenters, `BoardTypes`, `SegmentDisplay` | 9 files | `ActivityDetailScreen`, `ProfileScreen` (presenters navigate) |
| settings | 6 screens, `HistoryDate` among them | 6 presenters, `DefaultVisibilitySettings`, `SegmentMatchingSetting` | 8 files | gear's `GearListScreen`, moderation's `ModerationScreen`, profile's `BlockListScreen` (presenters navigate) |
| feed | `FeedScreen`, `UnsentChangesScreen` | `FeedPresenter`, `FeedQuery`, `UnsentChangesPresenter` | `FeedUi`, `UnsentChangesUi` | feedfilter's API, `RecordScreen`, `ManualActivityScreen`, `ImportActivityScreen` (presenter navigates) |

None of their UIs uses another feature's UI code, and no State in `:app-screens` holds one of their types. Their sample states (`*Samples.kt`) stay in `:app-fixtures`.

Who uses them from outside:
- `:app-presenters`:
  - detail opens `EditActivityScreen`, `GearScreen`, `CreateSegmentScreen`, `SegmentScreen` and `TrainingZonesScreen`;
  - profile opens `StarredSegmentsScreen` and `SettingsScreen`;
  - record uses `GearField`, `GearChoice` and `GearChoices`.
- `:app-common-ui`: `HistoryDates` and `SegmentEffortsUi` read `HistoryDate`, and record's `GearPicker` reads `GearField`.
- composeApp:
  - `TopLevelDestination` (feed, edit, segment map, segment);
  - `SessionPreviewSubUis` (`ProposeSegmentNameScreen`);
  - its jvmTest: `ContentLoadingConformanceTest`, `AppPreviewUiTest` (`FeedUi`), `TestTagsUiTest` (`SegmentUi`).
- androidApp's screenshot tests: every UI.

Tests:
- Presenter tests in `:app-presenters`' jvmTest:
  - gear's five;
  - `EditActivityPresenterTest`;
  - segment's six;
  - settings' six;
  - `FeedPresenterTest`, `FeedQueryTest`, `UnsentChangesPresenterTest` and `SegmentsPromptTest`, which drives `FeedPresenter`.
- Gear and settings tests build on `SessionTestFixture` (608 lines). `EditActivityPresenterTest` and `FeedPresenterTest` use `TestZones`. `EditActivityPresenterTest` and `SettingsPresenterTest` use `VisibilityFieldsTesting`'s `tierOf`.
- `SessionTestFixture` imports three record presenter types, which its `Repos` builds the way Metro would inject them: `ActivityTypeChoices`, `GearChoices` and `NewDeviceChoices`.
- `CreateSegmentPresenterTest` extends `ActivityDetailTestBase` but drives only `CreateSegmentPresenter`.
- UI tests in composeApp's jvmTest: `UnsentChangesUiTest`, gear's four (`GearUiTest`, `GearSensorsUiTest`, `GearCollectionUiTest`, `GearListUiTest`), `SegmentCreditUiTest` and `SegmentNamesUiTest`.

## Goals / Non-Goals

**Goals:**
- Move the five features with no change to what any screen shows. Every presenter and UI test keeps passing with the same count, now in the feature's modules.
- Leave no feature in this batch reaching into another feature's presenter or UI code. Anything borrowed moves to a shared module, and screens of unmoved features come from `:app-screens` only.
- Keep the convention plugins and boundary rules unchanged. If a move needs a rule change, that is a design issue to raise, not something to absorb.

**Non-Goals:**
- Moving batch 3 (profile, record, detail), `activitylist` or `visibility`, or record's `ActivityTypeChoices` and `NewDeviceChoices`.
- Moving `GearChoiceTest`. It tests `GearChoices` but builds on the record-side fixture, and stays in `:app-presenters` until record moves.
- Shrinking `:app-screens`' or `:app-presenters`' own dependencies beyond what the moves require.

## Decisions

### One feature at a time, in an order that keeps the build green
Order: shared types and fixtures, then gear, edit, segment, settings, feed.
- Gear goes first because settings opens `GearListScreen`.
- Edit follows the shared gear-picker move it needs.
- Feed goes last because it's the largest consumer of other features (feedfilter, record) and the start destination, so the apps are exercised on top of everything else.

After each feature the build compiles, `checkModuleBoundaries` passes and its tests pass, which keeps any breakage small and gives a commit boundary.

### Shared types move to the common modules, keeping their packages
- `HistoryDate` moves out of `ThresholdHistoryScreen.kt` into its own file in `:app-common-api` (`app.settings`). `:app-common-ui` already reads it for `longDate` and segment effort dates. Leaving it in settings' API would make `:app-common-ui` depend on a feature.
- `GearField` (`app.record`, contract) moves to `:app-common-api`, and `GearChoice.kt` (`app.record`, `GearChoice` and `GearChoices`) to `:app-common-presenter`. Edit and record both use them, and record's `GearPicker` in `:app-common-ui` already renders `GearField`.

This follows how the earlier changes moved `DetailsForm` (`app.edit`) and `OwnRoute` (`app.detail`). Declarations that now cross a module become public. No importer changes.

Alternatives considered:
- `:app-common-ui` and edit depending on settings' or record's API. Rejected: common modules don't depend on features, and record's API doesn't exist until batch 3.
- Moving the gear picker into gear's modules. Rejected: record and edit embed it as a form field, not as a gear screen, and `GearPicker` is already common.

### Presenters that open unmoved screens depend on `:app-screens` until batch 3
Feed, segment and settings presenters add `implementation(projects.appScreens)` with a comment naming the screens, as search did for `ProfileScreen`. The boundary rules already allow it, since `:app-screens` counts as an API module. Batch 3 replaces each with the feature's `-api`.

### Layer modules and shells declare the `-api` modules they use
- `:app-presenters` adds `implementation` dependencies on the gear, edit, segment and settings APIs.
- `:app-fixtures` adds all five APIs.
- composeApp adds all five APIs, and webApp and desktopApp add each feature's presenter and UI for Metro aggregation, as for batch 1.
- `androidApp`'s screenshot tests add the five UI modules.
- composeApp's jvmTest adds the feed and segment UI modules for `AppPreviewUiTest` and `TestTagsUiTest`.

Within the batch, settings' presenter depends on `:feature-gear-api`, and feed's API and presenter depend on `:feature-feedfilter-api`. There are no cycles: no new `-api` module depends on `:app-screens`.

### Shared test fixtures move to `:app-common-presenter-testing`
`SessionTestFixture`, `TestZones` and `VisibilityFieldsTesting` move there:
- `TestZones` becomes public.
- `:app-common-presenter-testing` adds `:app-common-presenter`, for `GearChoices` in `Repos`. `:app-common-presenter`'s own tests don't use the testing module, so there is no cycle.
- It also adds `:core-location`, for `FakeLocationTracker` and `ActivityRecorder`, and whatever else the fixture's imports need.

`Repos.typeChoices` and `Repos.newDeviceChoices` build record presenter types, so they leave the class. They become extension properties in a `RecordChoices.kt` in `:app-presenters`' jvmTest, which record's tests and `ActivityTypeChoiceTest` already live beside. Call sites read the same, since `repos.typeChoices` resolves to the extension.

Alternative considered: giving gear and settings presenter tests a `jvmTest` dependency on `:app-presenters`. Rejected for the same reason as in batch 1: it couples moved features' tests to the module that's being emptied.

### Tests move with their feature
Presenter tests move to each feature's presenter `jvmTest`, keeping the flat `app` package:
- gear: `GearPresenterTest`, `GearListPresenterTest`, `GearCollectionPresenterTest`, `EditGearPresenterTest`, `IgnoredSensorsPresenterTest`;
- edit: `EditActivityPresenterTest`;
- segment: `SegmentPresenterTest`, `SegmentLeaderboardPresenterTest`, `SegmentMayorsPresenterTest`, `StarredSegmentsPresenterTest`, `ProposeSegmentNamePresenterTest`, `CreateSegmentPresenterTest`;
- settings: `SettingsPresenterTest`, `ZonesPresenterTest`, `SportZonesPresenterTest`, `TrainingZonesPresenterTest`, `ThresholdHistoryPresenterTest`, `PrivacyZonesPresenterTest`;
- feed: `FeedPresenterTest`, `FeedQueryTest`, `UnsentChangesPresenterTest`, `SegmentsPromptTest`.

Two need adjusting:
- `CreateSegmentPresenterTest` switches from `ActivityDetailTestBase` to `ActivityTestBase`. If it uses a detail-only helper, that helper moves into the test, or into `ActivityTestBase` if it's feature-neutral.
- `SegmentPresenterTest` and `SegmentLeaderboardPresenterTest` only compare navigation to `ActivityDetailScreen`, which segment's presenter module reaches through `:app-screens`.

UI tests move from composeApp's jvmTest to each feature's UI `jvmTest`, in their feature packages:
- `UnsentChangesUiTest` to feed;
- gear's four;
- `SegmentCreditUiTest` and `SegmentNamesUiTest` to segment.

Found while applying gear:
- `GearSensorsUiTest`'s first two tests render record's import screen (`NewDevicesForm`, `ImportActivityUi`). They stay in composeApp as record's `ImportSensorsUiTest`, and the gear UI tests move with three.
- The UI tests rendered through `AppPreview(subUis = SessionPreviewSubUis)`, which lives in composeApp. No UI in this batch embeds a sub-screen other than segment's own `ProposeSegmentNameScreen`, so moved tests use `AppPreview`'s default instead.
- Found while applying feed: `UnsentChangesUiTest.theFeedCountsRefusedChangesAndOpensThem` renders `FeedUi`, which embeds the feed filter. The filter's real UI is in `:feature-feedfilter-ui`, which feed's UI module may not depend on. With nothing in its place, the top bar's padding (on the filter's modifier) is lost, so the list slides under the bar and the click misses. The test supplies a `PreviewSubUis` that keeps the filter's place with an empty `Spacer`.
- `GearChoices` is the one `@Inject` class in `:app-common-presenter`. The shells' Metro graphs need it on their classpath, so composeApp, webApp and desktopApp add `:app-common-presenter`.

Tests that span features stay where they are:
- `ActivityDetailSegmentsTest` (detail);
- `GearChoiceTest` (Non-Goals);
- composeApp's `ContentLoadingConformanceTest`, `AppPreviewUiTest`, `TestTagsUiTest` and `ScreenshotCoverageTest`.

## Risks / Trade-offs

- **[Batch size]** About four times batch 1's code, in 15 modules. → The per-feature order keeps each step small enough to localize a failure, and each feature is its own commit.
- **[First feature UI tests]** No feature `-ui` module has run Compose UI tests yet, though the convention configures them. → Gear's UI tests move first, with a check that their count matches composeApp's before. If the convention's setup is missing something (resources, a test rule's dependency), fixing it is a convention change. Raise it before absorbing it.
- **[Fixture move]** `SessionTestFixture` is the largest shared test file, and record's tests depend on it too. → It moves before any feature does, with `:app-presenters:jvmTest`'s total count unchanged as the check.
- **[Build cost]** 15 more modules, each compiling for 5 targets. → Measure `check` locally and on CI as in batch 1. If CI time or memory nears its limits (45-minute timeout, 16 GB runner), stop before batch 3 and consider the plan's fallback (api + impl per feature).
- **[Feed is the start destination]** A wiring mistake in feed shows as a blank app, not one broken screen. → Run the apps after feed moves on at least web and one mobile platform.
