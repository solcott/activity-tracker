# Proposal

## Why

A trail family such as Rush (OSM ways named `Rush - <Section>`) gives the Trail page 15 or more flat rows ordered by length, so related segments end up far apart. "Rush › Through the Trees" (a whole path through the family), its spans "→ Bonneville Shoreline" and "→ Canyon Hollow", and "Rush - Through the Trees Trail" (the section alone) all belong to one branch of the trail. Today they're scattered among the Exit Stage Left and Mystic Rhythms rows. Segment groups can't join them, because a segment group means one line, and these are different lines.

## What Changes

- **A new level between path and segment group: the section.** When the importer names a candidate, it already knows which section of its family the name comes from: the `›`/`from` section of a parent trail, the section of a `<Parent> - <Section>` trail, or neither. It now records that section.
- **Which segments join a section:**
  - Parent trails `<Parent> › X` and `<Parent> from X` join X.
  - Junction spans named after their own parent trail join that trail's section.
  - The `<Parent> - X` section trail joins X, both its whole candidate and its stretches.
  - Everything else is in no section: spans named after the family (`<Parent> → Y`), stretches of a plain `<Parent>` trail, relation trails, agency trails, and athlete-made segments.
- **Paths carry their sections.** The paths file, and `feed.path` in a new optional field `sections`, list each section of the path's family. Each section is a name, without generic words, and the record keys of its candidates. `publish-paths` refreshes a path when only its sections change.
- **`feed.getPath` serves sections.** Each section is its name and the URIs of the returned segments in it. Sections with no visible segments are left out. Segment views and `feed.segment` are unchanged.
- **The Trail page lists sections as collapsible rows,** expanded by default:
  - A section with fewer than two segment-group rows is listed as a plain row.
  - When there are two or more sections, the list offers expand all and collapse all.
  - Selecting a line in a collapsed section expands it.
  - The native iOS page does the same with system disclosure controls.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `segments/trail-candidates`: candidates carry the family section their name comes from.
- `segments/path-import`: the paths file and published paths list each family section's candidates, and a section change refreshes a published path.
- `segments/segment-paths`: `feed.path#sections` and the sections returned by `feed.getPath`.
- `segments/path-page`: the page lists segment groups folded into collapsible sections, expanded by default, with expand all and collapse all. Selection and hover cover sections. The native iOS page does the same.

## Impact

- **Lexicons:**
  - `feed.path` gains optional `sections`.
  - `feed.getPath` output gains `sections`.
  - The Kotlin mirror in `core/lexicon` (`PathRecord`, `GetPath.Output`) changes to match.
- **AppView:**
  - A Flyway migration adds `path_section_segments`.
  - `PathIndex` stores sections, from the importer's repo only.
  - `SegmentViews.path` serves them.
- **Importer:**
  - `TrailBuilder` and `CandidateCutter` track sections.
  - `Candidate` and `PathEntry` gain fields, and the paths file version goes up.
  - `Extract` assigns sections after dedup.
  - `PublishPaths` writes them and refreshes on change.
- **Clients:**
  - `:feature-segment-api` gains `PathState` entries and section events.
  - `PathPresenter` folds rows into sections, and `PathUi` draws section rows.
  - Strings, `:app-fixtures` samples and screenshot references change.
  - `iosApp` `PathPage.swift` changes.
- **Data:** existing paths gain sections when a region is imported again (`scripts/segment-import.sh <region>`). Until then, pages list flat rows as today.
