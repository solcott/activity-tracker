# Design

## Context

- **The importer names sections but doesn't keep them.**
  - `TrailBuilder.parentTrails` picks a branching parent trail's section in `branchOf` (`via`/`from`) and bakes it into `Trail.label` only.
  - `CandidateCutter.spanName` decides whether a span is named after its own trail (`own`) or after the family.
  - A NAMED trail of `<Parent> - <Section>` ways carries the section only in its name.
- **Imported segments are keyed by candidate id.** So a candidate id is the record key of its `feed.segment` in the importer's repo, and a path can name segments without knowing their URIs or CIDs.
- **Paths are published last and refresh in place.**
  - `publish-paths` already compares a held path's name, lines and sports with the paths file and rewrites it under its key (`PublishPaths.kt`).
  - `PathIndex.upsertPath` re-indexes it and keeps `path_segments` current.
- **Grouping on the Trail page is the client's job.**
  - `getPath` returns segment views.
  - `PathPresenter.contentOf` filters them by sport and builds `SegmentGroupRow`s (longest first).
  - `PathUi` lists them in a `LazyColumn`, and the iOS `PathPage.swift` lists them in a `List`.

## Goals / Non-Goals

**Goals:**
- Sections come from what the importer already decided when naming. Nothing is reparsed from names, so locals' renames and generic-word rules don't matter.
- Nothing changes for segments: no `feed.segment` field, no change to segment views, efforts or groups.
- Paths without sections, and older clients, behave as today.

**Non-Goals:**
- Sections for athlete-made segments, or sections worked out from geometry in the AppView.
- Shortening row names inside a section (for example, showing "→ Canyon Hollow" under Through the Trees Trail). Rows keep their segment group names.
- Sections in browse or name search. They appear on the Trail page only.

## Decisions

### Sections live on the path record, keyed by segment record key
- **Record shape:** `feed.path#sections` is an array, at most 64 items, of `{ name: string (≤100 graphemes), segments: [string record key, ≤1000] }`.
- **Why the path:** a section is relative to a trail family, which is exactly a trail path. The same segment can lie on another path, such as a shared-tread road, where the section would mean nothing.
- **Why record keys:**
  - The importer knows them at extract time, as candidate ids.
  - They survive renames and refines.
  - The AppView resolves them as `at://<importer>/app.activitytracker.feed.segment/<rkey>`.
- **Alternatives considered:**
  - A `section` field on `feed.segment`. It would need republishing every imported segment, would mean nothing on other paths, and would put importer-only data on a general record.
  - Section lines on the path, with the AppView assigning segments by geometry. A segment over a shared trunk plus a branch is ambiguous, and the importer has already answered that question.
  - Parsing names in the client. Rejected in the proposal.

### Importer
- **`Trail.section: String?`, shortened:**
  - Set by `parentTrails` from `branchOf` when the family keeps more than one path.
  - Set by `namedTrails` for a trail whose name has `" - "`: `words.shorten(name.substringAfter(" - "))`.
  - Section names use the same `words.shorten` as candidate labels, so "Through the Trees Trail Section" becomes "Through the Trees Trail", matching the name in "Rush › Through the Trees Trail".
- **`Candidate.section: String?`** comes from `CandidateCutter`:
  - for a parent trail's whole candidate, or a span with `own`, the trail's section;
  - for a NAMED section trail's pieces, its section;
  - otherwise null.
- **`Candidate.family: String?`** is the trail's family (`trail.family ?: familyOf(trail.name)` for section trails). It is used only to match a candidate to its path, and isn't written to staged candidates.
- **`PathEntry.sections: List<PathSection>?`** (`name`, `candidates`):
  - `Extract` builds it after `CandidateDedup`. For each kept candidate with a section, it picks the family's trail path whose source ways include the candidate's ways. `TrailPaths` already tracks each path's ways, and it keeps them on an internal field that isn't serialized.
  - Sections are sorted by name and their ids sorted, so the output is stable.
  - `PATHS_VERSION` stays as it is: `sections` is optional, so older paths files read as having no sections.
- **`publish-paths`:**
  - It already asks the AppView for the region's live segments (`pathsNeeded`). From that set, it filters each section's ids to the importer's published record keys, then drops empty sections.
  - The refresh test adds `old.sections != path.sections`, and `HeldPath` reads `sections`.

### AppView
- **Migration `V73__path_sections.sql`:** `path_section_segments(path_uri, segment_uri, section_name, section_order)`, primary key `(path_uri, segment_uri)`, plus an index on `path_uri`. This is H2-portable SQL.
- **`upsertPath`:**
  - Validates sections, dropping blank names and over-long lists.
  - Deletes the path's rows and inserts one per `(section, rkey)`.
  - If an rkey appears in several sections, the first section wins, enforced by the primary key with an ignore on insert.
  - Deleting a path deletes its rows.
  - This runs only for importer-repo paths, which are the only paths indexed.
- **`SegmentViews.path`:**
  - After loading the listed segments, reads `path_section_segments` for the path.
  - Keeps the rows whose segment is in the result.
  - Groups them by section in `section_order`, returning `sections: [{ name, segments: [uri] }]`.
  - Sections with no rows are dropped.
  - No visibility logic is new: sections only filter the already-visible result.

### Client
- **`GetPath.Output.sections: List<PathSectionView> = emptyList()`**, so older AppViews decode to none.
- **`PathContent` holds `entries: ImmutableList<PathEntry>`,** a sealed type with two cases, `Row(group)` and `Section(name, rows, …)`, as well as `groups`, a flat list kept for the map, selection and Swift.
- **`contentOf`:**
  - Builds the rows as today.
  - Maps each row's shown segment URI to its section.
  - Folds sections holding two or more rows into an entry.
  - Sorts the entries by their longest row, then by name.
  - The section's details reuse the row wording, by aggregating the rows' members as `SegmentGroupRow`'s detail builder does.
- **Expanded state:** `collapsed: Set<String>` of section names, retained in the presenter (`rememberRetained`), starting empty so every section is expanded. Events:
  - `ToggleSection(name)`
  - `SetAllSections(expanded)`
  - `Select` / `TapRow` of a URI in a collapsed section first removes that section from `collapsed`.
- **`PathState`** exposes:
  - `entries`;
  - `isExpanded(section)`;
  - `offersAllSectionsToggle` (two or more sections);
  - `allSectionsExpanded`, which picks the control's words: "Collapse all" when every section is expanded, else "Expand all";
  - `sectionLines(section)`, for hover.
- **`PathUi`:**
  - Each section is a header item with a chevron, name and details. Its rows follow, indented, when expanded.
  - The expand/collapse-all control sits beside the list's top, next to "Show all sports".
  - Scrolling to a selected row uses the index of its item in the flattened visible list.
  - Hovering a header passes all its rows' URIs to `segmentsMap(hovered:)`, which gains a set overload.
- **iOS:** a `DisclosureGroup` per section, with `isExpanded` bound to the presenter state through the events, and a toolbar button for expand/collapse all. Strings come from `generateIosResources`.

## Risks / Trade-offs

- **[Sections appear only after a re-import]** → Paths without sections show flat rows, as today. Run `scripts/segment-import.sh us-utah` after deploying the AppView.
- **[A record key is listed but its segment gets re-lined off the path]** → It's simply not in `getPath`'s result, so the section filter drops it.
- **[Ids of segments published after the paths]** → `segment-import.sh` always publishes paths last. A later `publish` without `publish-paths` leaves new segments loose until the next run.
- **[Large families]** → Families are capped at 4 parent trails per sport group, and a section's ids are bounded by the 1000 cap. The importer logs and truncates rather than failing the record.
- **[Section names that look wrong]** → For example "Through the Trees Trail", where OSM's "… Trail Section" loses only one generic word. This is the same as candidate names, consistent and unchanged by this design.

## Migration Plan

1. Deploy the AppView with the migration. The `sections` field is optional, and older records index as having none.
2. Ship the clients. A missing `sections` decodes to empty.
3. Re-run each region's import. `publish-paths` rewrites the paths whose sections changed.

Rollback: older AppViews and clients ignore the unknown field, and the table can stay.
