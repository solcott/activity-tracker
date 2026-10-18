# Tasks

## 1. Lexicons and models

Follow the `lexicon-change` skill throughout this group.

- [x] 1.1 `feed/activityStreams.json`: add `diveAlerts` (`#diveAlert`: `time`, `kind` as an open enum of the tokens in design.md, `maxLength` 64) to `streamData`, and `diveAlerts` to `channels.knownValues`. `feed/activity.json`: on `#dive` add `decoModel` (open enum `zhl16c`, `vpmB`, `vpmBGfs`, `dciem`), `ppO2Warn`, `ppO2Critical`, `ppO2Deco`, `lowSetpoint`, `highSetpoint` (millibar) and `safetyStopTime` (seconds), units in each description. Verify both files parse.
- [x] 1.2 Mirror 1.1 in `core/lexicon` (`ActivityStreamData.diveAlerts`, `DiveAlert`, the open-enum token constants, `StreamChannel.DIVE_ALERTS` and its place in `channels`, the `ActivityDive` fields, `DecoModel`). Extend `ActivityStreamsSerializationTest` and the activity serialization test; verify with `./gradlew :core-lexicon:jvmTest`.
- [x] 1.3 AppView: cap `diveAlerts` (500) and each `kind` (64 characters) in `ActivityStreamsViews.bounded`; run the `lexicon-auditor` agent on `feed.activity` and `feed.activityStreams`, and add AppView tests that `getActivityStreams` keeps (and caps) alerts and `getActivity` keeps a dive setting; verify with `./gradlew :server-appview:test`.

## 2. Alert kinds table

- [x] 2.1 Add `DiveAlerts` (common, `:core-activityfile`): each token with its FIT `dive_alert` value, its UDDF alarm if any and whether it's a warning, plus lookups by FIT value, by UDDF alarm and by token, and the fallback naming for unknown FIT values (design.md). Add a jvmTest that every Java SDK `DiveAlert` except `INVALID` and the two dismissals is in the table, and unit tests for the UDDF mapping and the fallback; verify with `./gradlew :core-activityfile:jvmTest --tests "*DiveAlerts*"`.

## 3. FIT decoding

- [x] 3.1 Extend `FitDiveMessages` with each `dive_alert` event (`FitDiveAlert(timestampMillis, value)`, the raw `data` number) and the `dive_settings` fields (model, ppO₂ warn/critical/deco, safety stop enabled and time, low and high setpoints); `DecodedActivity` with `diveAlerts: List<DecodedDiveAlert>`; `DecodedDive` with the settings in SI units. `FitDives` resolves alerts through `DiveAlerts`, drops dismissals and alerts without a time, and fills the settings (safety stop time only when enabled). Extend `FitDivesTest`; verify with `./gradlew :core-activityfile:jvmTest --tests "*.FitDivesTest"`.
- [x] 3.2 Fill the new `FitDiveMessages` fields in `GarminJavaFitDecoder` (`EventMesg` with `Event.DIVE_ALERT`, the kept `DiveSettingsMesg`); extend `GarminFitDiveTest` with a fixture carrying alerts (including a dismissal) and settings, encoded with Garmin's encoder like `SensorFitFixtures`; verify with `./gradlew :core-activityfile:jvmTest --tests "*.GarminFitDiveTest"`.
- [x] 3.3 Fill the same fields in `GarminJsFitDecoder`, passing `dive_alert`'s numeric value whatever the JS SDK names it; verify `./gradlew :core-activityfile:compileKotlinWasmJs` passes (wasm tests are disabled; checked end to end in 9.2).
- [x] 3.4 Fill the same fields in `iosApp/iosApp/SwiftFitDecoder.swift`; verify the iOS app builds with the `xcodebuild` command in CLAUDE.md.

## 4. UDDF alarms

- [x] 4.1 `UddfParser`: read each waypoint `alarm` as a `DecodedDiveAlert` at its waypoint's `divetime`, mapped through `DiveAlerts` (an unknown alarm keeps its text as the kind). Extend `UddfFixtures`/`UddfParserTest` with `ascent`, `rbt` and an unknown alarm; verify with `./gradlew :core-activityfile:jvmTest --tests "*.UddfParserTest"`.

## 5. Shearwater and Subsurface dive state

- [x] 5.1 `ShearwaterXmlParser`: fill each sample's ppO₂ (`averagePPO2`), no-deco limit (`currentNdl` minutes), deco stop depth (`firstStopDepth` in the header's units, null at 0) and time to surface (`ttsMins`); read `tank0pressurePSI`…`tank3pressurePSI` numeric readings as `tankUpdates` with a `DiveTank` per reporting slot; map the header's `decoModel` to its token. Extend `ShearwaterFixtures` (metric and imperial, a reporting and a non-reporting transmitter, in and out of deco) and `ShearwaterXmlParserTest`; verify with `./gradlew :core-activityfile:jvmTest --tests "*.ShearwaterXmlParserTest"`.
- [x] 5.2 `SubsurfaceXmlParser`: read sample `ndl`, `tts`, `stopdepth`, `stoptime`, `cns`, `po2` and `in_deco`, carrying each forward until the next sample that carries it and clearing the stop at `in_deco='0'`; read `pressure` and `pressureN`/`sensorN` as `tankUpdates` by cylinder index, adding a tank for a cylinder with readings but no start or end. Extend `SubsurfaceFixtures`/`SubsurfaceXmlParserTest` with carried-forward values and two cylinders; verify with `./gradlew :core-activityfile:jvmTest --tests "*.SubsurfaceXmlParserTest"`.

## 6. Streams, import and merge

- [x] 6.1 `StreamBuilder`: turn `DecodedActivity.diveAlerts` into the `diveAlerts` list in seconds since the start, in time order; `DecodedActivityMapper`/`ImportedDive` carry the settings. Extend `StreamBuilderTest` and `DecodedActivityMapperTest`; verify with `./gradlew :core-activityfile:jvmTest`.
- [x] 6.2 `core/data`: `DiveSummary.of` writes the settings in lexicon units (bar → millibar) and `ActivityDraft.fromImport` publishes them; `ActivityMerge` fills, shifts and counts `diveAlerts` like `gasSwitches`, and fills missing settings. Extend `ActivityDraftTest` and `ActivityMergeTest` with the spec's merge scenarios; verify with `./gradlew :core-data:jvmTest`.

## 7. Export

- [x] 7.1 `FitWriter`/`FitProfile`: write the settings into `dive_settings` (model only for `zhl16c`, safety stop enabled with its time) and a `dive_alert` event per alert with a FIT value. Extend `FitWriterTest` and `FitRoundTripTest` with the spec's settings and alert scenarios, decoded with the Java SDK; verify with `./gradlew :core-activityfile:jvmTest --tests "*Fit*"`.
- [x] 7.2 `UddfWriter`: put an `alarm` on the first waypoint at or after each alert with a UDDF alarm, in the schema's order. Extend `UddfWriterTest` (round trip) and let `UddfSchemaOrderTest` cover it; verify with `./gradlew :core-activityfile:jvmTest --tests "*Uddf*"`.

## 8. Dive page

- [x] 8.1 `ChartsBuilder`: add the warning alerts to `ActivityCharts` (`alerts`, with time and token), filtered by `DiveAlerts`' warning flag; `ActivityChartsUi` draws them on the depth chart as labelled markers in a warning colour, with a string resource per warning kind. Extend `ActivityFileDataChartsTest` (a warning is listed, an informational alert and an unknown kind aren't); verify with `./gradlew :app-presenters:jvmTest` and `./gradlew composeStabilityCheck`.
- [x] 8.2 `ui/DiveStats.kt`: show the deco model (with the gradient factors for `zhl16c`), ppO₂ limits and setpoints in bar, and the safety stop time, each only where stored; add string resources. Verify on the desktop app with Compose Hot Reload (`hot-reload.md`) by importing a FIT dive from 3.2's fixture: the stats show its settings and the depth chart its warning markers.

## 9. Integration

- [x] 9.1 Run `./gradlew check` and fix what it reports.
- [x] 9.2 With `scripts/dev.sh` running, import the 3.2 FIT fixture, a Shearwater and a Subsurface fixture on the web app (JS decoder) and confirm each dive's page shows its deco charts, tank pressure and, for FIT, alert markers and settings; then export the FIT dive as FIT and UDDF and re-import both, confirming alerts and settings come back.
- [x] 9.3 Update `.agents/rules/activity-files.md` with alerts, settings and the Shearwater/Subsurface dive state, and run `scripts/check-agent-sync.sh`.

## Workflow follow-up

- Archive the change with `/opsx:archive` once the tasks are done and reviewed.
