# Design

## Context

See proposal.md for the motivation. The foundation change left this in place:
- The convention plugins `activitytracker.feature.{api,presenter,ui}`. Each feature build script is one `plugins { }` line plus any extra dependency.
- `checkModuleBoundaries` reads each module's kind from its path. It treats `:app-screens` as an API module and `:app-presenters` as a presenter module, and exempts the app shells (`composeApp`, `androidApp`, `webApp`, `desktopApp`).
- `:app-common-presenter` and `:app-common-ui` `api`-depend on `:app-screens` until it's empty. `:app-presenters` depends on `:app-screens` and `:app-common-presenter`.
- `ScreenshotCoverageTest` already walks every `feature/*/ui`. `ContentLoadingConformanceTest` already collects States from every `-api` module on composeApp's jvmTest classpath.
- `SessionPreviewSubUis` (composeApp) maps `FeedFilterScreen` and `ReportScreen` to their real UIs for `AppPreview`.

The six features' files and their cross-package imports (surveyed for this change):

| Feature | Contracts → `-api` | Presenter → `-presenter` | UI → `-ui` | Reaches outside itself for |
|---|---|---|---|---|
| login | `LoginScreen` (`Expiry`) | `LoginPresenter` (`AppScope`) | `LoginUi` | `rememberSingleFlight` |
| map | `RouteMapScreen` | `RouteMapPresenter` | `RouteMapUi` | detail's `rememberOwnRoute`, `shownRoute` |
| media | `MediaGalleryScreen` | `MediaGalleryPresenter` | `MediaGalleryUi` | report's `Reporting`, `rememberReporting`, `ReportingHost` |
| report | `ReportScreen`, `Reporting`, `ReportingEvent`, `BlockOffer` | `ReportPresenter` | `ReportUi` | nothing beyond common |
| moderation | `ModerationScreen`, `ModerationSubject` | `ModerationPresenter` | `ModerationUi` | nothing beyond common |
| feedfilter | `FeedFilterScreen`, `FeedFilter`, `DateRange`, `Selection` | `FeedFilterPresenter`, `TypeSelection` | `FeedFilterUi` | record's `quickActivityTypes` |

None of the six presenters navigates to an unmoved feature's screen, so unlike search none of them needs a temporary `:app-screens` dependency. Their sample states (`*Samples.kt`) stay in `:app-fixtures`.

Who uses them from outside:
- `:app-screens`:
  - `ActivityDetailState` and `ProfileState` hold `Reporting`;
  - `FeedState` holds `FeedFilter` and embeds `FeedFilterScreen`.
- `:app-presenters`:
  - detail opens `RouteMapScreen` and `MediaGalleryScreen`;
  - settings opens `ModerationScreen`;
  - feed embeds `FeedFilterScreen` and maps `FeedFilter` to a query.
- `:app-common-presenter`:
  - `rememberReporting` builds a `Reporting`;
  - the activity-list sink opens `MediaGalleryScreen`.
- `:app-common-ui`: `ReportingHost` embeds `ReportScreen` by key.
- composeApp:
  - `App.kt` (`LoginScreen`);
  - `TopLevelDestination` (map, gallery);
  - `SessionPreviewSubUis`;
  - the detail, profile and feed UIs (`ReportingEvent`, `FeedFilterOuterEvent`).

## Goals / Non-Goals

**Goals:**
- Move the six features with no change to what any screen shows. Every presenter test keeps passing with the same count, now in the feature's presenter module.
- Leave no feature in this batch reaching into another feature's presenter or UI code. Anything borrowed moves to a shared module.
- Keep the convention plugins and boundary rules unchanged. If a move needs a rule change, that is a design issue to raise, not something to absorb.

**Non-Goals:**
- Moving any of batch 2 (feed, edit, gear, segment, settings) or batch 3 (profile, record, detail), or `activitylist` and `visibility`.
- Shrinking `:app-screens`' or `:app-presenters`' own dependencies beyond what the moves require.
- Moving `SessionPreviewSubUis` out of composeApp. It names UIs from several features, which only an app shell may depend on.

## Decisions

### One feature at a time, in an order that keeps the build green
Order: report, media, feedfilter, map, moderation, login. Report goes first because media's contracts hold `Reporting`. After each feature the whole build compiles and its tests pass, which keeps any breakage small and gives a natural commit boundary.

Alternative considered: moving all six in one step. Rejected because a single failure in 18 new modules is harder to localize, and the user commits by concern.

### `Reporting` stays with report; the shared helpers depend on `:feature-report-api`
`Reporting`, `ReportingEvent` and `BlockOffer` move with `ReportScreen` into `:feature-report-api`, as the foundation design decided (they're report's contract). `rememberReporting` (in `:app-common-presenter`) and `ReportingHost` (in `:app-common-ui`) stay where they are and gain a dependency on `:feature-report-api`. The boundary rules already allow a presenter or UI module to depend on any API module.

Alternative considered: moving `Reporting` and friends into `:app-common-api`. Rejected because then `:app-common-api` would describe one feature's sub-screen, and `ReportScreen` would be split across two modules.

The same applies to `:app-common-presenter`'s activity-list sink, which opens `MediaGalleryScreen`: it depends on `:feature-media-api`.

### Borrowed helpers move to `:app-common-presenter`, keeping their packages
`OwnRoute.kt` (`app.detail`) and `ActivityTypes.kt` (`app.record`) move to `:app-common-presenter` under their current packages. This follows how the foundation moved `Reporting.kt` (`app.report`) and `DetailsForm.kt` (`app.edit`). Their `internal` declarations become public. No importer changes.

Alternatives considered:
- Moving them into map and feedfilter. Rejected: detail and record still need them, and the boundary rules forbid reaching into another feature's presenter.
- Copying them. Rejected: that breaks "Shared code has one home".

### Layer modules declare the `-api` modules they use
- `:app-screens` adds `api` dependencies on `:feature-report-api` and `:feature-feedfilter-api`, because its States expose those types.
- `:app-presenters` adds `implementation` dependencies on the map, media and moderation APIs.
- `:app-common-presenter` and `:app-common-ui` add the API modules named above.
- `:app-fixtures` adds all six APIs.
- composeApp adds all six APIs, and webApp and desktopApp add each feature's presenter and UI for Metro aggregation, as for search.
- `androidApp`'s screenshot tests add the six UI modules.

There are no cycles: every new `-api` module depends only on `:app-common-api` (and media's on report's). Nothing in them refers back to `:app-screens`.

Alternative considered: letting `:app-screens` `api`-export all six so every layer module sees them. Rejected because explicit edges show what still depends on what, which is the point of the move.

### Tests move with their feature; shared fixtures go to `:app-common-presenter-testing`
These move to each feature's presenter `jvmTest`, keeping the flat `app` package their siblings use:
- `LoginPresenterTest`
- `RouteMapPresenterTest`
- `MediaGalleryPresenterTest`
- `ReportPresenterTest`
- `ModerationPresenterTest`
- `FeedFilterPresenterTest` and `TypeSelectionTest`

Several dependencies on `:app-presenters`' test sources are cut:
- `RouteMapPresenterTest` and `MediaGalleryPresenterTest` extend `ActivityDetailTestBase` (found while applying). Most of that base is feature-neutral: a mock AppView and PDS serving one activity, its comments, track and source, plus `repos()` building the data repositories over it. That part moves to an `ActivityTestBase` in `:app-common-presenter-testing`, which gains `:core-data` and Ktor's mock engine. `ActivityDetailTestBase` extends it and keeps only what drives `ActivityDetailPresenter`. Map and media then use `ActivityTestBase`.
- `RouteMapPresenterTest.theDetailMapOpensTheEnlargedMap` drives `ActivityDetailPresenter`, so it moves into `ActivityDetailRouteTest`.
- `ActivityDetailModerationTest.aBlurredPhotoRevealsBeforeItZooms` and `theAthletesOwnTakenDownActivityShowsPlaceholders` drive only `MediaGalleryPresenter`, so they move into `MediaGalleryPresenterTest` (also found while applying).
- `RouteMapPresenterTest` reads `ActivityDetailRouteTest.SERVED_POLYLINE` and `SERVED_ROUTE`. Those constants move to a `TestRoutes` object in `:app-common-presenter-testing`, and both tests use it.
- `ModerationPresenterTest.settingsOffersModerationOnlyToModerators` tests `SettingsPresenter` through `SessionTestFixture`. It moves into `SettingsPresenterTest`, and `SessionTestFixture` stays in `:app-presenters`.

Tests that span features stay where they are:
- `ActivityDetailModerationTest`, which uses media, report and moderation types through their APIs;
- `FeedPresenterTest` and `FeedQueryTest`.

Alternative considered: giving feature presenter tests a `jvmTest` dependency on `:app-presenters`. Rejected: it couples every moved feature's tests to the module that's being emptied.

## Risks / Trade-offs

- **[Build cost]** 18 more modules, each compiling for 5 targets. The pilot's 9 cost 9% cold on GitHub Actions. → Measure `check` locally and on CI after the move, and record it next to the pilot's numbers. If CI time or memory crosses its limits (45-minute timeout, 16 GB runner), stop before batch 2 and consider the plan's fallback (api + impl per feature).
- **[Login on `AppScope`]** `LoginPresenter` and `LoginUi` contribute to `AppScope`, which `AppGraph` builds before sign-in, not to `SessionScope`. → No convention change is needed, since scope comes from the `@CircuitInject` annotation. Verify by signing out and back in on one platform.
- **[Public helpers]** Making `rememberOwnRoute`, `shownRoute`, `quickActivityTypes` and `ActivityTypeSaver` public widens their visibility to every module. → It's the same trade the foundation made for `uiErrorOf` and the other shared helpers. Their packages still say where they came from.
- **[iOS framework]** Six more modules reach the `ComposeApp` framework transitively. → The iOS `xcodebuild` from CLAUDE.md runs as part of the final check.
