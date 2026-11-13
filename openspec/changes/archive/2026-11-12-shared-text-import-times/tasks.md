# Tasks

## 1. Shared starts

- [x] 1.1 Add `DateText.at(epochSeconds, utcOffsetMinutes)` with tests (12- and 24-hour, another offset crossing midnight)
- [x] 1.2 Add `ImportStart` to `:feature-record-api`; `ExistingActivityPreview.start` and `ImportRow.start` take it; `ImportActivityTexts` writes it with `DateText.at`
- [x] 1.3 The record presenter builds `ImportStart`s (`AddingRecording`, `batchRows`, `duplicateInfo`); `LOCAL_START` stays only for the import clock
- [x] 1.4 Update `RecordSamples`, `ImportActivityTextsTest`, the presenter tests and `RecordTextNamesTest`

## 2. Account page

- [x] 2.1 Remove the Compose `KeptSharedDialog`, `ArchiveHandOffResult.Shared` and its UI test; keep the contract, sample and strings iOS uses
- [x] 2.2 Leave `exportSharedAsk` out of the Android screenshots and delete its reference

## 3. Pictures and Swift

- [x] 3.1 Rerecord the Android screenshot references whose import starts changed, look at them, and pass `:androidApp:testDebugScreenshotTestDefaultTestSuite` (none changed: the phone-sized batch screenshots end above the rows)
- [x] 3.2 Update `ImportTextTests` and the iOS 26.0 import snapshots whose starts changed, and look at them

## 4. Verification

- [x] 4.1 `./gradlew check -Pkotlin.incremental=false` passes
- [x] 4.2 An ad-hoc-signed iOS Release build passes
- [x] 4.3 `xcodebuild test` passes on an iOS 26.0 and an iOS 18.4 simulator (the 18.4 run on a build made for it): 446/446 on 18.4; on 26.0, 445/446 in the full run, the one failure `zoneSheet` in `ImportSnapshotTests`, which passed alone (a later lone rerun at a load average over 300 also caught `wide()`'s chart and the zone sheet's close button half drawn, no text)
- [x] 4.4 Tick import's start times off in the roadmap's checklist and add this round's findings

## Workflow follow-up

- Archive the change and sync its specs (`dev/shared-display-text`).
