# Proposal

## Why

Imported segment names are too long to read in an activity's segment list: "Bonneville Shoreline Trail - Draper Section: Rush Trail - Dreamline Section to Aqueduct Trail" fills a row before its time. Most of the length is generic words OSM mappers add to every name (Trail, Path, Section) and the English joining words the importer adds (via, to). Corner Canyon's segments are published already, so shorter names only help if published segments take them too.

## What Changes

- The importer drops a generic word (Trail, Path, Foot Path, Footpath, Section) from the end of each OSM name it puts in a candidate name, and from each side of a `<Parent> - <Section>` name. Road and every other word stay. A name that would be left empty, or only an article, keeps its word.
- Generic words are listed per language. `extract` takes the region's language (`--lang`, default `en`). English is the only list to start with, and a language with no list keeps its names as OSM has them.
- Parent trails that branch are named `<Parent> › <section>` instead of `<Parent> via <section>`. Stretches and junction spans use ` → ` instead of ` to `. `from`, `(part n)` and the direction words are unchanged.
- `refine` also gives each published segment its candidate's current name when they differ, whether or not its line changes. This includes segments whose trail moved, which keep their line. **BREAKING** for the rule that `refine` changes only a segment's line: a trail renamed in OSM is now renamed on its segment at the next `refine`.
- In an activity's segment list, nested efforts also recognise ` › ` and ` → ` as the end of the trail name they share with the effort above, so "Rush - Exit Stage Left" under "Rush › Exit Stage Left → Canyon Hollow" shows as "Exit Stage Left".

Out of scope: athletes renaming segments (locals proposing and voting on names). It is a separate change. This one only leaves room for it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `segments/trail-candidates`: candidate naming: generic words dropped per language, `›` and `→` separators, `extract`'s language option.
- `segments/segment-import`: `refine` renames published segments to their candidate's name, and the importer may change a segment's name as well as its line.
- `segments/segment-efforts`: the separators that end a parent effort's trail name when nesting.

## Impact

- `server/segment-importer`: a new name shortener; `TrailBuilder` (parent trail names), `CandidateCutter` (stretch and span names), `Extract`/`Main` (`--lang`), `Refine` (renames); their tests. `scripts/segment-import.sh` passes `--lang` through.
- `app/screens` `SegmentEffortGroups.kt` (nesting separators), the activity detail fixtures, `GroupEffortsTest`, and the Android screenshot references that show segment names.
- No lexicon, AppView or migration change: the AppView already indexes a renamed `feed.segment`, and restaging replaces candidates' names.
- Operators need to re-extract, stage and `refine` each imported region to rename what is already published.
