# Design

## Context

See proposal.md for the motivation. Batch 2 left this in place:
- Twelve features in `feature/<name>/{api,presenter,ui}`, built by the `activitytracker.feature.*` conventions. Their UI tests run in the feature's `-ui` module. A moved test that renders another feature's sub-screen supplies its own `PreviewSubUis`.
- `checkModuleBoundaries` (`ModuleBoundaries.kt`) still treats `:app-screens` as an API module and `:app-presenters` as a presenter module, and its TestKit tests name both.
- These modules depend on `:app-screens`:
  - `:app-common-presenter` and `:app-common-ui` (`api`);
  - `:app-fixtures` (`api`);
  - the shells;
  - the search, segment, settings and feed presenters, which reach it for profile's, detail's and record's screens.
- `:app-presenters` is listed by composeApp, webApp and desktopApp for their Metro graphs.
- `:app-presenters`' jvmTest holds 343 tests in 41 files, all of them profile's, record's or detail's, or shared ones those use (`RecordChoices.kt`, `ActivityDetailTestBase`, `CombineTestBase`).

What is left, and what it reaches across (surveyed for this change):

| Feature | Contracts (`:app-screens`) | Presenters (`:app-presenters`) | UIs (composeApp) | Reaches outside itself for |
|---|---|---|---|---|
| profile | 5 screens | 5 presenters | 5 UIs, plus `AvatarCompression` (common, nonWeb and wasmJs) | nothing beyond the common modules |
| record | 3 screens, `NewDevicesFields`, `ActivityTypeField` | 3 presenters and 7 helpers | 9 UIs and forms | detail's page: `ActivityPageContent`, `ActivityCharts`, `DetailSection`, `FigureValue`, `MetricCard` (contracts) and `ActivityPage` (presenter); `ActivityDetailScreen` (navigation) |
| detail | `ActivityDetailScreen`, `ActivityPageContent`, `ActivityCharts`, `SegmentEffortGroups`, `CombinePickerScreen` | 2 presenters and 15 helpers | 11 UIs | `ProfileScreen`, `ImportActivityScreen` (navigation) |

The tie between record and detail is the activity page:
- `ActivityPage` (an object in `ActivityPage.kt`) builds an `ActivityPageContent` from an activity view. `ChartsBuilder`, `MetricCards`, `StructureRows` and `DiveFigures` help it, and nothing else in detail's presenters reaches into them except `ActivityDetailPresenter`.
- Detail shows the page for a saved activity, and record's import screen for the preview of a file.
- In the contracts, only `DetailSection` (in `ActivityDetailScreen.kt`) is reached by `ActivityPageContent.kt` and `ActivityCharts.kt`.

`:app-common-ui` already renders the page's pieces: `StartTimeText` reads `FigureValue`, `DiveFormat` reads `GasKind` from `ActivityCharts.kt`, and the depth and gear-time charts read `DepthPreviewPoint` and `GearTimePreview` from `ImportActivityScreen.kt`. Today it gets all of them through its `api` dependency on `:app-screens`.

Elsewhere:
- `RecordScreenRequests` (`app.record`, composeApp) is an `AppScope` singleton that `App` collects to open `RecordScreen` when the recording notification is tapped. It belongs to the app shell, not to record's screens.
- composeApp's jvmTest has three tests that span features: `SeamsUiTest` (detail and profile UIs), `TestTagsUiTest` and `AppPreviewUiTest`.

## Goals / Non-Goals

**Goals:**
- Move profile, record and detail with no change to what any screen shows. Every presenter and UI test keeps passing with the same count.
- Delete `:app-screens` and `:app-presenters`, and every special case and doc line that exists only because of them.
- Leave the boundary rules otherwise unchanged. Deleting the special cases makes them stricter, not looser.

**Non-Goals:**
- Moving composeApp's app-level code (`App`, `nav`, `di`, `preview`, `RecordScreenRequests`). It is the shell.
- Splitting detail's large presenter or UI files. They move as they are.
- Revisiting batch 1's and 2's shared modules beyond what these moves need.

## Decisions

### The activity page moves to the shared modules first
Before any feature moves, keeping packages (`app.detail`):
- `ActivityPageContent.kt` and `ActivityCharts.kt` go to `:app-common-api`. `DetailSection` leaves `ActivityDetailScreen.kt` for its own file there.
- `ActivityPage.kt`, `ChartsBuilder.kt`, `MetricCards.kt`, `StructureRows.kt` and `DiveFigures.kt` go to `:app-common-presenter`, and the declarations other modules then use become public.

Likewise `GearTimePreview` and `DepthPreviewPoint` leave `ImportActivityScreen.kt` (`app.record`) for `:app-common-api`.

Found while applying: `ActivityPage` also calls `detailSections` (two overloads) and `poolLength`, which sat at the bottom of `ActivityDetailPresenter.kt` with the stream-kind sets `detailSections` reads (`RUNNING_DYNAMICS`, `CYCLING_DYNAMICS`, `PHYSIOLOGY`, `E_BIKE`). They are the page's, so they move into a `PageSections.kt` beside it.

After this, record's and detail's API modules don't refer to each other. Each presenter only opens the other's screens. `:app-common-ui` stops needing `:app-screens` for these types.

Alternatives considered:
- Record depending on `:feature-detail-api` for the page types, and on a detail-owned presenter helper. Rejected: a feature may not reach another feature's presenter, and the page belongs to both.
- A separate `activitypage` feature. Rejected: it has no screen of its own. It is a shared building block, which is what the common modules are for.

### Order: profile, record, detail, then the deletion
- Profile goes first: nothing in it reaches another unmoved feature.
- Record goes next. Its presenter opens `ActivityDetailScreen`, so `:feature-record-presenter` depends on `:app-screens` until detail moves, as batch 2 did.
- Detail goes last. It opens `ProfileScreen` and `ImportActivityScreen` through their new APIs.

Each move also repoints the modules that reached the feature through `:app-screens`:
- profile: the search, segment and settings presenters, `:app-common-presenter` and `:app-fixtures`;
- record: feed's presenter;
- detail: segment's and record's presenters, `:app-common-presenter`.

Once detail has moved, `:app-screens` and `:app-presenters` hold no sources, and the last step deletes them.

### Found while moving detail
- `:app-common-ui`'s `DataSourcesUi.kt` read detail's contracts (`DataSources`, `DataSourceRow`, `ActivityDetailEvent`) through `:app-screens`. Its `DataSourcesSection` is only detail's (the combination editor), so it moves to `:feature-detail-ui`. `ChartSourceCaption`, which the shared page cards use, stays.
- Two files with the same name in the same package, in different modules, compile to the same file-facade class, so a classpath holding both modules sees only one of them. The moved file is therefore `DataSourcesSection.kt`, not `DataSourcesUi.kt`.

### `:app-common-presenter` depends on the detail and profile APIs
The activity-list sink opens `ActivityDetailScreen` and `ProfileScreen`. It already depends on media's API for the gallery, so it adds detail's and profile's in place of `:app-screens`. There is no cycle: neither API depends on `:app-common-presenter`.

### Feature API dependencies
- `:feature-profile-api` and `:feature-detail-api` `api`-depend on `:feature-report-api`, since `ProfileState` and `ActivityDetailState` hold `Reporting`.
- Detail's API adds whatever else its States hold from moved features: media, map, segment and edit types, found while applying.

### Tests move with their feature
- **profile**: `ProfilePresenterTest`, `EditProfilePresenterTest`, `FollowListPresenterTest`, `FollowRequestsPresenterTest`, `BlockListPresenterTest`, and `ApprovesFollowersTest`, which tests `:app-common-presenter`'s `rememberApprovesFollowers` through the presenter test helpers.
- **record**:
  - `RecordPresenterTest`, `ManualActivityPresenterTest`, `ActivityTypeChoiceTest`, `GearChoiceTest`;
  - the import tests (`ImportActivityPresenterTest`, `ImportActivityNotesTest`, `ImportCombinePresenterTest`, `ImportFileFormatTest`, `ImportGearTest`, `ImportLocalTimeTest`, `ImportLogbookPresenterTest`, `ImportPreviewPageTest`, `ImportSensorGearTest`, `ImportSourceFileTest`);
  - `ActivityPagePreviewTest`, which builds the page from record's `importDraft`;
  - `RecordChoices.kt`, the record producers over `SessionTestFixture`;
  - UI tests: `RecordUiTest`, `ManualActivityUiTest`, `ImportSensorsUiTest`.
- **detail**:
  - the `ActivityDetail*Test` files with `ActivityDetailTestBase`;
  - `ActivityFileDataChartsTest`, `WorkoutStructureChartsTest`;
  - `CombineTestBase` and the three `Combine*Test` files;
  - `AwaitIndexedTest`, `GroupEffortsTest`;
  - UI test: `StartTimeUiTest`.
- **`:app-common-presenter`'s jvmTest**: `ActivityPageTest`. It tests `ActivityPage` directly, with no presenter test helpers, as `SingleFlightTest` does there.

These stay in composeApp's jvmTest: `SeamsUiTest`, `TestTagsUiTest` and `AppPreviewUiTest`.

### Deleting the layer modules
- `settings.gradle.kts` drops `app/screens` and `app/presenters`, and both directories go, with any stray empty package directories left by earlier batches.
- These modules stop listing them, and list each `-api`, `-presenter` or `-ui` module they need instead:
  - the shells;
  - `:app-common-presenter` and `:app-common-ui`;
  - `:app-fixtures`;
  - the four feature presenter modules with a temporary `:app-screens` dependency.
- `ModuleBoundaries.kt` drops the two path special cases and their KDoc. Its TestKit tests drop or replace the cases that name them. Each replacement still covers its rule with a real module pair: an API depending on another API passes, a shared UI module depending on a presenter module fails.
- These comments and tests stop naming `:app-screens`:
  - `ContentLoadingConformanceTest` collects States from `-api` modules (and `:app-common-api`) only;
  - the comments in `ComposeStability.kt` and `UseSingleFlight.kt`.

## Risks / Trade-offs

- **[Detail is the biggest screen]** About 1,100 lines of contracts, 1,700 of presenters (after the page leaves) and 1,700 of UI, plus 18 test files. → It moves last, on its own, after the shared page has moved and been verified. Its tests' count is the check.
- **[Hidden `:app-screens` users]** Some module may use a type from `:app-screens` that the survey missed, through the `api` chain from `:app-common-presenter` or `:app-common-ui`. → Deleting the modules is its own step. A compile failure there names the type, which then moves to the API that owns it.
- **[Boundary tests]** The TestKit tests encode the special cases. → They change in the same step as `ModuleBoundaries.kt`, with `./gradlew -p build-logic :convention:test` as the check.
- **[Build cost]** 9 more modules and 2 fewer. → Measure as in batches 1 and 2. This batch should cost less than either.
