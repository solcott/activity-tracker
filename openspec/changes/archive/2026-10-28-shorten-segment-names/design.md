# Design

## Context

Candidate names are built in two places in `server/segment-importer`:

- `TrailBuilder` names each trail. A route relation, a joined set of same-named ways and an agency line use their source name, and a branching parent trail gets `<Parent> via <section>` / `<Parent> from <section>` (`viaName`).
- `CandidateCutter` turns trails into candidate names. A whole trail gets its name plus a direction (`nameOf`), a stretch `<trail>: <from> to <to>` with ends from `nameAt`, and a junction span `<trail> to|from <junction>` (`spanName`, with the junction from `junctionName` → `TrailBuilder.familyOf`). It then cuts the name to 100 graphemes (`truncate`).

The raw names also drive logic. Family grouping splits on `" - "` (`familyOf`). `wayNames` tells sections apart. `nameAt` prefers an end that isn't the trail's own name, and `others` compares agency names. That logic has to keep seeing the names exactly as OSM has them.

`refine` (`Refine.kt`) lists the importer's `feed.segment`s, and for each one whose rkey is a candidate id it rewrites `line` alone via `putRecord`, merging into the stored JSON. In the app, `SegmentEffortGroups.kt` cuts a parent effort's trail name at `TRAIL_NAME_ENDS`.

## Goals / Non-Goals

**Goals:**
- Shorten names when they are written out, without changing what any grouping, junction or dedup logic sees.
- Make it possible to add a language by adding a word list, with no other code change.

**Non-Goals:**
- Choosing between OSM `name:<lang>` tags. Names still come from `name`.
- Localising the joining words the importer adds (`from`, `(part n)`, directions).
- Protecting names set by anyone other than the importer. Nothing sets those yet (see Open Questions).

## Decisions

**Shorten each source name, never a composed one.** A new `GenericWords` (one file in the importer) has `shorten(name: String): String` for a single OSM, route or agency name. It splits on the first `" - "` into parent and section, sets aside a trailing `" (…)"` qualifier, removes one trailing generic word from each part, and puts the name back together. Composed names are built from names already shortened this way. *Alternative:* shorten the finished string by splitting on every separator. That would misread a real name that contains " from " or ": ", and would make shortening depend on how a name was composed.

**Trails carry a display name next to their raw one.** `Trail` gets a `label` field: the shortened name, which for a branching parent trail is `"${shorten(Parent)} › ${shorten(section).sectionPart}"` (or `from`). `name`, `family` and `wayNames` stay raw. `CandidateCutter` builds every output name from `trail.label` and from end and junction names passed through `shorten`, so `nameAt`, `junctionName` and `others` don't change. *Alternative:* shorten in `CandidateCutter` only. Then it would have to take `viaName`'s output apart again.

**Stretch ends keep their section and junctions don't.** `nameAt` keeps returning a full way name, so a stretch ending at "Rush Trail - Dreamline Section" gets "Rush - Dreamline". That says where on Rush the stretch ends and keeps two stretches of one trail from sharing a name. `junctionName` still returns the family ("Bonneville Shoreline"), as the spec says today: the junction is picked by name among every way meeting there, so its section wouldn't locate it any better.

**Word lists are keyed by language, and the language is an extract option.** `GenericWords.forLanguage(code)` returns the list for a code, longest phrase first (`Foot Path` before `Path`). For a code with no list it returns an instance that changes nothing. `extract --lang <code>` (default `en`, `[a-z]{2}`) builds it once and passes it to `TrailBuilder.build` and `CandidateCutter`. The language isn't stored in the candidates file, because names are final once extracted. *Alternative:* take the language from the region id (`us-` → en). That would be wrong for regions like `ca-quebec`, and an explicit option is cheaper than a lookup table.

**The guard keeps a generic word when it's all that would be left.** If removing the word leaves nothing, or only "The", "A" or "An", the name stays as it is. Matching ignores case (agency data is often in capitals) and needs a word boundary, so "Trailhead" and "Pathfinder" stay.

**`refine` renames in the same write as the line.** For each importer segment with a candidate, it compares the stored `name` with the candidate's. The new record is the stored JSON with `name` and/or `line` replaced. The record is written only when one of them changed, and once even when both did. `RefineResult` gets a `renamed` list, printed like `moved`. A moved trail can still be renamed, because its candidate id (same ways, same ends) still identifies the trail. *Alternative:* a separate `rename` command. That would be one more step for operators on every import, and they would run the two together anyway.

**The app keeps `" via "` in its separators.** `TRAIL_NAME_ENDS` becomes `": ", " (", " › ", " → ", " via ", " from "`. Keeping `" via "` costs nothing and nests names correctly for any region not yet refined.

## Risks / Trade-offs

- [Two trails shorten to the same name, e.g. "Rattler Trail" and "Rattler Path"] → Names were never unique. Ids are, and the segment screen shows the map. This is accepted.
- [Every OSM rename now reaches published segments at the next `refine`, vandalism included] → `refine` is run by an operator, who sees the renamed list before and after. Its report lists each rename with its old and new name so a bad one is easy to spot.
- [Names lose a hint of what kind of way they are] → Segment search is by map area (`feed.searchSegments`), not by text, so no lookup depends on the dropped words. The sport group and the map still show what the segment is.
- [A wrong `--lang` silently keeps long names] → The report says when a language has no word list. A malformed code is refused.
- [Screenshot and fixture names change] → Update the references in the same change (see tasks).

## Migration Plan

1. Ship the importer and app changes.
2. For each imported region: `extract --lang en`, `stage`, then `refine` with the new candidates file (`scripts/segment-import.sh` does all three). Staged candidates take their new names when staged. Published segments take theirs when refined.
3. Rollback: running the previous importer's `extract` with the old names and the new `refine` would rename the segments back. Nothing else stores the names.

## Open Questions

- When athletes can rename segments, `refine` will need to leave a name the community accepted. How that name is marked (an AppView overlay, or a field on the record) is for that change to decide. Nothing here needs to change before then.
