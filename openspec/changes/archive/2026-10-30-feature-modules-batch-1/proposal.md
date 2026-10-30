# Proposal

## Why

`feature-modules-foundation` added the shared modules, the feature conventions and `checkModuleBoundaries`, and moved athlete search as a pilot. The other 17 feature packages still live in the layer modules (`:app-screens`, `:app-presenters`, `composeApp`), where nothing stops one feature from reaching into another. This change moves the first batch: the six features other features depend on least. Most of them are endpoints or sub-screens embedded by key, so moving them first gives the later, larger features `-api` modules to depend on.

## What Changes

- **Six features move to feature modules**, each into `feature/<name>/{api,presenter,ui}` with its presenter tests:
  - `login`: `LoginScreen`, `LoginPresenter`, `LoginUi`;
  - `map`: `RouteMapScreen`, `RouteMapPresenter`, `RouteMapUi`;
  - `media`: `MediaGalleryScreen`, `MediaGalleryPresenter`, `MediaGalleryUi`;
  - `report`: `ReportScreen` (with `Reporting`, `ReportingEvent`, `BlockOffer`), `ReportPresenter`, `ReportUi`;
  - `moderation`: `ModerationScreen`, `ModerationPresenter`, `ModerationUi`;
  - `feedfilter`: `FeedFilterScreen` (with `FeedFilter`, `DateRange`, `Selection`), `FeedFilterPresenter` and `TypeSelection`, `FeedFilterUi`.
- **Two helpers that features borrow from other features move to `:app-common-presenter`:**
  - `OwnRoute.kt` (`rememberOwnRoute`, `shownRoute`) from detail, which map also uses;
  - `ActivityTypes.kt` (`quickActivityTypes`, `ActivityTypeSaver`) from record, which feedfilter also uses.
- **Shared test fixtures:** the route constants that map's and detail's presenter tests share (`SERVED_POLYLINE`, `SERVED_ROUTE`) move to `:app-common-presenter-testing`. The one test in `ModerationPresenterTest` that exercises `SettingsPresenter` moves to the settings tests, which stay in `:app-presenters`.
- **Modules that used these features through `:app-screens`** depend on the new `-api` modules instead:
  - `:app-screens` (detail, profile and feed States hold `Reporting`, `FeedFilter` and `FeedFilterScreen`);
  - `:app-presenters` (detail navigates to the map and gallery, settings to moderation);
  - `:app-common-presenter` (`rememberReporting`, the activity-list sink's gallery navigation);
  - `:app-common-ui` (`ReportingHost`);
  - `:app-fixtures`, `composeApp`, `webApp`, `desktopApp` and `androidApp`'s screenshot tests.
- **CLAUDE.md** and the `compose-screens` rule list the moved features.

App behavior doesn't change: the same screens, strings and screenshots.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
<!-- None. dev/module-boundaries already states the rules these moves follow ("Feature module kinds", "Features meet only through API modules", "Shared code has one home", "Moved screens stay covered", "Moving code does not change the app"). This change applies them to six more features without changing a requirement, so it sets skip_specs. -->

## Impact

- **Gradle:** `settings.gradle.kts` gains 18 modules (6 features × 3). No convention plugin or boundary-rule change is expected.
- **Moved code:** about 270 lines of contracts, 520 of presenters and 860 of UI, plus 7 presenter test files (about 1,000 lines). Packages stay the same, so imports in other files don't change; only build scripts do.
- **`internal` helpers become public** where they now cross a module: `rememberOwnRoute`, `shownRoute`, `quickActivityTypes`, `ActivityTypeSaver`, and any feature-internal declarations a moved test reads.
- **CI:** each module compiles for 5 targets. The pilot's 9 modules added 9% to a cold `check` on GitHub Actions. This change measures again with 18 more modules before batch 2.
- **No changes** to lexicons, the AppView, data-layer code or the module rules.
