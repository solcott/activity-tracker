# Tasks

## 1. Lexicon

- [x] 1.1 Add the optional `sections` field to `lexicons/app/activitytracker/feed/path.json`: up to 64 sections, each with a `name` (≤100 graphemes) and `segments`, up to 1000 record keys. Add `sections` to the output of `feed/getPath.json`: `[{ name, segments: [at-uri] }]`. Mirror both in `core/lexicon` (`PathRecord`, and `GetPath.Output` with a `sections` default of empty). Verify with `./gradlew :core-lexicon:jvmTest`, including a round trip with and without sections.

## 2. Importer

- [x] 2.1 Add `section` (shortened) to `Trail` for branching parent trails (`branchOf`, both `via` and `from`) and for NAMED `<Parent> - <Section>` trails. Add `section` and `family` to `Candidate`, set in `CandidateCutter`:
  - parent whole candidates and `own` spans take their trail's section;
  - section-trail pieces take their section;
  - everything else gets none.

  Verify with `:server-segment-importer:test`, using a Rush-shaped fixture (two branches, a `from` entry and a shared trunk) that covers each scenario of `segments/trail-candidates`.
- [x] 2.2 Add `sections` to `PathEntry`, built in `Extract` after dedup by matching kept candidates to their family's trail path by ways. Sort sections and ids. `sections` is optional, so `PATHS_VERSION` stays and older files read as having none. Verify with `:server-segment-importer:test`: the Rush path lists its sections, a dropped duplicate's id is absent, a plain trail lists none, and an older paths file still reads.
- [x] 2.3 In `publish-paths`, write sections filtered to published segment record keys, dropping empty sections. Read `sections` back into `HeldPath`, and rewrite a path whose sections differ. Verify with `:server-segment-importer:test`: a sections-only change rewrites the path, and an unridden section isn't written.

- [x] 2.4 Name a stretch of a family-named trail whose two ends are both its family's sections "<start> → <end>", without "<trail>: ", in `CandidateCutter.nameOf`. Verify with `:server-segment-importer:test` covering both scenarios of `segments/trail-candidates` "Stretches are named after their ends" (the family stretch drops the prefix, a section's stretch keeps it), then re-run `extract` and `refine` for us-utah locally and check `getPath` shows "Rush - Exit Stage Left → Rush - Mystic Rhythms".

## 3. AppView

- [x] 3.1 Add migration `V73__path_sections.sql` (`path_section_segments`) and its Exposed table. In `PathIndex.upsertPath`, validate sections and replace the path's rows (the first section wins for a repeated key). Delete the rows with the path. Verify with `./gradlew :server-appview:test` in `SegmentPathsTest`: sections are stored, re-indexing replaces them, deleting drops them, and a record without sections stores none.
- [x] 3.2 Serve `sections` from `SegmentViews.path`, limited to the returned segments, in record order, with empty sections dropped. Verify with `SegmentPathsTest`: an archived member is left out, a section with nothing visible isn't returned, and segment views are unchanged.
- [x] 3.3 Run the `lexicon-auditor` agent on `feed.path` and `feed.getPath`, and verify it reports no mismatches.

## 4. Shared Trail page

- [x] 4.1 In `:feature-segment-api` (`PathScreen.kt`, `PathTexts.kt`):
  - Add the `PathEntry` (row or section) types, `entries` on `PathContent`, and the `PathState` helpers (`isExpanded`, `offersAllSectionsToggle`, `allSectionsExpanded`, section hover lines).
  - Add the events `ToggleSection` and `SetAllSections`.
  - Add the strings for the expand/collapse-all control and the section row's accessibility label to `:ui-resources`.

  Verify that it compiles with `./gradlew :feature-segment-api:jvmTest`.
- [x] 4.2 In `PathPresenter`, fold rows into sections:
  - a section needs at least two rows;
  - order sections and rows by their longest row, then by name;
  - section details aggregate the rows' wording;
  - the collapsed set is retained and starts empty;
  - toggle and all-toggle events;
  - selecting a row in a collapsed section expands it.

  Verify with `PathPresenterTest` covering each `segments/path-page` scenario.
- [x] 4.3 In `PathUi`, draw section headers with a chevron, rows indented under them, and the expand/collapse-all control. Scroll to the selected row's visible index, and highlight a hovered section's lines. Verify with `PathUiTest`: headers, collapsing, the control appearing at two sections, and hover.
- [x] 4.4 Add a Rush-like sample to `:app-fixtures`, with three sections, plain rows and one section collapsed. Update the screenshot references with `:androidApp:updateDebugScreenshotTestDefaultTestSuite` and review the diff. Verify that `:composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"` and `:androidApp:testDebugScreenshotTestDefaultTestSuite` pass.

## 5. iOS

- [x] 5.1 In `iosApp/iosApp/Screens/PathPage.swift`:
  - Draw sections as `DisclosureGroup`s bound to the presenter's expanded state.
  - Add a toolbar expand/collapse-all control when there are two or more sections.
  - Expand on map selection.
  - Run `./gradlew generateIosResources` for the new strings.

  Verify with an xcodebuild simulator build, and with the snapshot and text tests (`InteractiveMapsSnapshotTests`, `InteractiveMapsTextTests`) on an iOS 26.0 simulator, adding a sectioned sample.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and verify that it passes, including `checkSwiftExportSources`, `checkIosResources` and `composeStabilityCheck` for the new state types.
- [x] 6.2 End to end on the local stack:
  - Re-run `scripts/segment-import.sh us-utah` (or extract and publish only the Rush area), and check that the paths file lists Rush Trail's sections.
  - Open Rush Trail in the desktop app with hot reload and take screenshots that show:
    - Through the Trees Trail and Exit Stage Left as expanded sections, each holding its path, its spans and its section trail;
    - Mystic Rhythms holding its `from` path and spans;
    - family-named spans as plain rows;
    - collapse all and expand all working.

## Workflow follow-up

- After deploy, re-import each published region so its paths gain sections.
- Archive the change once it has been reviewed.
