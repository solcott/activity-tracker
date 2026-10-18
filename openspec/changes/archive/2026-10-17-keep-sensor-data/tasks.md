# Tasks

## 1. Lexicons and models

- [x] 1.1 Use the `lexicon-change` skill to update the lexicons, with the units in design.md's table. In `feed/activityStreams.json`, add the eleven new int channel properties and their `StreamChannel` known values. In `feed/activity.json`, add `cycles`, `avgStrokeDistance` (centimeters) and `utcOffset` (minutes, −720 to 840). In `feed/activityZones.json#trainingLoad`, add `hrvRmssd` (milliseconds). Verify that the JSON parses and that the `lexicon-auditor` agent reports no mismatch once 1.2 lands.
- [x] 1.2 Mirror the new fields in `core/lexicon`: the `ActivityStreamData` fields, each with a `StreamChannel` token and an `INT_CHANNELS` entry, the three `ActivityRecord` fields and `TrainingLoad.hrvRmssd`. Verify that `ActivityStreamsSerializationTest` (including its every-`INT_CHANNELS` round trip) and `RecordSerializationTest` round-trip each field through `AtJson`, and that older JSON without them still decodes.

## 2. FIT decoding

- [x] 2.1 Check that the pinned Java, JS (npm) and Swift FIT SDKs expose these: `hr` (`timestamp`, `event_timestamp` after component expansion, `filtered_bpm`); `hrv.time`; `activity.timestamp`/`local_timestamp`; the record fields in design.md's table; `session.total_cycles`/`avg_stroke_distance`; and record developer fields with their resolved `field_description` name and units. Also check that the JS decoder accepts `mergeHeartRates: false`. Record any gap in a KDoc on `FitDecoder`, and bump an SDK only for a patch or minor release.
- [x] 2.2 Add `DecodedHrMessage`, the HRV interval list, the activity message's timestamp and local timestamp, and `DecodedDeveloperField` to `DecodedActivity`. Add the new record fields to `DecodedSample` and `totalCycles`/`avgStrokeDistanceMeters` to `DecodedSummary`. Fill all of them in `GarminJavaFitDecoder`. Verify in `GarminJavaFitDecoderTest` with new `FitFixtures`:
  - a pool swim with `hr` messages and no record heart rate
  - a run with `hrv` messages and Stryd developer fields
  - a ride with cycling dynamics, core temperature and SmO₂
  - an e-bike ride
  - a file with a non-zero UTC offset
- [x] 2.3 Fill the same in `GarminJsFitDecoder`, reading with `mergeHeartRates: false`. Verify that `:core-activityfile:compileKotlinWasmJs` passes (wasm tests are disabled; 7.2 checks the web import).
- [x] 2.4 Fill the same in `iosApp/iosApp/SwiftFitDecoder.swift`. Verify that the iOS app builds with the `xcodebuild` command in CLAUDE.md (7.2 checks the iOS import).

## 3. GPX and TCX notes

- [x] 3.1 Read `trk/desc` in `GpxParser` and `Activity/Notes` in `TcxParser` into `DecodedActivity.notes`. Verify in `GpxParserTest` and `TcxParserTest`, and in `DecodedActivityMapperTest`, that a single file's notes become the import's description and a file without notes leaves it empty.

## 4. Interpreting the import

- [x] 4.1 Add a common `HrMessages`, a port of Garmin's algorithm as design.md describes, that anchors `hr` event timestamps (with rollover), fills gaps and gives records that lack heart rate the average of the readings in their interval. Call it from `DecodedActivityMapper` before streams and summaries are built. Verify in a new `HrMessagesTest` (anchoring, rollover, gap filling, a record's own heart rate kept), and in `DecodedActivityMapperTest` that the pool swim fixture gets a heart rate stream and average and maximum heart rate.
- [x] 4.2 Add a common HRV summary: keep intervals between 300 and 2000 ms that are within 20% of the previous kept one, require at least 30, and compute RMSSD in whole ms. Carry it on `ImportedActivity` into `TrainingLoad.hrvRmssd` in `ActivityDraft.fromImport`. Verify in a unit test (a known series, an artifact dropped, too few intervals give none) and in `ActivityDraftTest` that it lands in the training load and not on the record or streams.
- [x] 4.3 Add a common `DeveloperFields` table matching `Power`/`Watts`, `Form Power`/`Watts` and `Leg Spring Stiffness`/`KN/m` by trimmed, case-insensitive name and units. Developer power fills only samples without native power. Verify in `DecodedActivityMapperTest` the spec's three developer field scenarios.
- [x] 4.4 Carry the new record fields, form power and leg spring stiffness through `StreamBuilder` into `ActivityStreamData`, in design.md's units. Verify in `StreamBuilderTest` that each lands in its channel, scaled, and that a file without them leaves them null.
- [x] 4.5 Map `cycles` (summed over sessions, doubled for on-foot sports) and `avgStrokeDistance` (centimeters) onto `ImportedActivity` and through `ActivityDraft.fromImport` onto the record. Verify in `DecodedActivityMapperTest` the spec's run (4,200 strides → 8,400) and row scenarios, and in `ActivityDraftTest` that both reach the record.
- [x] 4.6 Compute the UTC offset from the activity message (rounded to 15 minutes; none when local time is missing or out of range). Have `ImportedActivity.inZone` set it from the placed zone, and `ActivityDraft.fromRecording` from the device zone. Verify in `DecodedActivityMapperTest` (+2:00 FIT, missing local timestamp), in an `ImportedActivity` test for a Shearwater dive placed in America/Cancun (−5:00), and in `ActivityDraftTest` for a recording.

## 5. Merging and the AppView

- [x] 5.1 In `ActivityMerge`, fill `cycles`, `avgStrokeDistance`, `utcOffset` and `hrvRmssd` when missing (the new channels already merge through `INT_CHANNELS`). Verify in `ActivityMergeTest` the spec's pedals-add-cycling-dynamics scenario, and that existing values are kept.
- [x] 5.2 Clamp and pass `hrvRmssd` in `ActivityStreamsViews.trainingLoad`. Verify with `./gradlew :server-appview:test` that a served training load carries it, that an out-of-range value is dropped, and that the new channels come back aligned from `bounded`.

## 6. Showing it

- [x] 6.1 In `ChartsBuilder`, add charts for the new channels: torque effectiveness, pedal smoothness and PCO as left/right pairs, assist mode as a step chart, and the rest as lines. Add their `StreamKind`s and strings in `composeApp`. Verify in the charts presenter test that a ride with cycling dynamics gets the paired charts and an app recording gets none, and run `./gradlew composeStabilityCheck`.
- [x] 6.2 Show `cycles` as steps, strokes or pushes by sport (none for other sports) and average stroke distance in the activity's stats. Show RMSSD with the training load. Verify in the detail presenter test the spec's run, ride, owner and follower-without-training scenarios.
- [x] 6.3 Use a FIT file's UTC offset for the import screen's default title and existing-activity preview. Verify in `ImportActivityPresenterTest` the spec's morning run imported in UTC−7.

## 7. Export and integration

- [x] 7.1 In `FitWriter`, write the new record fields in FIT's scales, and the session's `total_cycles` (strides, halved for on-foot sports) and `avg_stroke_distance`. In `ActivityExporter`, build the export zone from `utcOffset`, falling back to the device's zone, for `FitWriter`, `UddfWriter` and `fileName`. Verify in `FitWriterTest`, `FitRoundTripTest` (steps and cycling dynamics survive a round trip; form power isn't written), `UddfWriterTest` and an `ActivityExporter` test the spec's export scenarios.
- [x] 7.2 Import the new fixtures in the desktop, web and iOS apps against the local stack (`scripts/dev.sh`). Check that the swim shows heart rate, the run shows Stryd power, steps and RMSSD to its owner, and the ride shows its cycling dynamics charts. Record any platform whose SDK lacks a message.
- [x] 7.3 Run `./gradlew check` and `scripts/check-agent-sync.sh`, and verify that both pass.

## Workflow follow-up

- Archive the change with `/opsx:archive` once it's implemented and reviewed, syncing its specs.
- Then propose the next steps for `keep-remaining-dive-data`, which also changes `feed/activityStreams.json`.
