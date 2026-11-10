# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on an iPhone 17 (iOS 26.0) simulator over `IgnoredSensorsSamples` and `GearCollectionSamples` (design decision 2): the ignored devices' `ContentState` value through `elements(as: LinkedSensor.self)`, each device's `weak`, `serialTail` and names, the collection's `notes` and `members`. Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway tests. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (gear pages)", with the decision to defer the item page (decision 1). Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 Move `deviceName` to `:app-common-api` beside `GearTexts`; add `LinkedSensorTexts` in `:feature-gear-api`; `LinkedSensorRow`, `IgnoredSensorsUi` and `GearUi`'s linked devices ask them (decision 3). Verify `commonTest`s for each branch and label, and `:feature-gear-ui:jvmTest` and `:feature-record-ui:jvmTest` pass.
- [x] 2.2 `GearCollectionState.title`, `showsActions` and `actionsEnabled`; `GearCollectionUi` asks them (decision 4). Verify a `commonTest` over every sample kind, and `:feature-gear-ui:jvmTest`.
- [x] 2.3 `GearCollectionSamples.variants` and `IgnoredSensorsSamples.variants` (decision 6). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed.

## 3. The native pages

- [x] 3.1 Type aliases and `Samples.gearCollection`, `gearCollectionVariants`, `ignoredSensors`, `ignoredSensorsVariants`. Verify a Swift test reads them (`GearPagesTextTests`).
- [x] 3.2 `GearCollectionView` with its delete alert, registered (decision 5). Verify `GearPagesTextTests` resolves the spec's lines, title and conditions on the samples and the alert's words, and `ScreenRegistryTests` names `GearCollectionScreen`.
- [x] 3.3 `IgnoredSensorsView`, registered; `AppItemRow` without a leading view only if needed, with a gallery entry and its reference. Verify `GearPagesTextTests` covers the devices' names, details, labels and the empty text, and `ScreenRegistryTests` names `IgnoredSensorsScreen`.
- [x] 3.4 Previews and `GearPagesSnapshotTests` (decision 6). Verify they record on a first run and pass on a second on an iOS 26.0 iPhone 17 simulator, and each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 Run the Swift tests (`xcodebuild test`) on the iPhone 17 iOS 26.0 simulator and on iOS 18.4. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 4.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-gear-api` or `:app-common-api`.

## 5. Walk the pages

- [x] 5.1 On the signed-in iPhone 17 Pro (iOS 26.0): a new collection made through the shared editor from Gear, opened natively; its rows against the shared page; an item opened from it and back; Edit opened and left; Delete cancelled, then confirmed, closing the page; Ignored devices from Gear's More, empty; back by the button and the swipe. Verify by noting what was seen, and that the account holds no collection the walk made.
- [x] 5.2 On the signed-in iPad Air 11-inch (iOS 26.0): a collection and Ignored devices opened from Gear beside Settings' list take the whole window, and back returns to the list beside Gear; upright and on its side. Verify by noting what was seen.
- [x] 5.3 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 6. Docs

- [x] 6.1 The roadmap's phase 4 entry, a "Phase 4 findings (gear pages)" section, and the open items: the item page waits for the native activity card, with its components, mount sheet and unmount date picker. Verify `openspec validate ios-native-gear-pages --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` (the native pages' list, `LinkedSensorTexts`, `deviceName`, the collection's getters) and `adaptive-ui.md` if it names the native pages. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 5.

## Probe results (1.1)

- A list as a `ContentState`'s value doesn't reach Swift as a Kotlin collection: through the erased `data` the loaded devices arrive as a
  Swift `Array<Any>` of two `LinkedSensor`s, and the loading sample's empty `persistentListOf()` throws `NSInvalidArgumentException`
  (`count` unrecognized selector) when read. `IgnoredSensorsState.sensors` (typed `ImmutableList<LinkedSensor>`) reads every sample
  through `elements(as:)`: loaded, refreshing and refresh-failed two devices, loading, failed and nothing ignored none. The design and the
  roadmap record it (decisions 2 and 5).
- `weak` reads (false for the Edge, true for the H10), `serialTail` "2071" and nil; the collection's notes "Cold water setup" and nil,
  its members Regulator, Wetsuit (retired) and Fins in order. `deviceName` is `GearPackage.deviceName(manufacturer:model:)` once moved.

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as the local test account; iPhone 16 Pro on iOS 18.4)

- **5.1, iPhone.** Settings → Gear → More → Ignored devices: native, titled in the stack's bar, "No ignored devices. Devices you mark as
  not gear when importing are listed here." (the account's real, empty list: the case the probe found would have crashed); back by the
  edge swipe. New collection (the shared editor) made "Walk kit" with the Garmin Edge 840 and the Garmin HRM 200; its notes saved as
  "Made by t", cut off by a tap that landed while typing. Walk kit opened natively: titled "Walk kit", the notes in a section of their own,
  "Garmin Edge 840 / Device · Only me" and "Garmin HRM 200 / Heart rate · Only me" with their placeholders and no totals (the shared page
  shows none either: a collection's members carry no totals), Edit and Delete in the bar. The Edge opened the shared gear page, whose
  linked devices row (now asking `LinkedSensorTexts`) read "Garmin Edge 840 / Serial …2071" with Unlink; its back button returned to
  Walk kit. Edit opened the shared editor filled in; its back returned unsaved. Delete asked "Delete this
  collection? / Its items, and the gear on your activities, stay as they are." with Delete collection (red) and Cancel; Cancel closed it
  and Walk kit stayed. After the iPad walk, Delete → Delete collection closed the page onto Gear, which listed no collection again: the
  account holds nothing the walk made.
- **5.2, iPad (this change's Debug build, already signed in).** Upright: Settings with Activity defaults beside it, then Gear beside the
  list with Walk kit (2 items). Walk kit took the whole window, titled under the tab bar with Edit and Delete at the bar's trailing end;
  back returned to Settings with Gear beside the list, Gear marked. Gear's More → Ignored devices took the whole window with the empty
  text on one line. Turned on its side there: still the whole window; back returned to the list beside Gear. Walk kit on its side: the
  whole window with Edit and Delete; back returned to the list beside Gear. Turned upright again.
- **5.3, iOS 18.4 (iPhone 16 Pro).** The same build launched to the native sign-in screen.

## Not verified

- **Ignored devices with devices in it, on a device.** The test account has none, and ignoring one needs an import that offers a new
  device. Its rows, Un-ignore and its spoken label ("Un-ignore Garmin Edge 840") rest on the `loaded` and `unignoreFailed` snapshots and
  `GearPagesTextTests`; VoiceOver wasn't heard.
- **A failed delete, a failed un-ignore and the deleting state, on a device.** The local stack answered every write; they rest on the
  `deleteFailed`, `unignoreFailed` and `deleting` snapshots. Edit and Delete greyed while a delete runs weren't seen either: the
  delete finished before a screenshot.
- **A retired item and an item's photo in a collection, on a device.** Walk kit held two active items with no photo; retired rows rest
  on the samples' snapshots and text tests, and a photo on `Thumbnail`, seen on the Gear list.
- **The delete alert in a snapshot.** `.alert` presents over the page, so the `confirmingDelete` snapshot shows the page under it; the
  alert was seen on the iPhone and its words are held by `GearPagesTextTests`.
- **The pages on iOS 18, signed in.** The 18.4 simulator isn't signed in and signing in needs the account's password; the Swift tests ran
  there with the snapshots skipped.

## Workflow follow-up

- Archive with `/opsx:archive` once the user has reviewed and committed the change.
- The gear item page (`GearScreen`) and the editors (`EditGearScreen`, `EditGearCollectionScreen`) are later changes.
