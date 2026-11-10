# Design

## Context

After phase 2 the iOS app is a SwiftUI shell over the presenter host. One screen, sign-in, is native; the other 50 render through their Compose UI in a view controller each. What the native side has to draw with is thin:

- `iosApp/iosApp/Localizable.xcstrings`, hand-written, holds 20 of the 2,026 strings in `ui/resources/.../values/strings.xml` (which also has 68 plurals and one string array). `checkIosStrings` compares those entries with the shared strings by name.
- Text that follows a rule is built in composables that read Compose resources: `Format` (11 unit formatters reading `LocalUnitSystem`), `Labels.kt` (ten labels, several looked up by token with a fallback), `ErrorText.kt` (a 112-case table from `UiError.Kind` to a string, regular but for one name, plus the reason). `:app-common-ui` holds them, and it isn't exported to Swift; 135 composables across the UI modules return text this way. Phase 2 moved one such rule, `FailureReason`, to `:app-common-api`.
- Icons: `AppIcon` (`:ui-designsystem`) carries each icon's SF Symbol name beside its Material and Cupertino forms, in a Compose module. Sports draw one of 82 vendored Material Symbols drawables (Android vector XML, one path each, a 960 viewport), chosen by `SPORT_ICONS`, a map typed with `DrawableResource`.
- Design system: `AppButton`, `AppTextField` with `TextFieldModel`, two colours (`AccentColor`, `OnAccentColor`).
- Samples live in `:app-fixtures` (contracts and lexicon only, no Compose imports), not exported to Swift. The Swift tests are 33 unit tests; nothing renders a view.
- `ContentState` and `PagedState` reach Swift with their type parameter erased (`data` is any Kotlin object), and `LoadStatus` as a sealed type.

Xcode 27.1 is installed locally and the CI runner is `macos-26`; both have `STRING_CATALOG_GENERATE_SYMBOLS`.

## Goals / Non-Goals

**Goals:**

- Every shared string is available to a native view under its shared name, with nothing typed twice and nothing able to drift.
- A rule about text is written once: the unit formatters, the labels and the error text return a value both UIs resolve.
- A sport's icon and an app icon are the same on every platform.
- A native screen builds from a small set of controls that already hold the app's look and its loading behaviour.
- A native view can be seen and compared without a session, from the samples every other UI test uses.
- Android, desktop and web render exactly as before: no screenshot reference changes.

**Non-Goals:**

- Any other native screen.
- Moving every composable that builds text. Dates and times, the dive and stat tables and feature-specific text move with their feature in phase 4, onto the type this phase adds.
- Translations, right-to-left mirroring, a Dynamic Type audit.
- SwiftLint and swift-format.
- A Swift equivalent of every `ui/adaptive` wrapper. Sheets, menus, alerts, date pickers and segmented controls are the system's own in SwiftUI and need none.

## Decisions

### 1. Probe first

Six questions decide the shape of what follows. Each is answered with throwaway code before the work that depends on it, and the answers go in the roadmap.

1. **Catalog symbols.** With `STRING_CATALOG_GENERATE_SYMBOLS` on, what Xcode generates for `login_sign_in`, for `login_left` (one `%1$@`) and for `error_with_reason` (two): the symbol's name, its arguments, and whether `Text` and `String(localized:)` take it. Fallback: the generator also writes a Swift file of names.
2. **A plural whose quantity isn't an argument.** Compose's `pluralStringResource(res, quantity, args…)` chooses the form by a quantity that is separate from the placeholders. Whether a catalog entry whose text is one substitution on argument 1, with the forms' own placeholders shifted to start at 2, picks "one" and "other" correctly from Swift. Fallback: the generator writes one entry per form and Swift chooses between them, which is right for English only and is recorded as a limit.
3. **Percent signs.** Compose replaces only `%1$s` and `%1$d`, so a lone `%` in a shared string is literal. Whether a no-argument entry shows `%` as written and an entry with arguments needs `%%`.
4. **The whole catalog.** 2,026 entries and their symbols: the time they add to a clean and to a one-line Swift build, and to the app's size. Limit: stop and report if a one-line Swift rebuild grows by more than 15 seconds.
5. **A drawable as a template image.** One vendored drawable converted to SVG in an asset catalog: that it draws at 16, 24 and 48 points, takes a tint, and stays sharp (vector kept).
6. **Fixtures and snapshots.** `:app-fixtures` exported: the Swift it generates, whether a sample state can be read from Swift, and what the stub check lists. swift-snapshot-testing on the test target under Swift Testing: that a recorded SwiftUI view compares equal on a second run, light and dark.

### 2. The catalog is generated, committed and checked

A Gradle task, `generateIosResources`, writes `iosApp/iosApp/Localizable.xcstrings` from `strings.xml`. The file is committed, and `checkIosResources`, in `check`, fails when the committed file differs from what the task would write, naming the task to run.

Committed, not written during the build: Xcode fixes the files of a synchronized folder before its build phases run, so a catalog created by a build phase is missing from the first build after a checkout, and previews need it without a build phase at all. The check keeps `strings.xml` the single source, on Linux too, since it only reads and writes text.

The generator, in `build-logic` beside the phase 2 check it replaces:

- **A string** becomes an entry under its name with `extractionState` `manual` (so Xcode neither removes it as unused nor rewrites it) and its English text. Every placeholder becomes `%@`, `%d` included, keeping positions: an argument reaches the catalog as text that shared code has already formatted. A `%lld` would have Foundation group a number's digits by its own rule, where `ui/number-format` wants the shared `formatWhole` to decide, and would make a year read "2,026". It also lets a string chosen at run time be filled from a list of texts. In an entry with placeholders a literal `%` becomes `%%` (probe 3). Android's backslash escapes are undone; the XML parser has already undone the entities.
- **A plural** becomes one entry whose text is a single substitution on argument 1, the quantity, with the catalog's plural variations as its forms and each form's own placeholders numbered from 2 (probe 2). That is Compose's `pluralStringResource(res, quantity, args…)`, where the quantity is apart from the arguments.
- **The string array** becomes one entry per item, named with the array's name and the item's index.
- Entries are sorted by name and written in Xcode's own JSON layout, so opening the file in Xcode doesn't rewrite it.

Swift names a string by Xcode's generated symbol (probe 1), which makes a removed or renamed string a compile error where it is used. A string chosen at run time (decision 3) is looked up by name.

Alternative: keep a hand-written catalog and grow it per screen. Rejected: 2,000 chances to mistype, and the check could only ever say what had already drifted.

Alternative: read Compose resources from Swift. Rejected in phase 2 for the same reason as now: the reader is suspending, and Compose resources leave iOS in phase 5.

### 3. `AppText`: text chosen in Kotlin, resolved by each UI

A composable that builds text does two things: it decides which string and arguments (a rule), and it reads the resource (a platform matter). `AppText`, in `:app-common-api`, is the decision without the reading:

- a shared string's name with its arguments, each itself an `AppText`, and an optional fallback for a name that may not exist (an open enum's token);
- a plural's name with its quantity and arguments;
- text as written (a formatted number, a handle, an athlete's own words).

`:app-common-ui` resolves it with `Res.allStringResources` and `Res.allPluralStringResources`, which the labels already use for token lookups, in a composable, since reading a Compose resource needs a composition. For a UI that reads strings outside one, `AppText.resolve(source)` is written once in Kotlin over a two-method `TextSource` (a string by name with finished arguments, a plural by name and quantity), which Swift implements over the catalog. So the order of resolution, the nesting and the fallback are Kotlin's on iOS, and Swift only looks a name up and fills it. A name that isn't a string resolves to its fallback, or to the name itself, on both. The fallback is itself an `AppText`, because an unknown sensor falls back to another string and an unknown sport to written text.

What moves onto it in this phase, each keeping its Compose signature so no caller changes:

- **`Format`'s unit formatters** become functions of the value and a `UnitSystem` in `:app-common-api`; `Format.distance(meters)` reads `LocalUnitSystem` and resolves the result. `Format.duration` is already plain text.
- **`Labels.kt`**: the sport, activity type, dive alert, sensor type, sport category, handle, gender and detail group labels. `sensorLabel`, which joins a sensor's type to its maker and model, stays a composable over the moved sensor type: `AppText` has no joined form, and it isn't worth one for a single caller.
- **The error text**: what failed is `error_` and the kind's name in snake case, with the one irregular name listed; why is `FailureReason`'s name; `error_with_reason` joins them.

The names are no longer checked by the compiler on the Kotlin side, where they were typed resources. A JVM test in `:app-common-ui` takes that over: every `UiError.Kind`, every `FailureReason`, each formatter in both unit systems and each closed label resolves to an existing resource.

`:app-common-api` already depends on `:core-data`, which has the units, the conversions, `formatDecimal` and the sport catalog. Formatted numbers keep coming from `formatDecimal` and `formatWhole`, so `ui/number-format` holds on iOS without a line of Swift.

As built, one more rule moved than planned: sign-in's failure text and its message after leaving, which `LoginUi` and the Swift `LoginText` each chose, are `LoginTexts` in `:feature-login-api`. A feature's own rules go in its `-api`, on the same type.

Alternative: leave Compose as it is and write each rule again in Swift. Rejected: it is the duplication this phase exists to avoid.

Alternative: return finished strings from Kotlin on iOS by reading Compose resources there. Rejected with decision 2's second alternative.

### 4. Icons

**`AppIcon`.** The generator reads the Kotlin enum's entries (each names its SF Symbol, and a filled one where it differs) and writes `iosApp/iosApp/Design/Generated/AppIcon.swift`, committed and checked like the catalog. It reads the source text, since the enum's other fields are Compose types that can't be exported, and fails on an entry it can't read rather than skipping it. The Kotlin enum stays the source until Compose leaves iOS, because the Compose UI on iOS draws the same symbols.

**Drawables.** The generator converts each vendored drawable to an SVG image set in `iosApp/iosApp/Generated/Drawables.xcassets`: a template image that keeps its vector. An Android path's data is SVG path data, and all but one file is a single filled path; the converter handles what the files use and fails on an attribute it doesn't know. `autoMirrored` is ignored (out of scope).

**Which icon a sport gets** is a table from sport token to drawable name in `:app-common-api`, replacing the map typed with `DrawableResource`. Compose resolves a name through `Res.allDrawableResources`, Swift through the asset catalog, and the test of decision 3 holds every name to an existing drawable.

Alternative: SF Symbols' own sport figures on iOS. Rejected: they cover a fraction of the sports, and a sport would look different in the feed on each platform.

### 5. The Swift design system

`iosApp/iosApp/Design` holds what a native screen builds from, each piece small and each with a preview and a snapshot:

- **Colours**: the theme's additions to the system palette as catalog colours (accent, on-accent, the amber used for likes). Backgrounds, labels, separators and the error red are the system's.
- **Buttons**: `AppButton` (filled), `AppSecondaryButton` (tinted), `AppTextButton` (plain), `AppIconButton` (an `AppIcon` with an accessibility label).
- **`AppTextField`**: as built, plus supporting text and an error state.
- **`AppListRow`**: a title, an optional summary of its value, and a chevron or a trailing control, for the system's `List` and `Section`, which already draw the inset grouped look.
- **`AppCard`**: a group of content on the grouped background.
- **`AppProgress`**, **`AppIconImage`**, **`SportIcon`**.
- **`ErrorMessage`**: an error's text in the error colour, with Retry when given an action.
- **`ContentStateView`**: `ui/content-loading`'s layout for a `ContentState`: progress before the first answer, the failure with Retry when nothing has loaded, and the content with a refresh failure above it once something has. It takes the loaded value's Swift type and casts the erased `data`.

As built: which of the three `ContentStateView` shows is not decided in Swift. `ContentState` reaches Swift as `data`, `origin` and `status` only (the library's `hasLoaded`, `errorOrNull` and `isLoading` are extensions the export doesn't carry), so the rule is `ContentShown.of(state, errorKind)` in `:app-common-api`: progress, a failure with its `UiError`, or content with a refresh error or a running refresh. The Compose `ContentStateLayout` now asks it too. `AppListRow`'s opening form takes `opens:` written out, because a trailing closure there read as either the action or the row's control. Stats come from the environment (`\.stats`), which `SessionView` fills from a new `SessionHost.shownUnits`, the athlete's unit system wrapped for Swift like the host's other flows.

No Swift wrapper is written for what SwiftUI already gives the platform's form of: sheets, menus, alerts, confirmation dialogs, date pickers, toggles, segmented pickers, pull to refresh.

### 6. Samples in Swift, in debug builds only

`:iosExport` exports `:app-fixtures` unless Xcode's `CONFIGURATION` is `Release`, read as a Gradle provider when the build phase runs the export. Swift that names a sample is inside `#if DEBUG`: previews and the test target. A release build therefore neither carries the samples nor compiles against them.

`:app-fixtures` gains the Swift source check that the contracts have. It has one finding today: `ProfileSamples.self`, renamed.

Alternative: write samples again in Swift. Rejected: two sets drift, and the point of the fixtures is one set for every renderer.

### 7. Snapshot tests

swift-snapshot-testing joins the project beside the FIT SDK, on the test target only. A snapshot test renders a view at a fixed size from a sample, in light and dark, and compares it with a reference image under `iosApp/iosAppTests/__Snapshots__`.

A reference is true for one simulator OS: fonts, materials and control shapes change between releases. The OS the references were recorded on is written beside them, and the comparisons run only on that OS; anywhere else they are skipped with a message, as the Android references are skipped on Linux. CI's simulator is the newest the runner has, so CI compares only when that happens to match. References change only when rerecorded on request, as `dev/screenshot-tests` says of Android's.

Covered now: sign-in in each of `LoginSamples`, and each design system piece.

As built: a view is drawn in the app's own window, at three pixels a point whatever the simulator, with time stopped in its layer. Off screen a `List`'s rows drew apart, as separate capsules, and a spinner was at a different turn on each run. The recorded OS is iOS 26.0, a constant beside the helper, and the suites are enabled only where the simulator's major and minor version match it. swift-snapshot-testing records a missing reference and fails that run, and never rewrites one that exists, which is the behaviour the spec asks for without any setting.

### 8. What `checkIosStrings` becomes

`checkIosResources` covers the three generated outputs (the catalog, `AppIcon.swift`, the drawables' asset catalog) with one comparison each, and `generateIosResources` writes all three. Both are registered where the phase 2 check was, on `:iosExport` by the Swift export convention, and the check stays in `check`.

## Risks / Trade-offs

- **The catalog's symbols slow the Swift build** → probe 4 measures it against a limit; the fallback is names in a generated Swift file, which costs one small file.
- **The separate-quantity plural doesn't select** → probe 2; the fallback is right for English and recorded as a limit before any translation.
- **Names replace typed resources in Kotlin** → the resolution test fails for any name that isn't a resource, and it runs in `check`.
- **A generated file is edited by hand, or Xcode rewrites it** → the check fails and names the task; entries are `manual` and the layout is Xcode's own.
- **Snapshots are flaky across machines** → compared with a perceptual tolerance, on one OS version only.
- **The design system is built ahead of its screens** and some pieces will change shape on first use → kept to pieces with a rule behind them, each small; the first phase 4 screens are expected to adjust them.
- **Debug and release export different Kotlin** → the difference is one module that only `#if DEBUG` Swift names; CI builds Debug, so a release-only break would show first in an archive build, which is noted in the open items.

## Migration Plan

Each task group leaves the app working. The Kotlin move (group 2) lands first and changes nothing visible; the generated catalog then replaces the hand-written one under the same names. Rollback is reverting the change: nothing persists.

## Open Questions

- Whether Xcode's symbol names for snake-case keys read well enough to use directly: they do (`login_sign_in` is `.loginSignIn`).
- Whether `ContentStateView` needs a Kotlin-side helper: it does, `ContentShown` (decision 5).
