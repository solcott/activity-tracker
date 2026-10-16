# Tasks

## 1. Lexicons and models

Follow the `lexicon-change` skill throughout this group.

- [x] 1.1 `feed/activityStreams.json`: add `gasSwitches` (`#gasSwitch`: time, oxygen, helium), `tankPressures` (`#tankPressure`: pressure array), `laps` (`#lap`), and the int channels ppO2, cns, n2Load, ndl, decoStopDepth, decoStopTime, tts, respirationRate, verticalOscillation, stanceTime, stepLength, verticalRatio, stanceTimeBalance, leftRightBalance, with units in each description (design.md, Units); add the tokens to `channels.knownValues` and raise `channels.maxLength` to 32. Verify the JSON parses and `scripts/check-agent-sync.sh` is unaffected.
- [x] 1.2 `feed/activity.json`: add elevationLoss, avgCadence, maxCadence, avgPower, maxPower, normalizedPower, work, calories, frontShifts, rearShifts, jumps, grit, flow; on `#dive` add startN2, endN2, avgAscentRate, maxAscentRate, avgDescentRate, maxDescentRate, descentTime, waterType (open enum: fresh, salt, en13319, custom), waterDensity, airTemperature, visibility, altitude, noFlyTime, desaturationTime, highestPpO2; on `#diveTank` add volume and a `gas` ref to `#diveGas`. Verify the JSON parses.
- [x] 1.3 `feed/activityZones.json`: add a `#trainingLoad` object (ftp, intensityFactor, trainingStressScore, aerobicTrainingEffect, anaerobicTrainingEffect) and say in the record description that it holds training load as well as zones. Verify the JSON parses.
- [x] 1.4 Mirror 1.1–1.3 in `core/lexicon` (`ActivityStreamData`, `GasSwitch`, `TankPressure`, `Lap`, `StreamChannel`, `ActivityRecord`, `ActivityDive`, `ActivityDiveTank`, the zones record and `TrainingLoad`), and add the channel table (token, getter, copy-with) that `ActivityStreamData.channels` now derives from. Extend `ActivityStreamsSerializationTest` and the activity/zones serialization tests with the new fields; verify with `./gradlew :core-lexicon:jvmTest`.
- [x] 1.5 Check the AppView passes the new fields through: run the `lexicon-auditor` agent on the three NSIDs, fix any view that rebuilds records from columns, and add an AppView test that a `getActivity` and `getActivityStreams` response keeps a new dive field and a new channel token; verify with `./gradlew :server-appview:test`.
- [x] 1.6 Republish the local lexicons (`docker/scripts/publish-lexicons.sh`) and verify a record with 20 channels is accepted by the local PDS with `scripts/dev.sh` running.

## 2. FIT decoding

- [x] 2.1 Check the Java, JS (npm) and Swift FIT SDK versions pinned here expose `record.po2`, `ndl_time`, `next_stop_depth`, `next_stop_time`, `time_to_surface`, `cns_load`, `n2_load`, `respiration_rate`/`enhanced_respiration_rate`, the running dynamics fields, `left_right_balance`, `tank_update`, `session.total_calories`/training effect, and the dive summary's N₂, ascent/descent and descent time fields; record any gap in a KDoc on `FitDecoder` and bump an SDK only if the bump is a patch or minor release.
- [x] 2.2 Extend `DecodedSample` (dive state, respiration, running dynamics, balance), `DecodedSummary` (calories, training effect), `FitDiveSummary` (N₂, rates, descent time), `FitDiveMessages` (`FitGasSwitch(timestampMillis, gasIndex)`, water type and density, `DecodedTankUpdate` list) and `DecodedActivity` (gas switches, tank updates, notes). Verify the module compiles: `./gradlew :core-activityfile:compileKotlinJvm`.
- [x] 2.3 Fill the new fields in `GarminJavaFitDecoder` and extend `GarminFitDiveTest`/`GarminFitRideTest` with the bundled dive fixtures and a ride fixture asserting switch timestamps, deco channels, tank updates, laps and the summary fields; verify with `./gradlew :core-activityfile:jvmTest`.
- [x] 2.4 Fill the same fields in `GarminJsFitDecoder` (wasm tests are disabled); verify `./gradlew :webApp:wasmJsBrowserDevelopmentRun` imports the dive fixture with switch markers on its depth chart once group 7 lands, and for now that `:core-activityfile:compileKotlinWasmJs` passes.
  - Checked on the web app: the CCR fixture imported through the JS decoder shows Tx 17/30 → EAN52, N₂ load and salt water, and its published depth chart has both switch markers and the ceiling.
- [x] 2.5 Fill the same fields in `iosApp/iosApp/SwiftFitDecoder.swift`; verify the iOS app builds with the `xcodebuild` command in CLAUDE.md.

## 3. UDDF parsing

- [x] 3.1 `UddfParser`: record each `switchmix` with its waypoint's `divetime`, and read waypoint `tankpressure` (with its `ref`), `calculatedpo2`, `measuredpo2`, `cns`, `nodecotime`, `decostop` (depth and duration) and `heartrate`. Extend `UddfFixtures`/`UddfParserTest`; verify with `./gradlew :core-activityfile:jvmTest --tests "*.UddfParserTest"`.
- [x] 3.2 `UddfParser`: read `tankvolume` and each tank's mix link, `informationbeforedive` air temperature and altitude, and `informationafterdive` visibility, no-fly time, desaturation time, highest ppO₂ and notes; ignore buddy links. Extend the test with each, including a buddy that doesn't appear in the result; verify as in 3.1.

## 4. Subsurface and Shearwater switch times

- [x] 4.1 `SubsurfaceXmlParser`: record each `gaschange` event's time and gas; `ShearwaterXmlParser`: record a switch where a record's gas differs from the previous one. Extend `SubsurfaceXmlParserTest` and `ShearwaterXmlParserTest`; verify with `./gradlew :core-activityfile:jvmTest`.

## 5. Mapping into ImportedActivity

- [x] 5.1 `FitDives`: resolve `FitGasSwitch` indexes to gases (dropping unknown slots), keep `gasesBreathed` as it is, map water type and the new summary fields, and order tank sensors by `tank_summary`. Extend `FitDivesTest` (unknown slot dropped, switch back to a gas, first gas at 0); verify with `./gradlew :core-activityfile:jvmTest --tests "*.FitDivesTest"`.
- [x] 5.2 `StreamBuilder`: build every new int channel, `gasSwitches` (first gas at 0), `tankPressures` (updates placed on the nearest sample within 30 s, measured ppO₂ averaged and preferred over calculated) and `laps` (none for a single spanning lap). Extend `StreamBuilderTest`; verify as in 5.1.
- [x] 5.3 `DecodedActivityMapper` and `ImportedActivity`/`ImportedDive`: carry calories, training effect, the dive summary fields, tank volume and gas, and the notes as `ImportedActivity.description`. Extend `DecodedActivityMapperTest`; verify with `./gradlew :core-activityfile:jvmTest`.

## 6. Publishing and merging

- [x] 6.1 `ActivityDraft.fromImport`: publish the new summary fields from `power`, `shifting`, `mtb`, cadence and elevation loss, and start the draft's description from the import's. `DiveSummary.of`: map the new dive and tank fields in lexicon units. Extend the draft and `DiveSummary` tests; verify with `./gradlew :core-data:jvmTest`.
- [x] 6.2 A dive with no GPS samples builds its GPX track from `entry`/`exit` (else the site position) timed at its start and end, with `route` left empty so no polyline is made. Add an `ActivityRepositoryTest` case that the track is in `self`, and the record has no polyline; verify as in 6.1.
- [x] 6.3 Training load: snapshot FTP, IF, TSS and training effect from the import into the zones record, write the record when there's training load and no zone snapshots, and keep its placement and moves as for zones. Extend the zones store and repository tests (Public ride with Only-me training puts training load in `self`; training narrowed moves it); verify as in 6.1.
- [x] 6.4 `ActivityMerge`: iterate the channel table in `fillMissing`, `addsChannels` and `shifted`; fill `gasSwitches` and `laps` when absent (shifted onto the existing start), `tankPressures` when the dive has no tanks or the same count, and the new summary and dive fields in `fillGaps`/`DiveSummary.fillGaps`. Extend `ActivityMergeTest` per spec scenario; verify as in 6.1.

## 7. UI

- [x] 7.1 Depth chart (`ui/DepthProfile.kt`): a marker per gas switch labeled Air, EANnn or Txnn/nn, and the deco stop depth drawn as a ceiling under the depth line. Add the gas-name formatter with a test; verify the UI on the desktop app with the hot-reload MCP (screenshot of an imported fixture dive).
- [x] 7.2 Dive charts: ppO₂, CNS and tank pressure (a line per tank) after the depth chart, only where stored; the new dive summary fields in `ui/DiveStats.kt`. Extend the detail presenter's chart test for which series exist; verify with `./gradlew :app-presenters:jvmTest` and a hot-reload screenshot.
- [x] 7.3 Activity charts and stats: respiration rate, running dynamics and left/right balance charts; power, cadence, calories, elevation loss, shift and MTB stats on the detail page; training load shown only when the zones record is readable by the viewer. Extend presenter tests (follower without training access sees no FTP or TSS); verify as in 7.2.
- [x] 7.4 Lap table on the detail page from `streamData.laps`, built from `ui/adaptive` wrappers. Add a presenter test for lap rows in the viewer's units; verify as in 7.2.
- [x] 7.5 Import screen: prefill the description from the file's notes and keep it editable. Extend the import presenter test; verify as in 7.2.
- [x] 7.6 Run `./gradlew composeStabilityCheck` and fix any unstable parameter the new state adds.

## 8. Export

- [x] 8.1 `ExportPoints`/`ExportActivity`: carry the new channels, gas switches, tank pressures, laps, summary fields, dive fields and the zones record's training load. Extend `ExportPointsTest`; verify with `./gradlew :core-activityfile:jvmTest --tests "*.ExportPointsTest"`.
- [x] 8.2 `FitProfile`/`FitWriter`: write the new record fields, `dive_gas_switched` events, `tank_update` messages, stored laps (one lap when none), the new session, lap and dive summary fields, and `dive_settings` water type and density; drop the "no `dive_gas_switched`" KDoc. Extend `FitWriterTest` and `FitRoundTripTest` for each export spec scenario (switches, laps, tank pressure, deco records, power and training load); verify with `./gradlew :core-activityfile:jvmTest`.
- [x] 8.3 Pass the zones record's training load into the export in `ActivityExporter`, and say laps are missing in the rebuilt-file note only when none are stored. Extend the export presenter test for both notes; verify with `./gradlew :core-data:jvmTest :app-presenters:jvmTest`.

## 9. Docs

- [x] 9.1 Update `.agents/rules/activity-files.md` (what decoders carry, gas switches, the new channels, laps, FIT export writing switches) and trim BACKLOG.md's "Keep more of what activity files hold" so its dive, ride and streams bullets no longer list what this change keeps (what it leaves out is already there, planned in `keep-remaining-file-data`). Verify `scripts/check-agent-sync.sh` passes.
- [x] 9.2 Note in `openspec/changes/export-dives-as-uddf/proposal.md` that gas switch times are stored (its open question is answered) and that a trackless dive's track now holds its site position. Verify `openspec validate export-dives-as-uddf` passes.
  - The notes are in. Validation still fails, but not because of this change: `export-dives-as-uddf` has no spec deltas yet ("Change must have at least one delta"). It will pass once that change's specs are written.

## 10. Integration

- [x] 10.1 Run `./gradlew check` (via the `gradle-runner` agent) and fix what it reports.
- [x] 10.2 With `scripts/dev.sh --seed alice`, import a FIT dive with deco gas and a FIT ride with power and laps on the desktop app, and confirm the dive's switch markers, ceiling and tank chart, the ride's stats, charts and lap table, and that a second account following alice sees power but no TSS; export both as FIT and re-import, checking gases, switches and laps come back.
  - Checked by driving the desktop app. Bob's view was checked through the AppView as bob (`getActivity` has the power summary, `getActivityStreams` has the power stream and no `trainingLoad`) rather than by signing bob in on screen. The re-imports were checked on the import screen and by decoding both exports; neither was published.
