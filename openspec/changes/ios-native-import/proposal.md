# Proposal

## Why

Import is one of the last two screens the native activity page opens that iOS still draws with the shared Compose UI: "Add a
recording…" opens it with `combineInto`, and the feed's Add opens it to import files. Every piece it shows already has a native
counterpart (the activity page's figures, cards and map; the forms' type, details, gear and visibility sections; the batch page's
months), so the import screen can be native too.

## What Changes

- **Import is native on iOS** (`ImportActivityView`), drawn from the states `ImportActivityPresenter` publishes:
  - **Picking.** The hint and Choose files (Choose other files once something is read), the system's document picker
    (`fileImporter`, several files, any type, as the shared picker leaves filtering to the presenter), reading's progress with Cancel,
    the failure, a batch save under way (View uploads) and each stopped import with Dismiss.
  - **One activity.** The file's name, its time zone (a row opening a searchable list of zones), the duplicate search's banner with
    Retry, a duplicate's warning and what to do with it, the activity a recording is added to (and why it can't be), and what its page
    will show (start, hero figures, the native route map, the avg/max table, training load, each metric card with its charts, the
    stats); then the type, title and details, threshold changes, gear and its conflicts, the new devices and the profile link, and who
    sees it (not for a recording added to an activity). Save in the bar. From a regular width of 840 points the preview and the form sit
    side by side, as the shared screen lays them out.
  - **Several activities.** What was read (counts, each export's note, a zone per zone-less file, duplicates with Retry, files skipped,
    left out and too large), All / New / Duplicates, the selected count with Select all or Deselect all, an action for every shown
    duplicate, the rows by month (a tick to select, a tap to open), the batch's gear, devices and visibility, the originals' size, a
    failed save, and Save with its progress. An opened row is a sheet: its type and title, a duplicate beside what it repeats, and its
    page as it would publish.
  - **After saving.** A batch's result and its combine suggestions (each opening its activity), View uploads and Done; the after-import
    panel (links not saved, adding new gear to earlier imports with its progress).
- **Picked files reach Kotlin as sources read a range at a time**, as the shared picker's do: `IosApp.importSources(urls)` wraps each
  security-scoped URL in the same `PickedFileSource` FileKit's picker gives, and the view sends `FilesPicked`. A batch save keeps them as
  bookmarks, as it does today.
- **Kotlin chooses what the import screen says** (dev/shared-display-text): `ImportActivityTexts` (`:feature-record-api`) for the
  default titles, the picking section, threshold changes, duplicate warnings and banners, a row's lines, the batch's summary, filters and
  Save, the comparison, the saved screen and the after-import panel, the zone names and search, and the new devices' lines and choices;
  `ImportActivityState.canSave` and `canSaveBatch` and the events Save and Save batch send with their default titles worded
  (`saveEvent`, `saveBatchEvent`). The Compose screen resolves the same texts, with no visible change.

Out of scope: Android, desktop and web beyond the shared rules moving; Create segment (another session's); the record screen.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/batch-import`: importing, one file or several, is native on iOS.
- `feed/import-duplicates`: the native screen offers the same duplicate choices, the retry, and Add a recording.
- `dev/shared-display-text`: the import screen's texts are chosen in shared code.

## Impact

- **`:feature-record-api`:** `ImportActivityTexts`, state getters and event builders; tests.
- **`:feature-record-ui`:** the Compose import screen asks them.
- **`:app-common-api`:** `RecordingTexts.suggestedActivity` (the combine suggestion's line), which `suggestedActivityLabel` resolves.
- **`:iosShell`:** `IosApp.importSources`.
- **`:app-fixtures`:** import variants (a built page, a dive's zone, a duplicate, a recording added, thresholds, a failed save, the
  opened row).
- **`iosApp`:** `ImportActivityView` and its pieces, aliases, samples, previews, snapshots, the registry entry and its tests.
- **Docs:** the roadmap's phases, findings and open items; `.agents/rules/compose-screens.md`.
- **Risk carried:** the Create segment session edits the same shared Swift files; entries are appended as their own blocks.
