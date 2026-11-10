# Tasks

## 1. The text field module, proven before anything depends on it

- [x] 1.1 Create `:core-textfield` (`core/textfield`, `activitytracker.kmp.library` with the Compose compiler plugin) and add it to `settings.gradle.kts`; `compose-foundation` is `api` in `nonAppleMain` and `implementation` in `iosMain`. Verify `./gradlew :core-textfield:assemble` configures and builds every target.
- [x] 1.2 Declare the `expect` API of design decision 2 in `commonMain` and its `nonAppleMain` actuals (the alias, and four functions calling Compose's). Verify a `jvmTest` shows `textFieldStateOf("a")` is a Compose `TextFieldState`, and that set, clear and Compose's `edit { }` all act on the same text.
- [x] 1.3 Write the iOS `actual class` of design decision 3 (`text`, `value`, `composeState`, the two functions, `rememberTextFieldState` saving through Compose's saver). Verify with `:core-textfield:iosSimulatorArm64Test`: setting `value` changes `text`; `clearText` empties `value`; setting the same `value` leaves the Compose state's selection where it was.
- [x] 1.4 Add `:core-textfield` to `:iosExport` as an `api` dependency with the Swift module name `CoreTextfield`, build with the `xcodebuild` command in AGENTS.md, and compile a throwaway Swift file that makes a state, sets `value` and reads it back. Verify the build passes and the generated `CoreTextfield.swift` shows `TextFieldState` as a class with a settable `value`; record in the roadmap's spike findings how `text` and `rememberTextFieldState` came out. If the alias doesn't resolve from another module's `nonAppleMain` in 2.1, move the actuals to the per-target source sets here.

## 2. Move every caller to the app-owned type

- [x] 2.1 `:ui-designsystem`: depend on `:core-textfield` (`api`); `AppTextField`'s expect and non-Apple actual take the app type; the iOS actual unwraps `composeState`; `ReadOnlyTextFieldState.kt` and `MenuField.kt` use `textFieldStateOf`. Verify `:ui-designsystem:check` passes and `:ui-designsystem:compileKotlinIosSimulatorArm64` compiles.
- [x] 2.2 `:app-common-api` takes `:core-textfield` as `api` and drops `compose-foundation`; `FeatureApiConventionPlugin` drops it too. Change imports in the 25 contract files and `TextFieldState()` defaults to `textFieldStateOf()`. Update `FeatureConventionPluginsTest` for the dependency that left. Verify `./gradlew -p build-logic :convention:test` passes and every `-api` module compiles for JVM and `iosSimulatorArm64`.
- [x] 2.3 Change imports in the presenters (`:app-common-presenter` and the 22 feature presenter files) and their `TextFieldState(...)` calls in common code. Verify no presenter file changes anything but imports and constructor calls (`git diff --stat` and a read of the diff), and that every `-presenter` module's `jvmTest` passes with its tests untouched.
- [x] 2.4 Change `:app-fixtures` (54 constructor calls in 18 files), the 9 UI files in `feature/*/ui` and `:app-common-ui`, `composeApp`'s `SessionPreviewSubUis.kt` and `androidApp`'s screenshot file. Verify `:app-fixtures:check`, `:composeApp:jvmTest` and `:androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference image changed.
- [x] 2.5 Add the `UseAppTextFieldState` rule to `:detekt-rules` (design decision 4) for Compose's `TextFieldState`, `rememberTextFieldState`, `setTextAndPlaceCursorAtEnd` and `clearText`, turned on in `config/detekt/detekt.yml`, skipping `*Test.kt` files and suppressed by name in `:core-textfield`'s actuals and the design system's iOS field, with the reason in the message. Verify `./gradlew :detekt-rules:test` and `./gradlew detekt` pass, and that detekt fails with that message when one presenter's import is put back.
- [x] 2.6 Update the `UseTextFieldState` rule's message and KDoc, `.agents/rules/compose-screens.md`, the `new-screen` skill and AGENTS.md's screens paragraph to name the app-owned type and `textFieldStateOf`. Verify `./gradlew :detekt-rules:test` and `scripts/check-agent-sync.sh` pass.
- [x] 2.7 Build and run the iOS app in the simulator against `scripts/dev.sh --seed alice`. Verify by hand, on the sign-in handle, a comment draft and a search query: typing, moving the cursor mid-text, selecting and pasting, the presenter clearing the comment after it sends, and the text still there after the app goes to the background and returns.

## 3. The two other contract fixes

- [x] 3.1 Move `LocalSettingsPane` from `:app-common-api` to `:app-common-ui`, keeping its package. Verify `:feature-settings-ui:jvmTest` and `composeApp`'s `SettingsListDetailDecorationTest` pass with no import changed, and `:app-common-api` no longer imports `compositionLocalOf`.
- [x] 3.2 Rename `PlaceNameFields.self` to `viewerDid` at its declaration and every use. Verify `grep` finds no `.self` or `self =` on a `PlaceNameFields` left, and the place-name presenter and UI tests pass.

## 4. The guards

- [x] 4.1 Add `CheckSwiftExportSources` to `build-logic` (design decision 8): registered by the feature API convention, applied to `:app-common-api` and `:iosShell`, wired into `check`. Verify with TestKit: a Compose import fails with file and line, a property named `self` fails, a parameter named `self` fails, a `Pair` field fails, `@Immutable` and `@Stable` imports pass, a comment mentioning `androidx.compose` passes.
- [x] 4.2 Run `./gradlew checkSwiftExportSources` on the repository. Verify it passes with no exclusions, which it can only do after groups 2 and 3.
- [x] 4.3 Write the stub finder as a plain class in `build-logic` that takes a generated Swift file's text and returns the stubbed declarations. Verify with unit tests on snippets copied from `iosExport/build/SwiftExport`: a stubbed function, getter and initialiser are found by name; an object's `private override init()` and an enum initialiser's `default:` are not.
- [x] 4.4 Add the `checkSwiftExportStubs` task to `:iosExport`: inputs are `build/SwiftExport`, the Swift module names to fail on and the allow-list with reasons; it writes other project modules' stubs to `build/reports/swift-export/stubs.txt`; it fails when `:iosShell`'s Swift has no `private override init()` stub at all. Verify with TestKit against a fixture directory: a stub in a failing module fails naming the declaration, an allow-listed one passes, one in another module is only reported.
- [x] 4.5 Change the Xcode build phase to run `:iosExport:embedSwiftExportForXcode :iosExport:checkSwiftExportStubs`, the check ordered after the export. Verify `xcodebuild` passes on the current export, and fails naming the declaration when `IosApp` temporarily gains a function returning a type from a module the export hides (`:core-location`'s `GeoPoint`; a `:composeApp` type is not a stub, because a module that is neither named nor hidden is exported as far as it is named).
- [x] 4.6 Describe both guards in `.agents/rules/build-logic.md` and replace AGENTS.md's "read the generated Swift for `fatalError()`" with what the build now does. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.

## 5. Export every contract module

- [x] 5.1 In `iosExport/build.gradle.kts`, add `:app-common-api` and every `-api` project as `api` dependencies from the root project's module names, each with its derived Swift module name, and pass those names to `checkSwiftExportStubs`. Verify `./gradlew :iosExport:dependencies --configuration iosSimulatorArm64MainApi` (or the equivalent) lists all 21 project modules.
- [x] 5.2 Set the hidden modules by design decision 6's rule and build with `xcodebuild` until the export compiles. For each construct that fails, apply design decision 7 and note it. Verify the build passes and `checkSwiftExportStubs` passes with only the allow-list entries the design expects, or stop and report a construct that needs a decision.
- [x] 5.3 Compile a throwaway Swift file, not merged, that reads one state and sends one event for a contract holding each construct the probe left open: a `Duration` and an `Instant` (`UploadsState`), a `LocalDate` (`EditGearState`), a `ByteString` built from Swift bytes (`ImportActivityEvent.FilePicked`), the `ImmutableMap` in `ImportActivityEvent.SaveBatch`, a `SubScreen`, the `GeoPoint`s of `RouteEnds`, and a text field's `value`. Verify it compiles without warnings, and record each result in the roadmap's contract probe table.
- [x] 5.4 Add a TestKit or settings-level test that a new `-api` module is picked up. Verify it fails when the derivation is replaced by a fixed list missing the new module.
- [x] 5.5 Measure, as the spike did: lines of generated Swift, the app's debug dylib size, a build with nothing changed, and a build after a one-line change in `:iosShell`. Verify the four figures are added to the roadmap's size and time table; if the one-line rebuild is over two minutes, stop and report before group 6.
- [x] 5.6 Update `README.md`'s module table and AGENTS.md's iOS paragraph for `:core-textfield` and the exported contracts. Verify `scripts/check-public-safety.sh` passes.

## 6. CI builds the iOS app

- [x] 6.1 Add the `ios` job to `.github/workflows/ci.yml` (design decision 9): gated by `preflight` like `check`, the same JDK and Gradle setup, the simulator `xcodebuild` with ad-hoc signing, a 45 minute timeout. Verify the workflow file parses (`actionlint` if installed, otherwise a YAML parse) and that the command it runs passes locally from a clean clone with no `local.properties`.
- [ ] 6.2 Push the branch only when asked, then verify on GitHub that the `ios` job passes, that it is skipped on a docs-only commit, and note its cold and warm durations in `.agents/rules/build-logic.md`.

## 7. Integration

- [x] 7.1 Run `./gradlew check`, `./gradlew -p build-logic :convention:test`, `./gradlew composeStabilityCheck` and `./gradlew checkModuleBoundaries`. Verify all pass.
- [ ] 7.2 Run the simulator pass against `scripts/dev.sh --seed alice`: sign in, open the feed, a profile and an activity, write a comment, search for an athlete, propose a segment name (the renamed field's sheet), and open Settings. Verify each behaves as before this change.
- [ ] 7.3 Run the web app and the desktop app once each and type in one text field. Verify typing, clearing and submitting work.
- [x] 7.4 Rewrite the roadmap's "TextFieldState" section and phase 1 line in `openspec/explorations/ios-native-ui.md` to what was built, and move the open items this change settled out of "Open items". Verify `openspec validate ios-swift-clean-contracts --strict` passes.

## Not verified

- 6.2 needs the branch pushed. The `ios` job has never run: its runner label and the memory given to Gradle are untested.
- 7.2: the place-name sheet was not opened in the simulator. The seeded account has no efforts on any segment, so it is never offered
  "Propose a name". Everything else in the pass was done, on iOS 26 signed in and on iOS 18.4 for the sign-in handle.
- 7.3: on the desktop app, typing was checked and clearing and submitting were not (no way to see the window after the first capture).
  The web app passed all three.

## Workflow follow-up

- Archive the change and sync its three delta specs once the user has reviewed it.
- Phase 2 (`:app-host`, the SwiftUI shell and a native Login) is planned as its own change after this one.
