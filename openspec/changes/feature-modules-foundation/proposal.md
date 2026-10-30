# Proposal

## Why

`:app-screens`, `:app-presenters` and `:composeApp` are each split by layer, and each holds all 18 feature packages. That keeps presenters and UIs apart, but it doesn't isolate features from each other. Any feature can reach into any other's internals, and dependency cycles go unnoticed today: record and detail use each other in the UI, and detail and record in the presenters. Pieces several features use, such as `uiErrorOf`, `rememberReporting`, the activity-page widgets and the visibility types, sit inside whichever feature wrote them first.

Moving to feature modules makes those boundaries explicit, and it means each shared piece is extracted once into a module everyone reuses. This change lays that foundation and proves it on one feature. The other features follow in later changes.

## What Changes

- **Five shared modules** are extracted, so code that more than one feature uses lives in exactly one place:
  - `:ui-resources`: every Compose string and drawable, moved out of `composeApp`.
  - `:ui-designsystem`: the `ui/adaptive` wrappers, theme, charts, `AppPreview`, test tags, and generic widgets such as paged lists and content-state layouts.
  - `:app-common-api`: contract types shared across features, such as `UiError`, `ShownRoute`, `RouteEnds`, the visibility fields and events, `Revealed`, `Reporting`, `ActivityListEvent`, `MediaItem`, and the details-form fields.
  - `:app-common-presenter`: shared presenter helpers, such as `SingleFlight`, `ContentStates`, `FormSeed`, `uiErrorOf`, and the `remember*` helpers for reporting, revealing, visibility, activity-list events, details forms and follower approval.
  - `:app-common-ui`: domain widgets several features render, such as the activity card, map, route preview, stats, visibility pickers, avatars, media rows, and the activity-page widgets that record's import preview reuses.
- **Three convention plugins:** `activitytracker.feature.api`, `activitytracker.feature.presenter` and `activitytracker.feature.ui`. Each sets up its kind of feature module with the standard dependencies, so a feature module's build script stays a few lines long.
- **A module dependency check runs in `./gradlew check`.** It fails the build when:
  - a presenter module depends on a UI module, or the reverse;
  - a feature depends on another feature by any route other than that feature's `-api` module;
  - an `-api` module depends on anything beyond shared contracts and other `-api` modules.
- **Athlete search is the pilot:** it moves to `feature/search/{api,presenter,ui}` (`:feature-search-api`, `:feature-search-presenter`, `:feature-search-ui`), along with its tests.
- **The screenshot coverage and content-loading conformance tests** scan the new feature modules as well as `composeApp` and `:app-screens`. Moved screens stay covered.
- `:app-screens`, `:app-presenters` and `:composeApp` remain the home of every feature that hasn't moved yet. They depend on the shared modules, so nothing is duplicated while the move is in progress.
- **Docs and scaffolding:**
  - CLAUDE.md's architecture and commands sections, plus the `compose-screens` and `build-logic` rules, describe the feature-module layout.
  - The `new-screen` skill scaffolds into it.

App behavior doesn't change: the same screens, strings and screenshots.

## Capabilities

### New Capabilities
- `dev/module-boundaries`: how client UI code is divided into shared and feature modules. Covers which modules may depend on which, how the build enforces it, and that shared code has one home.

### Modified Capabilities
<!-- None. The screenshot-test and UI-test-harness requirements are unchanged; their tests scan more modules to keep meeting them. -->

## Impact

- **Gradle:** `settings.gradle.kts` gains 8 modules (5 shared, 3 search). `build-logic/convention` gains 3 plugins and the dependency check, with TestKit tests.
- **`composeApp`** loses its compose resources and most of `ui/`. It keeps `App.kt`, `di/`, `nav/`, platform glue and the UIs of unmoved features. Every file that imports a moved symbol needs its imports updated.
- **`:app-screens`, `:app-presenters` and `:app-fixtures`** lose the moved shared types and helpers, and depend on the shared modules instead.
- **Other modules:** `androidApp`'s screenshot tests, `desktopApp` (window title string) and `webApp` depend on the new modules where they used moved code. The iOS `ComposeApp` framework picks the new modules up transitively.
- **CI:** each module compiles for 5 targets. This change measures `check` time and memory on the GitHub runner before later changes add the other ~45 feature modules.
- **No changes** to lexicons, the AppView or data-layer code.
