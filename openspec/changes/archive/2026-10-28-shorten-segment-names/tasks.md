# Tasks

## 1. Generic words

- [x] 1.1 Add `GenericWords` to `server/segment-importer`: `forLanguage(code)`, with the English list (Foot Path, Footpath, Trail, Path, Section, longest first) and an instance that changes nothing for any other code, plus `shorten(name)` per design.md (split on the first `" - "`, set aside a trailing `" (…)"`, drop one trailing whole word from each part ignoring case, keep it when only nothing or an article would be left). Verify with a new `GenericWordsTest` covering every scenario of "Generic words are dropped from names" plus "Trailhead", "Pathfinder" and a name with no list (`de`).

## 2. Candidate names

- [x] 2.1 Add `label` to `Trail` and set it in `TrailBuilder.build` from `GenericWords` (taken as a parameter), with `viaName` building `<Parent> › <section>` / `<Parent> from <section>` from shortened parts. Keep `name`, `family` and `wayNames` raw. Verify `TrailBuilderTest`'s "Alternatives that rejoin" and "Entry that joins partway down" cases expect "Rush › Exit Stage Left", "Rush › Through the Trees Trail" and "Rush from Mystic Rhythms", and that its family and junction cases are unchanged.
- [x] 2.2 Make `CandidateCutter` take `GenericWords` and build names from `trail.label`: `<trail>: <from> → <to>` with shortened full end names, `<trail> → <junction>` / `<trail> from <junction>` with the shortened family, then `truncate`. Verify `CandidateCutterTest` expects the scenarios of "Candidates are named after their trail" and "Junction spans are named after their junction" (including "Bonneville Shoreline - Draper: Rush - Dreamline → Aqueduct"), and that candidate ids are unchanged.
- [x] 2.3 Update the other `CandidateCutter(data)` callers (`AgencyReaderTest`, `CandidateDedupTest`) and verify `./gradlew :server-segment-importer:test` passes with `AgencyReaderTest` asserting the title-cased agency trail "Wilkinson Trail" is named "Wilkinson (northbound)" / "(southbound)".

## 3. Extract language

- [x] 3.1 Add `--lang <code>` to `extract` in `Main.kt` (default `en`, `[a-z]{2}`, otherwise usage), pass `GenericWords.forLanguage` into `Extract`, and report when the language has no list. Update the usage text. Verify in `ExtractTest` / the argument-parsing tests: default English, `de` keeps names and is reported, `english` is refused.
- [x] 3.2 Pass `--lang` through `scripts/segment-import.sh` (default `en`), update its usage line and the `extract` example in `CLAUDE.md`/`AGENTS.md`. Verify with `scripts/segment-import.sh` printing its usage and `bash -n`.

## 4. Refine renames

- [x] 4.1 In `Refine.kt`, compare each importer segment's stored `name` with its candidate's. Write one `putRecord` with the stored JSON plus the new `name` and/or `line` when either changed, including for a moved trail (name only). Add `renamed` to `RefineResult` and print each rename with its old and new name. Verify in `RefineTest`: long name renamed with everything else kept, moved trail renamed with its line kept, name and line in one write, second run writes nothing, athlete segments untouched.

## 5. App nesting

- [x] 5.1 Set `TRAIL_NAME_ENDS` in `app/screens/.../detail/SegmentEffortGroups.kt` to `": ", " (", " › ", " → ", " via ", " from "`. Verify `GroupEffortsTest` covers every scenario of "Nested efforts drop their parent's trail name", the old-name scenario included.
- [x] 5.2 Update the segment names in `app/fixtures` `ActivityDetailSamples.kt` to the shortened forms, regenerate the screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, and check the changed images show the short names with nesting intact.

## 6. Integration

- [x] 6.1 On the local stack (`scripts/dev.sh`), import a region whose segments are already published with long names (the Utah extract around Corner Canyon, or Vermont), then run `refine`. Verify the report lists the renames and the activity's segment list in the app shows the names in proposal.md.
- [x] 6.2 Run `./gradlew check` and verify it passes.

## Workflow follow-up

- Re-extract, stage and refine every imported region in production with the new importer.
- Archive the change after review.
