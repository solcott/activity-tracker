# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Kotlin in `:composeApp` and `:iosShell`, and throwaway Swift tests, on a simulator made for this change (iOS 26.0) (design decision 7): a record over a stand-in parent embedding the real `FeedFilterScreen`, asked for its sub-record from Swift with `Any` and a closure over `SubCircuitOuterEvent`; `SetRange` and `Clear` sent from Swift, and `SubScreenView` drawing the bar. The sub-presenter was a stand-in, since the real one needs the session's database; it runs through the host in 2.2's JVM test. Verify the tests pass and print the states and outer events they read.
- [x] 1.2 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (host sub-screens)". Verify it records each result.

## 2. The host runs sub-screens

- [x] 2.1 `HostSubRecord`, `HostRecord.sub` and `close`, the sub-screen lookup in `Presenters`, sub-records run inside `RunRecord` (decisions 1 to 5); `SessionHost` takes `Set<SubPresenterFactory>`. Verify new `jvmTest`s over fake sub-presenters for each scenario of the presenter-host delta: a state and an outer event, asked twice, replaced, covered and back, popped and reopened, closed and reopened, a non-sub-screen and an unknown one, and the existing host tests.
- [x] 2.2 A `composeApp` JVM test that runs the real `FeedFilterPresenter`, over the shared session fixture, as the feed's sub-screen through a `SessionHost` (`sessionHostOf`, a test entry like `appHostOf`; the whole session graph isn't built in a JVM test). Verify it passes.

## 3. The filter bar's texts

- [x] 3.1 `FeedFilterTexts.types`, `range`, `rangeChoices` and `chosenRange`, and `TypePickerState.selectionOf`, in `:feature-feedfilter-api`; `FeedFilterUi` asks them. Verify a `commonTest` over each case, the names in the feature's text-names test, `:feature-feedfilter-ui:jvmTest` and that the filter's Android screenshot references are unchanged.

## 4. Swift

- [x] 4.1 Type aliases (one added block in `KotlinTypes.swift`) and `HostSubRecord`, and `SubScreenView` in `Shell/` (decision 6).
- [x] 4.2 `FeedFilterBar`, previews over `FeedFilterSamples` (one added block in `Samples.swift`), a text test reading the chips' words against the shared ones, and `FeedFilterSnapshotTests`. Verify they record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at, light and dark.

## 5. The whole change together

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 5.3 Build the Debug and the Release configurations for the simulator. Verify both succeed and pass the stub check, and the stub report lists nothing new from `PresenterHost` or `:feature-feedfilter-api`.

## 6. Docs

- [x] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (host sub-screens)", the open items (the Feed's blocker, the equal filter screen). Verify `openspec validate ios-host-subcircuits --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` (the host's sub-screens, `SubScreenView`, closing a sheet's sub-record). Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why.

## Order

The host's Kotlin (2.1, 2.2) was written before the probe, so the probe asked its questions of the real lookup rather than of a sketch
of it; nothing the probe found changed the host.

## Probe results (1.1)

On `Host Subs iPhone 17` (iOS 26.0), throwaway Kotlin (a `SessionHost` from `sessionHostOf` over a stand-in feed presenter holding a real
`FeedFilterScreen` and a stand-in filter presenter, on the main thread with the display-link clock, reached through a throwaway
`IosApp.probeFeedRecord()`) and two throwaway Swift tests:

- `record.sub(screen:outerEventSink:)` took the state's `FeedFilterScreen` as `any _KotlinBridgeable`, with no stub; `screenName` read
  `FeedFilterScreen`, `key` `record-0-sub-0`.
- Asking again returned the same object (`===`); after `close()`, the same screen gave a new one.
- `state.value` cast to `FeedFilterState`: first the default filter, then, after `SetRange(range: DateRange.LastDays(days: 30))`,
  `LastDays(days=30)`. `asAsyncSequence()` followed it: narrowed, then not after `Clear.shared`.
- The closure received `FeedFilterOuterEvent.Changed` three times: nil, the last 30 days, nil. Asking again replaced the closure, so only
  the newest one heard them.
- `SubScreenView { FeedFilterBar(state:) }` in a window drew "Any time", and after a `SetRange` sent through the drawn state's sink drew
  "Last 7 days" with Clear filters (its rendering looked at); the outer events read "Any time", "Last 7 days".

## Not verified

- **A native page drawing a sub-screen in the app**: no page places `FeedFilterBar` (the feed is still the shared UI's), so the bar and
  `SubScreenView` were drawn from a host record only in the throwaway probe's window, not in the running app, and never covered by a
  push and uncovered on a device. Being covered and coming back rests on `HostSubRecordTest`.
- **The real filter presenter on iOS**: it needs the session's database, so on iOS the probe ran a stand-in; the real one ran through
  the host on the JVM (`FeedFilterThroughHostTest`).
- **The native type and custom range sheets interactively**: they were seen in snapshots (the type sheet) and previews only; their
  dismiss-by-swipe binding, the date picker's bounds and the two-step pick weren't driven on a device.
- **A sheet sub-screen (report, place name) through the host**: none is native yet; closing a sheet's sub-record rests on the JVM tests.
- **The feed's outside Clear**: that it leaves the bar's chips unchanged was read from `FeedPresenter`, not seen; it is the same in both
  UIs and for the feed change to fix.
- **VoiceOver** on the bar's chips and marks.

## Results (5)

- `./gradlew check` passed (after `SessionHost`'s constructor took a `LongParameterList` note), with no Android screenshot reference
  changed; `./gradlew -p build-logic :convention:test` passed.
- Swift tests: 281 tests in 49 suites passed on `Host Subs iPhone 17` (iOS 26.0), snapshots compared, and on `Host Subs iPhone 16 Pro`
  (iOS 18.4), snapshots skipped.
- Debug (with the tests) and Release simulator builds succeeded and passed `checkSwiftExportStubs`; the stub report names nothing from
  `PresenterHost` or `:feature-feedfilter-api`.
