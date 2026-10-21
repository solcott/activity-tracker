# Tasks

## 1. Lexicon

- [x] 1.1 Following the `lexicon-change` skill, add optional `exerciseLoad`, `staminaStart`, `staminaEnd`, `staminaMin` and `sweatLoss` (with units and bounds in their descriptions) to `lexicons/app/activitytracker/feed/activityZones.json#trainingLoad` and `core/lexicon` `TrainingLoad`, including `isEmpty`. Verify the lexicon serialization tests and a `lexicon-auditor` run report no mismatches.
- [x] 1.2 Bound the new fields in `ActivityStreamsViews.trainingLoad` (AppView). Verify with an `ActivityStreamsTest` case that a zones record holding them is served to the owner, and that an out-of-range stamina of 150 is dropped. Run `./gradlew :server-appview:test`.

## 2. Decoding

- [x] 2.1 Add the five values to `DecodedSummary`, plus a common `SessionFieldNumbers` table and a range check (Garmin manufacturer only). Map them through `DecodedActivityMapper` → `ImportedActivity` → `ActivityDraft`'s training load. Verify with `DecodedActivityMapperTest` cases: present, absent and out of range.
- [x] 2.2 Read them in `GarminJavaFitDecoder`: field 168 through `getTrainingLoadPeak`, and 178 and 205–207 by field number. Verify with a `GarminJavaFitDecoderTest` on a fixture built with those session fields set (extend `FitFixtures`), expecting 187 / 764 / 100 / 70 / 48.
- [x] 2.3 Read them in `SwiftFitDecoder.swift`, first confirming how the Swift SDK exposes unknown session fields. Verify by importing the reference FIT on the iOS simulator and checking the training load card shows the same five values.
- [x] 2.4 Read them in `GarminJsFitDecoder` (`includeUnknownData`). Verify by importing the reference FIT in the web app (`:webApp:wasmJsBrowserDevelopmentRun`) and checking the card.

## 3. Export

- [x] 3.1 Write the five values in `FitWriter`'s session, passing them through `ExportActivity.trainingLoad`. Verify with a `FitWriterTest` round trip (write, then decode with `GarminJavaFitDecoder`) that keeps 187 / 764 / 100 / 70 / 48.

## 4. Activity page

- [x] 4.1 Add exercise load, stamina and sweat loss to `ChartsBuilder.trainingLoad`/`ActivityPage` and the training load card (`PageCardsUi`), with strings and an fl oz conversion. Verify with `ActivityPageTest` (owner sees them; a viewer without zones sees no card) and a `PageFiguresTest` case where 764 ml reads as 25.8 fl oz.
- [x] 4.2 Re-import the reference ride on the desktop app. Verify the training load card shows exercise load 187, stamina 100% → 70% (min 48%) and sweat loss 764 ml (25.8 fl oz in imperial).

## 5. Checks

- [x] 5.1 Run `./gradlew check` and `./gradlew :server-appview:test` and verify both pass.
