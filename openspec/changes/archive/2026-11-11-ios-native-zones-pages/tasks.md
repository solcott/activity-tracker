# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Throwaway Swift tests on a simulator made for this change (iOS 26.0) (design decision 1): `Zone.name` as Swift's enum, `ZoneModel` options read typed, a `List` of a contract class from a text function, a contract enum's properties and as a parameter, and a burst-typed "9a5" through `TextFieldModel`. Verify the tests pass and print what each read.
- [x] 1.2 Remove the throwaway code. Verify `git status` shows only the change's own files.
- [x] 1.3 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (zones pages)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `ZoneNameText` in `:app-common-api`; `zoneName` resolves it for the activity charts and the zones pages (decision 7). Verify a `commonTest` over every `ZoneName`, `SharedTextNamesTest`, and `:app-common-ui:jvmTest`.
- [x] 2.2 `ZoneTexts`' new functions, `TrainingZonesTexts`, `SportZonesTexts`, `PrivacyZonesTexts`, `ZoneThreshold` and the states' getters in `:feature-settings-api`; the four Compose pages ask them. Verify `commonTest`s over every branch and sample, the presenter tests and `:feature-settings-ui:jvmTest`.
- [x] 2.3 The samples' `variants` in `:app-fixtures` (decision 8). Verify `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite` pass with no reference changed.

## 3. Design system

- [x] 3.1 `TextFieldRules.digitsOnly` keeps an edit's digits and `TextFieldModel` redraws over what it drops (decision 6). Verify `TextFieldModelTests` (a burst "9a5", a paste "170 bpm", a too-long paste) and `GearEditorLogicTests` pass.
- [x] 3.2 `AppTextField`'s `hint` (decision 5), with a gallery entry. Verify `DesignSnapshotTests` record and then compare.

## 4. The native pages

- [x] 4.1 Type aliases (one block) and the samples' variants in `Samples.swift`. Verify `ZonesPagesTextTests` reads them.
- [x] 4.2 The shared zone rows (a zone, a kind's heading, what a model needs) and `TrainingZonesView`, registered (decision 2). Verify `ZonesPagesTextTests` resolve its headings, threshold lines and ranges on the samples, and `ScreenRegistryTests` names `TrainingZonesScreen`.
- [x] 4.3 `ZonesView`, registered (decision 3). Verify `ZonesPagesTextTests` cover its limits, validity, auto-update and `canSave` with a model driven, and `ScreenRegistryTests` names `ZonesScreen`.
- [x] 4.4 `SportZonesView`, registered (decisions 3 and 5). Verify `ZonesPagesTextTests` cover its title, pace label, hints, pace ranges and Remove, and `ScreenRegistryTests` names `SportZonesScreen`.
- [x] 4.5 `PrivacyZonesView`, registered (decision 4). Verify `ZonesPagesTextTests` cover the names, distances, hidden distances and the add button's words, and `ScreenRegistryTests` names `PrivacyZonesScreen`.
- [x] 4.6 Previews and `ZonesPagesSnapshotTests` (decision 9). Verify they record on a first run and pass on a second on an iOS 26.0 simulator, and each image was looked at, light and dark.

## 5. The whole change together

- [x] 5.1 Run `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 5.2 Run the Swift tests (`xcodebuild test`) on an iOS 26.0 and an iOS 18.4 simulator. Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 5.3 Build the Release configuration for the simulator. Verify it succeeds and passes the stub check, and the stub report lists nothing new from `:feature-settings-api` or `:app-common-api`.

## 6. Walk the pages (only when no other session is walking, or with the user's go-ahead)

- [x] 6.1 On the signed-in iPhone 17 Pro (iOS 26.0), as alice on the local stack, after noting her zones, visibility and privacy zones: Training zones read against the shared page; the visibility changed and put back; Edit defaults, a threshold typed with a letter in a burst (the digits kept), a value out of range (error, Save off), a model changed and saved, then put back; Add a sport opened and left by back; a sport saved, seen on Training zones, then removed; Threshold history opened (the shared page). Verify by noting what was seen.
- [x] 6.2 On the same iPhone: Privacy zones, a radius changed and put back, Hide start and end changed and put back, a zone added here and removed if location is already allowed. Verify by noting what was seen, and that alice's zones read as before.
- [x] 6.3 On the signed-in iPad Air 11-inch (iOS 26.0): Training zones beside Settings' list, a sport's zones as the column's second page with its back button, upright and on its side; Privacy zones beside the list. Verify by noting what was seen.
- [x] 6.4 Launch the build on an iOS 18.4 simulator. Verify it reaches sign-in, and record what couldn't be seen there.

## 7. Docs

- [x] 7.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (zones pages)", the zones contracts' bullet and the open items corrected (no charts or map needed, the refused edit decided, the zone row's text moved). Verify `openspec validate ios-native-zones-pages --strict` passes.
- [x] 7.2 `.agents/rules/compose-screens.md` (the zones pages, the new texts and getters, `ZoneThreshold`, the digits rule, `hint`) and `adaptive-ui.md`'s native page list. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 7.3 A "Not verified" section listing what wasn't seen and why. Verify it names every unchecked case from 6.

## Probe results (1.1)

On `Zones Pages iPhone 17` (iOS 26.0): `heartRateModels.elements(as: ZoneModel.self)` read the five heart rate models with their tokens
and `basis` as Swift's enum; `SportZonesState.paceUnit` read as `.PerKilometer` with `meters` 1000.0 and `defaults.maxHeartRate` as
`Int64?`; and `TextFieldModel` under digits only, given "9", "9a" and "9a5" as a burst delivers them, ended at "9" with two refusals.
Recorded in the roadmap under "Phase 4 probes (zones pages)".

## Walk (iPhone 17 Pro and iPad Air 11-inch on iOS 26.0, signed in as alice on the local stack; this change's Debug build)

Before: alice (imperial units) had no thresholds, zone models, history, sports or privacy zones, Hide start and end Off. No other
session was walking (the invite lists' and uploads' notes say done), and the tunnel answered.

- **6.1, iPhone.** Settings → Training zones opened natively: Default zones with Edit, four thresholds "Not set", heart rate and power
  zones each with its model and basis and what it needs, Add a sport; no visibility and no history (nothing saved). Edit → "Default
  zones": "17a0" typed in one burst read "170" (the digit after the letter kept); heart rate zones appeared live from it; 300 as max
  heart rate turned its label red and Save grey as it was typed; a fourth digit was refused by the 3-digit length; the heart rate model
  menu listed the five models with Andy Coggan (5) checked, and "% of max heart rate (5)" changed the basis to "Max heart rate" and the
  zones to what it needs. Left by back without saving: Training zones unchanged. Add a sport listed Run, Ride, Swim, Row, Cross-Country
  Ski, Walk and Hike; Run → "Run zones" with "Threshold pace (m:ss /mi)" and no default hints (alice has no defaults); 7:30 typed showed
  pace zones per mile live; Save returned to Training zones, now with "Who can see your zones" (Only me) and Threshold history, and Run
  zones reading "7:30 /mi" and "Not set" for the rest, zone 5c's "6:45 /mi or faster" under its name. Visibility to Followers saved and
  showed the Followers note; back to Only me. Threshold history opened the shared page, listing Run's pace. Run → Edit → "Remove Run
  zones" returned to Training zones without Run. The threshold history still held the 7:30 value, so visibility and history still showed;
  the value was removed on the shared history page, after which Training zones read as before the walk.
- **6.2, iPhone.** Privacy zones opened natively: the help as its first header, "No privacy zones yet.", the activity note, a label
  field and "Add a zone at my location" (a phone), and Hide start and end "Off" with its menu offering Off and 0.12 mi to 0.99 mi.
  0.12 mi saved and showed; back to Off. Settings' row read "No zones" before and after. Then, in a second walk on the same iPhone
  (after the edit profile and segment lists sessions' walks, this change's build reinstalled): "Walk test" typed as the label (the
  field capitalised it "Walk Test"), Add a zone at my location read "Finding your location…" with a small progress mark and asked for
  no permission (the app already had it); the simulator had no location, so one was set with `simctl location` (a Boulder point), after
  which the zone was added as "Walk Test" with "Radius" under it and 0.25 mi (400 m), and the label field cleared. Its radius menu
  offered 0.12 mi to 0.99 mi with no Off and 0.25 mi checked; 0.50 mi saved, then back to 0.25 mi. A left swipe on the row showed
  "Remove zone"; it removed the zone, and the page read "No privacy zones yet." again, Settings' row "No zones". The simulated location
  was cleared afterwards.
- **6.3, iPad.** Settings beside Activity defaults; Training zones beside the list, titled in the column header with no back button and
  its row marked; Edit → Default zones as the column's second page with a back button and Save in the header; back returned to
  Training zones beside the list. Privacy zones beside the list, upright and on its side (the list kept its marked row), then upright.
- **6.4, iOS 18.4** (`Zones Pages iPhone 16 Pro`, made for this change). This change's 18.4 build launched to the native sign-in.

After: alice's zones, visibility, threshold history and privacy settings read as before. Her training record may keep an empty
training object and set-at times from the Run save and its removal; nothing the app shows reads them.

## Not verified

- **The location prompt.** The app already had location access on the walk's iPhone, so the first-time prompt after Add a zone at my
  location wasn't seen; that the button sends `PermissionResult(granted: true)` and the tracker then asks rests on the shared iOS UI
  doing the same (`LocationPermission.ios.kt`). Finding no location at all (the 30-second "No location" error) wasn't waited out.
- **The swipe's row while open.** With Remove zone showing, the row's name and "Radius" slid out of view and only the radius menu stayed;
  that is the system's swipe, unchanged here.
- **The Release configuration (5.3), being fixed in the session that started this one.** The Release build fails in the upload queue's aliases: `UploadQueueView` has no
  Swift type in Release (only `:app-fixtures`' samples name it), from `ios-native-uploads`, merged into the migration branch before this
  change. Every type this change names is in the Release export (checked in the generated Swift); the stub check ran up to date.
- **A sport's form with default hints on a device.** alice has no default thresholds, so the walk's Run form showed no "Default: N";
  the hints rest on the `loaded` and `saved` snapshots and the text tests.
- **The default zones saved on a device.** A default save writes set-at times and a model token that the form can't take back out, so
  the walk left by back; Save itself was seen on Run's form, and its enabling on the default form while typing.
- **The sign-in-again notice, a failed save and the saving state on a device**: the local stack granted every scope and answered each
  write at once. They rest on the `signInAgain`, `saveFailed` and `saving` snapshots.
- **VoiceOver**: the zone rows read as one element, the models' names and the swipe's Remove action weren't heard.
- **The pages on iOS 18 signed in**: the 18.4 simulator isn't signed in and signing in needs the account's password; the Swift tests
  ran there with the snapshots skipped.
- **A hardware keyboard typing at a human's pace**: the burst was the simulator tool's; at any pace each edit now keeps its digits.

## Workflow follow-up

- Commit in small commits by concern once the user asks; never push.
- Archive with `/opsx:archive` once the user has reviewed and committed the change, then merge into `claude/ios-swiftui-uikit-migration-df6ef6`.
