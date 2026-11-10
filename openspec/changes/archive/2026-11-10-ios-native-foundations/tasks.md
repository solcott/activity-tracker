# Tasks

## 1. Probes that gate the design (throwaway code, not merged)

- [x] 1.1 Catalog symbols (design decision 1.1): turn `STRING_CATALOG_GENERATE_SYMBOLS` on for the app target with the existing 20-string catalog and use `login_sign_in`, `login_left` and `error_with_reason` from a throwaway Swift file through whatever Xcode generates. Verify the build passes, read the generated symbols (names, arguments), and confirm `Text` and `String(localized:)` take them and show the right text in a unit test. If they don't, record that the generator writes a Swift file of names.
- [x] 1.2 Plurals and percent signs (1.2, 1.3): add throwaway entries to the catalog by hand in the shape the generator would write (a plural selected by argument 1 with its text's placeholders from 2; a no-argument entry with a `%`; an entry with an argument followed by `%%`). Verify in a unit test that quantities 0, 1 and 3 read "other", "one", "other" with a second argument intact, and that both percent entries read one `%`. If the plural doesn't select, record the fallback.
- [x] 1.3 The whole catalog (1.4): write the 2,026 strings to a scratch catalog with a script and build with symbols on. Verify the app builds, and record clean and one-line-Swift build times and the app's size against the same without it. Stop and report if the one-line rebuild grows by more than 15 seconds.
- [x] 1.4 A drawable as a template image (1.5): convert `directions_run.xml` to an SVG image set by hand and draw it at 16, 24 and 48 points, tinted, in a throwaway view. Verify by a simulator screenshot that it is sharp and tinted.
- [x] 1.5 Fixtures and snapshots (1.6): export `:app-fixtures` from `:iosExport`, read `LoginSamples` from a unit test, and note what the stub check lists for it; add swift-snapshot-testing to the test target and snapshot `LoginView` for one sample, light and dark. Verify the first run records, a second passes, and a deliberate padding change fails.
- [x] 1.6 Remove the throwaway code and write the results into `openspec/explorations/ios-native-ui.md` under a "Phase 3 probes" heading, with any design decision they changed updated in `design.md`. Verify `git status` shows only those files plus what a later task keeps (the package reference, the build setting).

## 2. Text chosen in Kotlin (no behaviour change)

- [x] 2.1 `AppText` in `:app-common-api` (design decision 3): a named string with arguments and an optional fallback, a plural with its quantity, and text as written, `@Immutable` and free of what `checkSwiftExportSources` refuses. Verify `:app-common-api:check` passes and a `commonTest` covers equality and nesting.
- [x] 2.2 Resolve `AppText` in `:app-common-ui` (a composable returning the string) through `Res.allStringResources` and `Res.allPluralStringResources`. Verify a `jvmTest`: a named string with arguments, a plural for 1 and 3, a missing name giving its fallback and, without one, the name.
- [x] 2.3 Move the unit formatters' rules to `:app-common-api` as functions of the value and a `UnitSystem` returning `AppText`, and make `Format`'s composables resolve them, signatures unchanged. Verify `commonTest`s of each formatter in both unit systems and for missing values, and that `:app-common-ui:jvmTest` passes unchanged.
- [x] 2.4 Move the labels of `Labels.kt` the same way (sport, activity type, dive alert, sensor, sport category, handle, gender, detail group, visibility scope). Verify `commonTest`s for a known and an unknown token of each open set.
- [x] 2.5 Move the error text: the kind's string name by rule with the irregular one listed, the reason's name from `FailureReason`, joined by `error_with_reason`; `errorText` and `failureText` resolve it. Verify `commonTest`s (with and without a reason) and that `ErrorText.kt` no longer holds the 112-case table.
- [x] 2.6 Move which icon a sport gets to a table of drawable names in `:app-common-api`; `SportIcons.kt` resolves a name through `Res.allDrawableResources`. Verify every existing caller compiles unchanged.
- [x] 2.7 The names test in `:app-common-ui` `jvmTest` (design decision 3): every `UiError.Kind`, every `FailureReason`, each formatter's output in both unit systems, each closed label and every sport icon name exists in the generated resources. Verify it fails when one kind's rule is temporarily broken, naming the missing string.
- [x] 2.8 Verify nothing shown changed: `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` passes with no reference changed, and `:composeApp:jvmTest` passes.

## 3. Generated iOS resources

- [x] 3.1 The catalog generator in `build-logic` (design decision 2), from `IosStrings.kt`: strings, plurals and the string array, specifier and percent conversion, Android escapes, entries sorted, Xcode's JSON layout. Verify unit tests with real snippets: a plain string, positional `%1$s` and `%2$d`, a literal `%` with and without arguments, an apostrophe escape, an `&amp;`, a plural with one and with two arguments, the array.
- [x] 3.2 The `AppIcon` reader and Swift writer (design decision 4): each entry's name, SF Symbol and filled symbol to `AppIcon.swift`. Verify unit tests with real entries (one line, wrapped over lines, with and without a filled symbol) and that an unreadable entry fails naming it.
- [x] 3.3 The drawable converter (design decision 4): an Android vector to an SVG image set with template rendering and its vector kept, failing on an attribute it doesn't handle. Verify unit tests with a real single-path drawable and the one that isn't, and that all 82 convert.
- [x] 3.4 `generateIosResources` and `checkIosResources` (design decision 8), registered by the Swift export convention on `:iosExport`, the check in `check`; remove `checkIosStrings`. Verify with TestKit: generate then check passes; a changed shared string fails the check naming the task; a hand-edited catalog entry fails it.
- [x] 3.5 Run the generator and commit its outputs: the catalog replaces the hand-written one, `Design/Generated/AppIcon.swift`, `Generated/Drawables.xcassets`. Turn symbol generation on (or add the generated names file, per probe 1.1). Verify `./gradlew checkIosResources` passes and the `xcodebuild` build passes.
- [x] 3.6 Move the Swift that names strings (`LoginView`, `LoginText`, `FailureText`, `SessionView`'s tab titles) to the generated symbols, and the tabs' symbols to `AppIcon`. Verify the Swift tests pass, the sign-in screen and the tabs look as before on the simulator, and removing a used string from a scratch copy of the catalog fails the build at its use.

## 4. Text and icons in Swift

- [x] 4.1 Resolve `AppText` in Swift (named, plural, written; fallback; nested arguments), with the by-name lookup for names chosen at run time. Verify unit tests mirroring 2.2's cases, and that `FailureText` and `LoginText` are now thin over the shared error text.
- [x] 4.2 The stat formatters and labels from Swift: small Swift entry points over the Kotlin functions for the unit system in use. Verify unit tests: a distance in both unit systems, a missing value, a known and an unknown sport, a sub-sport, an error with and without a reason, each equal to the string the spec's scenario gives.
- [x] 4.3 `AppIconImage` and `SportIcon` views over the generated enum and asset catalog. Verify a unit test that every `AppIcon` symbol name and filled name is a symbol the system has, and every sport icon name the Kotlin table can return is an image in the asset catalog.

## 5. The design system

- [x] 5.1 Colours: the theme's like colour joins the accent pair in the asset catalog, with named accessors. Verify the values against `ui/theme` in light and dark.
- [x] 5.2 Buttons: `AppSecondaryButton`, `AppTextButton` and `AppIconButton` beside `AppButton`, each taking a catalog string, the icon button an `AppIcon` and a label. Verify previews compile and a unit test that the icon button's accessibility label is the one given.
- [x] 5.3 `AppTextField` gains supporting text and an error state. Verify on the simulator through a preview host or sign-in that nothing changed for a field with neither.
- [x] 5.4 `AppListRow` (title, summary, chevron or trailing control), `AppCard` and `AppProgress`. Verify previews compile; they are compared in 6.4.
- [x] 5.5 `ErrorMessage` (the shared error text in the error colour, Retry when given an action) and `ContentStateView` (design decision 5). Verify unit tests of the choice it makes (progress, failure, content, content with a refresh failure) from `ContentState` samples, apart from its drawing.

## 6. Samples, previews and snapshots

- [x] 6.1 Give `:app-fixtures` the Swift source check and rename `ProfileSamples.self`. Verify `./gradlew :app-fixtures:check` passes and the tests and screenshot previews that named it compile.
- [x] 6.2 Export `:app-fixtures` from `:iosExport` unless `CONFIGURATION` is `Release` (design decision 6), under a Swift module name of its own. Verify the Debug build exports it and passes the stub check, and a Release build of the app (`-configuration Release`) succeeds with no sample module in the generated Swift.
- [x] 6.3 Previews: `LoginView` in each of `LoginSamples`, and each design system piece, inside `#if DEBUG`. Verify the app builds in Debug and Release.
- [x] 6.4 Snapshot tests (design decision 7): swift-snapshot-testing on the test target, a helper that renders a view at a fixed size light and dark, the recorded OS written beside the references and the skip elsewhere; snapshots of sign-in in each sample and of each design system piece. Verify the tests pass twice running on the recording simulator, fail naming the view after a deliberate padding change, leave the reference untouched when they fail, and report as skipped on the iOS 18.4 simulator.
- [x] 6.5 Look at every recorded reference image, light and dark, and fix what looks wrong before keeping them. Verify by noting each image seen.

## 7. Docs, measurements and the whole change together

- [x] 7.1 Update `.agents/rules/compose-screens.md` and the `new-screen` skill (a native view names strings by symbol, builds from `Design`, ships previews and snapshots; text with a rule returns `AppText` from `:app-common-api`), `build-logic.md` (the generator, the check, what replaced `checkIosStrings`), `adaptive-ui.md` (the Swift design system's pieces and what has none) and AGENTS.md's commands and iOS paragraph. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.2 Measure as phases 1 and 2 did (generated Swift lines, debug dylib size, no-change build, one-line rebuild) and add the column to the roadmap's table. Verify the numbers are from this change's final state.
- [x] 7.3 Run `./gradlew check`, `./gradlew -p build-logic :convention:test` and the iOS `xcodebuild test`. Verify all pass, with no Android screenshot reference changed.
- [x] 7.4 Rewrite the roadmap's phase 3 line to what was built, add a "Phase 3 findings" section, and update its open items. Verify `openspec validate ios-native-foundations --strict` passes.

## Not verified

- 1.5: the probe showed a first run recording and a second passing. That a deliberate change fails the comparison was left to 6.4,
  where it is checked on the real tests.
- 5.2: the icon button sets the accessibility label it is given, but no test reads it back: a SwiftUI view's accessibility tree isn't
  reachable from a unit test without a UI test target. It is one line of the view.
- 6.3: the previews compile in Debug and are left out of Release. They were not opened in Xcode's canvas, which needs the app.
- 7.3: `./gradlew check` passed except `GearPresenterTest.unmountingEndsTheMountOnTheChosenDay`, which timed out once under the full
  run's load ("No value produced in 10s") and passed, with the module's other 47 tests, when rerun alone. This change doesn't touch
  gear.
- Nothing native was walked by hand beyond the app opening signed in on the iOS 26.0 simulator with its tabs and feed as before:
  sign-in and the design system are covered by their snapshots, which were looked at one by one, light and dark.

