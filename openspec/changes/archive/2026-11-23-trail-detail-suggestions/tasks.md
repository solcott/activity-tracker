# Tasks

## 1. Lexicon

- [x] 1.1 Add `feed/trailDetailSuggestion.json` (subject, detail open enum, value `trailAttributes`, createdAt) and `feed/trailDetailVote.json` (a suggestion strongRef or segment/detail/value, direction `up`/`down`/`still`/`changed`, createdAt). Add `sources` to `feed.defs#trailAttributes`. Add `trailSources`, `trailSuggestions` (with the suggestion view), `trailConfirmations`, `canSuggestTrailDetails`, `trailDetailCooldowns`, `canVoteOnTrailDetails` and `recentEffort` to `segmentView`. Verify: the JSON parses, and the lexicon tests pass.
- [x] 1.2 Mirror the lexicons in `core/lexicon`: records, views, `Nsid` constants, and both NSIDs in `indexedCollections`. Add the vote to `space/athlete.json`, `AthleteSpaceDeclaration` and `GrantFeature.byCollection`. Verify: `RecordSerializationTest` round-trips both records, and `AthleteSpaceDeclarationTest` and `GrantStatusTest` pass (`./gradlew :core-lexicon:jvmTest :core-data:jvmTest`).
- [x] 1.3 Run the `lexicon-auditor` agent on the new and changed NSIDs. Verify: it reports no mismatches, apart from the layers still pending.

## 2. Importer sources

- [x] 2.1 Track, per attribute, the length of line each source gives it in the combine step, and write `sources` with the longest. Bump the candidates and paths file versions, and carry `sources` through `stage`, `publish`, `refine` and `publish-paths`. Verify: a `TrailAttributeCombinerTest` case gives the mixed USFS/OSM scenario, and the version tests refuse the old formats.

## 3. AppView: indexing

- [x] 3.1 Add migration `V77__trail_detail_suggestions.sql`: the suggestions, votes and adopted tables, plus `segments.trail_effective`, with the matching `Tables.kt` entries. Verify: `./gradlew :server-appview:test` migrates on H2.
- [x] 3.2 Index `feed.trailDetailSuggestion` from repos only. Apply the regular, active, not-a-lift, suggestable-detail, vocabulary, new-value, one-open-per-detail and cooldown checks, and handle edits (votes cleared, time open restarted), deletes and closed rewrites. Verify: `TrailDetailSuggestionsTest` covers each scenario in the spec's suggestion requirements.
- [x] 3.3 Index `feed.trailDetailVote` from the `interactions` space only, keeping the latest per voter and subject, with delete. Verify: tests for a repo vote ignored, a changed vote, and a confirmation naming a value.

## 4. AppView: adoption and views

- [x] 4.1 Write `TrailDetailRules` and `TrailDetailAdoption`, sharing the rider window, vote counting and label filter with `SegmentNaming`. It adopts, supersedes, rejects and expires per (segment, detail), and recomputes `trail_effective` with sources, also after `refine` and label changes. Run it from `SegmentMatcher` after the naming pass. Verify: tests for the quiet segment, too soon, two qualifying, cooldown, voted down, re-import after adoption and adopted value taken down.
- [x] 4.2 Serve effective `trail` and `trailSources` on every segment view (athletes' segments included). On `getSegment`, also serve confirmations (365-day count, month only at 2+, may-have-changed), open suggestions with totals and the viewer's vote (block and mute filtered), and the viewer's may-suggest, cooldowns, may-vote and recent effort. Verify: `SegmentsTest` and `TrailDetailViewsTest` cover the view scenarios, including the athlete's segment, one confirmation and a blocked author.
- [x] 4.3 Update `.agents/rules/appview.md` and the CLAUDE.md data-flow paragraph: the new private record in `interactions` and what the AppView stores. Verify: `scripts/check-agent-sync.sh` passes.

## 5. Shared client logic

- [x] 5.1 In `core/data`, write suggestions and votes through the outbox (the suggestion to the repo, votes to `interactions`), with optimistic updates of the cached segment view. Verify: jvmTest with `MockEngine` and an in-memory database.
- [x] 5.2 Extend the `:feature-segment-api` contracts and texts:
  - the source caption per row (`TrailTexts`);
  - the suggestion count;
  - may-have-changed;
  - last confirmed by month;
  - the detail sheet state;
  - the picker values in words, with current and already-suggested marked;
  - the missing details;
  - the pending-question row ("Paved?");
  - the quick-check details;
  - `PathTexts` roll-up rows ("Varies along the trail", the segment list, and "Dogs vary" in the folded line).

  Add the strings to `:ui-resources`, then run `generateIosResources`. Verify: `TrailTextsTest` and `PathTextsTest` cover the spec's row scenarios.
- [x] 5.3 Presenter logic in `-presenter`:
  - Open the sheet for riders only.
  - Record Still right, It's changed (opening the picker for regulars), agree and disagree.
  - Turn a pick of an already-suggested value into an agree.
  - Handle Add a missing detail.
  - Run the quick check, with its local once-per-season and Not now memory.

  Verify: presenter tests for each flow, including "All still right" writing one confirmation per listed detail.

## 6. Compose UI

- [x] 6.1 Build the Segment page in `-ui`:
  - source captions and the community badge on rows;
  - the detail bottom sheet;
  - the full-screen suggest picker;
  - Add a missing detail and the empty-segment invitation;
  - the quick-check card;

  all from `ui/adaptive` wrappers. Add the sample states to `:app-fixtures`. Verify: `SegmentUiTest` cases pass, and `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite` references are reviewed against the design canvas.
- [x] 6.2 Add the Trail page roll-up rows with their segment links, and the varying folded line. Verify: a `PathUiTest` case for "Dogs vary" and re-recorded Trail page screenshots.

## 7. iOS

- [x] 7.1 Build native SwiftUI versions on the Segment screen: source captions, the medium-detent detail sheet with Done, the picker, the missing-detail invitation and the quick check. Use `iosApp/iosApp/Design`, keeping row controls trailing. Verify: new iOS snapshot tests on the iOS 26 simulator, and a run on iPhone 17 Pro.
- [x] 7.2 Add the Trail screen roll-up rows. Verify: a snapshot test, and `:iosExport:checkSwiftExportStubs` passes in the iOS build.

## 8. Integration

- [x] 8.1 Run `./gradlew check`. On the local stack, have a seeded regular suggest a value, have two riders agree, run the pass with the thresholds lowered, and confirm the Segment page shows "Community · N riders" on Android and iOS. Verify: `check` passes, and screenshots show the adopted value.
