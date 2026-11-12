# Design

## Context

See proposal.md for why. `ImportActivityPresenter` already runs in the presenter host; its state is a plain contract with shared
sub-states (`ActivityTypeField`, `ActivityDetailsFields`, `GearField`, `VisibilityFields`, `NewDevicesFields`), each with its own sink,
and an `ActivityPageContent` per preview, the same content the native activity page draws. The Compose screen picks files with FileKit
(`FileKitMode.Multiple`, unfiltered) and sends `FilesPicked` with a `PickedFileSource` per file.

## Goals / Non-Goals

- Goals: a native screen registered in `ScreenRegistry` covering every state the shared screen draws; every text rule asked of Kotlin;
  the Compose screen unchanged to the eye.
- Non-goals: changing what the presenter decides; a native record screen.

## Decisions

- **`FilesPicked` for one file as for several.** The task's events offer `FilePicked` (bytes) too, but the shared picker sends
  `FilesPicked` even for one file: an archive can be gigabytes, and a batch save reopens its files from bookmarks (`BookmarkSourceKeeper`
  needs a `PlatformBacked` source; `ByteArrayImportSource` isn't one). So the view hands the picker's URLs to
  `IosApp.importSources(urls: [String])`, which builds `PickedFileSource(PlatformFile(NSURL))`, the very sources FileKit's iOS picker
  gives; FileKit opens each URL's security scope while its stream is open. `FilePicked` stays for tests.
- **`fileImporter`** with `allowedContentTypes: [.item]` and `allowsMultipleSelection: true`: iOS has no type for `.fit`, so a filter
  would hide the files, as the shared code notes.
- **Layout.** One inset grouped `List` on a phone: the picking section, then (one activity) the preview's sections and the form's, or
  (several) the batch's. From a regular width of 840 points one activity shows as two lists side by side, the form 380 points wide, as
  the shared screen does from 840dp. Save for one activity is the bar's action (the shared screen's iOS layout); a batch's Save, whose
  label counts, is the last section's button, as on every platform. The saved screen and the after-import panel replace the list.
- **The preview reuses the activity page's pieces** (`HeroFigures`, `AvgMaxSection`, `TrainingLoadSection`, `MetricCardSection`,
  `StatsSections`, `RouteMapView` over `RouteMap.of(polyline, gaps)`), with no zones prompt and no gear links (nothing is saved yet).
- **Zone picker.** A row naming the zone opens a sheet with a searchable list; the match is Kotlin's (`ImportActivityTexts.zones(clock,
  query)`, the folding the Compose field uses, moved there).
- **The opened batch row** is SwiftUI's `.sheet` shown while `batch.preview` is set, its swipe down sending `ItemClosed`; Done does too.
- **Enum choices reach Swift as classes.** The duplicate actions, filters and device decisions are listed as small option classes
  (`DuplicateChoice`, `FilterChoice`, `DeviceDecisionChoice`) holding the enum, since a list of enums stops the app.
- **Default titles are worded in Kotlin** through a `TextSource` (`saveEvent(source)`, `saveBatchEvent(source)`): Swift passes
  `CatalogText`, Compose resolves its own through the same function, so neither UI repeats which rows get one.
- **Where the platform matters** (the uploading note: phones keep uploading in the background) the text function takes `mobile`.

## Risks / Trade-offs

- [The sibling session edits `ScreenRegistry.swift`, `KotlinTypes.swift`, `Samples.swift` and the registry tests] → entries are
  appended as their own blocks at the ends.
- [Swift export leaves out or stubs a member] → read the generated Swift for each new function; the stub check fails the build.
- [Release exports no fixtures, so a type named only by samples has no Swift type] → build Release before archiving; texts functions
  name the types the view reads.
- [Picking real files needs files on the device and an account] → snapshots cover every state; a signed-in walk on the local stack is
  noted, and what it couldn't reach goes under "Not verified".
