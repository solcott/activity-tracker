# Proposal

## Why

Round 2 of moving rule-chosen text into shared code (the roadmap's "Import's start times" item). The import screen's starts are chosen
in shared code already, but the record presenter writes each one into `State` as `yyyy-MM-dd HH:mm` text, and `ImportActivityTexts`
shows that text as it is. So a duplicate's "Already in your log: …", the recording a file is added to, a batch's rows and the compare
sheet read "2024-05-12 07:02" on every platform: ISO, and on a 24-hour clock whatever the device's setting. Every other date and time in
the app reads "May 12, 2024" and follows the device's clock.

Separately, the `compose-ios-cleanup` change left the Compose Account page's "Did you keep the export?" dialog. Only iOS's share sheet
asked it (`ArchiveHandOffResult.Shared`), and the Compose UI no longer builds for iOS, so the branch is dead.

## What Changes

- **Import's starts carry their moment, not text.** `ExistingActivityPreview.start` and `ImportRow.start` become an `ImportStart`: the
  instant (epoch seconds) and the UTC offset of the zone it's read in. The presenter no longer formats them.
- **`ImportActivityTexts` writes them like every other start.** A new `DateText.at(epochSeconds, utcOffsetMinutes)` reads "May 12, 2024
  at 7:02 AM": the date written out, and an `AppText.Time` in that offset, which each UI formats on the device's 12- or 24-hour clock.
  `addingTo`, `duplicateWarning`, `rowDetails`, `compareRow` and `compareMatched` use it.
- **The import clock stays text.** `ImportClock.localStart` is the file's zoneless wall clock, the same in every zone, which the zone
  notice quotes; it is unchanged.
- **The Compose Account page loses its kept-shared dialog**, its `ArchiveHandOffResult.Shared` case, its UI test, and the
  `exportSharedAsk` Android screenshot. The contract (`AccountState.askingKeptShared`, `AccountEvent.ExportShared`/`KeptShared`), the
  sample and the strings stay: the native iOS Account view uses them.

Visible text changes:
- Android, desktop, web and iOS alike: a batch row's start ("May 12, 2024 at 7:02 AM · Ride · …"), the compare sheet's two starts, a
  duplicate's "Already in your log: Lunch loop · May 14, 2024 at 12:01 PM · Garmin Fenix 7", and "Adds this file as another recording of:
  …". On a 24-hour device the time reads "07:02" in Compose and the locale's short time in Swift.
- Nothing on the Account page changes on any platform: Android, desktop and web never showed the dialog.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dev/shared-display-text`: the import screen's starts read as a written-out date at a time of day on the device's clock.

## Impact

- `:app-common-api` (`DateText.at`), `:feature-record-api` (`ImportStart`, `ExistingActivityPreview`, `ImportRow`,
  `ImportActivityTexts`), `:feature-record-presenter` (`LOCAL_START`'s callers), `:app-fixtures` (`RecordSamples`), with tests.
- Compose: `AccountUi`, `ArchiveHandOff`, `AccountUiTest`, `AccountScreenshots`. The import UI reads only the texts.
- Swift: `ImportTextTests`; the import views read only the texts.
- iOS 26.0 snapshots of the import batch, duplicate, adding-to and opened-row samples. No Android reference shows an import start
  (the phone-sized batch screenshots end above the rows); the `exportSharedAsk` reference is deleted.
- Roadmap `openspec/explorations/ios-native-ui.md`: the item ticked and a findings section.
