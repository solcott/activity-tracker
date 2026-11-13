# Proposal

## Why

Three leftovers of the native import screen (`openspec/explorations/ios-native-ui.md`): the zone notice still quotes the file's zoneless
start as ISO text on a 24-hour clock ("its start (2024-10-16 17:30)"), unlike every other start; the import state still carries dive and
gear-time data no UI has drawn since the import preview became the activity page; and `ImportSnapshotTests`' wide iPad page fails even
when its suite runs alone.

## What Changes

- **The import clock carries its wall clock, not text.** `ImportClock.localStart` becomes the zoneless `LocalDateTime` the file wrote.
  A new `DateText` function writes one as the date written out at its time of day ("Oct 16, 2024 at 5:30 PM"), the time an
  `AppText.Time` each UI formats on the device's 12- or 24-hour clock. The zone notice uses it in both UIs; the presenter no longer
  formats it (`LOCAL_START` goes).
- **Dead import preview state goes.** `ImportPreview.dive` (`DivePreview` and its gas, tank, site and depth-profile types) and
  `ShiftingPreview.gears` (`GearTimePreview`) are read by no Compose or Swift UI, sample or screenshot test; they go with the presenter
  code that fills them and the tests that check it. Anything of them a UI still reads stays.
- **Import's wide iPad snapshot passes reliably.** The cause of the page drawn about 20 points higher than its reference is found and
  fixed, without raising the tolerance or dropping the sample.

Visible text changes: the import zone notice on every platform, for a dive log or any file whose times have no zone ("…its start (Oct 16,
2024 at 5:30 PM)…"), and the time follows the device's clock. Nothing else shown changes.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `dev/shared-display-text`: the import clock's start is written by shared code like every other start.
- `dev/ios-snapshot-tests`: a suite's snapshots compare the same when it runs alone, run after run.

## Impact

- `:app-common-api` (`DateText`), `:feature-record-api` (`ImportClock`, `ImportPreview`, `ShiftingPreview`, `ImportActivityTexts`),
  `:feature-record-presenter`, `:app-fixtures` (`RecordSamples`), their tests, the Swift import tests and snapshots.
- iOS 26.0 references whose zone notice changes are rerecorded; Android references only if a screenshot shows the notice.
