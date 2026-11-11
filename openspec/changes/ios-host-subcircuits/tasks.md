# Tasks

## 1. Probe (throwaway code, not merged)

- [ ] 1.1 Throwaway Kotlin in `:app-host` and `:iosShell`, and a throwaway Swift test, on a simulator made for this change (iOS 26.0) (design decision 7): a record over a fake parent embedding the real `FeedFilterScreen` and `FeedFilterPresenter`, asked for its sub-record from Swift with `Any` and a closure over `SubCircuitOuterEvent`; `SetRange` and `Clear` sent from Swift. Verify the test passes and prints the states and outer events it read.
- [ ] 1.2 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [ ] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (host sub-screens)". Verify it records each result.

## 2. The host runs sub-screens

- [ ] 2.1 `HostSubRecord`, `HostRecord.sub` and `close`, the sub-screen lookup in `Presenters`, sub-records run inside `RunRecord` (decisions 1 to 5); `SessionHost` takes `Set<SubPresenterFactory>`. Verify new `jvmTest`s over fake sub-presenters for each scenario of the presenter-host delta: a state and an outer event, asked twice, replaced, covered and back, popped and reopened, closed and reopened, a non-sub-screen and an unknown one, and the existing host tests.
- [ ] 2.2 A `composeApp` JVM test that runs the real `FeedFilterPresenter` as the feed's sub-screen through a `SessionHost` from the session graph. Verify it passes.

## 3. The filter bar's texts

- [ ] 3.1 `FeedFilterTexts.types` and `range` in `:feature-feedfilter-api`; `FeedFilterUi` asks them. Verify a `commonTest` over each case, the names in the feature's text-names test, `:feature-feedfilter-ui:jvmTest` and that the filter's Android screenshot references are unchanged.

## 4. Swift

- [ ] 4.1 Type aliases (one added block in `KotlinTypes.swift`) and `HostSubRecord`, and `SubScreenView` in `Shell/` (decision 6).
- [ ] 4.2 `FeedFilterBar`, previews over `FeedFilterSamples` (one added block in `Samples.swift`), a text test reading the chips' words against the shared ones, and `FeedFilterSnapshotTests`. Verify they record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at, light and dark.

## 5. The whole change together

- [ ] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [ ] 5.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 5.3 Build the Debug and the Release configurations for the simulator. Verify both succeed and pass the stub check, and the stub report lists nothing new from `PresenterHost` or `:feature-feedfilter-api`.

## 6. Docs

- [ ] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (host sub-screens)", the open items (the Feed's blocker, the equal filter screen). Verify `openspec validate ios-host-subcircuits --strict` passes.
- [ ] 6.2 `.agents/rules/compose-screens.md` (the host's sub-screens, `SubScreenView`, closing a sheet's sub-record). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 6.3 A "Not verified" section listing what wasn't seen and why.
