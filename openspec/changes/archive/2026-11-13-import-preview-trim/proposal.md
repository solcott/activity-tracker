# Proposal

## Why

The import leftovers change found more of `ImportPreview` that no UI reads (`openspec/explorations/ios-native-ui.md`): since the import
preview became the activity page, the page draws the file's figures from the activity it would publish, and the preview's own copies of
them are filled by the presenter and read only by its tests. Each one is surface both UIs and Swift export carry for nothing.

## What Changes

- `ImportPreview` keeps only `fileName`, `polyline`, `polylineGaps`, `clock` and `duplicate`, which the UIs read.
- `elapsed`, `device`, `elevationLossMeters`, `cadence`, `power`, `shifting`, `mtb`, `sensors` and `hasStreams` go, with
  `CadencePreview`, `PowerPreview`, `ShiftingPreview`, `MtbPreview`, `SensorPreview` and the presenter code that fills them.
- The presenter tests that checked them check the built page's figures instead.

Nothing shown changes on any platform.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none: an internal cleanup with no change in behaviour, so the change skips specs)

## Impact

- `:feature-record-api` (`ImportActivityScreen.kt`), `:feature-record-presenter` (`ImportActivityPresenter.kt` and its tests),
  `:app-fixtures` (`RecordSamples`), `ImportActivityTextsTest`.
- The Swift export of `ImportPreview` loses those properties; no Swift source reads them.
