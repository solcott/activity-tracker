# Tasks

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/actor/groupPrefs.json` (`literal:self`, `mode` open string `off|connected|anyone`, `updatedAt`), its `GroupPrefsRecord` and `GroupMode` constants in `core/lexicon`, and its `Nsid`. List it in `space/athlete.json`. Verify an `AtJson` round-trip test, including that an unknown `mode` decodes.
- [x] 1.2 Add the optional `groupExcluded` to `feed/segmentTrack.json` and `SegmentTrackRecord`. Verify a round-trip test, with the field absent and with it present.
- [x] 1.3 Add `feed.defs#groupPartner` (`author` profileViewBasic, `uri`) and the optional `activityView.group` to `feed/defs.json` and `Views.kt` (`@Immutable`, `ImmutableList`-friendly). Verify with the `lexicon-auditor` agent over `actor.groupPrefs`, `feed.segmentTrack` and `feed.defs`.

## 2. AppView: storage and indexing

- [x] 2.1 Write `V47__group_activities.sql`: `group_prefs`, `activity_group_pairs` with the ordered-pair key and author and `activity_b` indexes, and `segment_tracks.group_excluded`, all H2-portable. Add the Exposed tables to `db/Tables.kt`. Verify that `:server-appview:test` boots the migration on H2.
- [x] 2.2 Index `actor.groupPrefs` from `self` only (beside `SEGMENT_COLLECTIONS` in `Indexer.spaceCollections`), plus `upsertGroupPrefs` and `deleteGroupPrefs`. Off or delete drops the author's pairs in the same transaction. Off-to-on queues a `group` job for each of their groupable tracks. Also add drops to `dropSpace(self)` and `deleteActor`. Verify with indexer tests: a repo copy is ignored, off drops pairs, and an unknown mode counts as off.
- [x] 2.3 Index `segment_tracks.group_excluded` from the track record. A change to true drops the activity's pairs; a change to false queues a `group` job. Verify with indexer tests for leave and rejoin.
- [x] 2.4 Delete the pair rows between two authors when either blocks the other (`upsertBlock` and the `app.bsky.graph.block` path). Verify with an indexer test.

## 3. AppView: purpose-gated tracks

- [x] 3.1 Add `groupableTracks()` to `db/SegmentTrackQueries.kt`, and split `trackToMatch` / `none()` in `SegmentMatcher` per D2. Cells are written when either purpose admits the track, efforts and riders only under segment consent, pairs only under group consent, and cells are dropped only when neither does. Make `MatchInputs.matchable` consider both. Verify with `SegmentMatcherTest` cases: matching off with grouping on keeps cells and drops efforts and riders, and grouping off with matching on keeps efforts and drops pairs (segment-track delta "The AppView keeps segment tracks only while matching is on").
- [x] 3.2 Split `dropSegmentTracks` in `index/SegmentIndex.kt` into purpose drops called from `upsertSegmentPrefs` and `deleteSegmentPrefs` and the new group-prefs handlers. Delete track rows and cells only when both consents are off. Verify with indexer tests for every on/off combination.

## 4. AppView: matching

- [x] 4.1 Implement the pure `GroupMatch.together(a, b)` with named threshold constants (D4). Verify with unit tests: side by side pairs, a 2-hour offset doesn't, routes km apart don't, a 10-minute shared start of a 2-hour ride doesn't, a shared start inside a privacy zone pairs on the shown parts, a <5 min overlap doesn't, and paddle tolerance.
- [x] 4.2 Add `exactStart(activity)` (start from `effectiveRecord` over all `activity_details` rows) and `pairVisibility(row)` (narrowest of the tier and the Start time and Map categories) beside `effortVisibility`, and refresh the pair tiers in `retier`. Verify with tests: a hidden start still matches, and narrowing Map retiers the pair.
- [x] 4.3 Add the `GROUP` job kind to `SegmentJobs` and `matchGroup(trackUri)` to `SegmentMatcher`:
  - candidate SQL (other author, not a combined member, same `SegmentSport` group, window overlap, shared cell, capped);
  - the pair rule (modes, `effectiveFollow`, `blocked`);
  - the blob fetch with a per-job cache, `together`, and replacing the activity's pairs.

  Queue it from `matchTrack` and from `MatchInputs.activityChanged` on a start or sport change. Verify with `GroupMatcherTest`:
  - Ride + MTB ride pair, ride + run don't, indoor doesn't;
  - Anyone + Anyone pairs, Connected without a follow doesn't, Connected with a one-way effective follow pairs, a pending request doesn't, a block doesn't;
  - the pair is found whichever track is indexed second;
  - pairs aren't chained (A–B, B–C without A–C).

## 5. AppView: serving

- [x] 5.1 Hydrate `activityView.group` in `Views.hydrate` with one batched query per page (D6), filtered as D5 and D6 say: viewer access to the partner tier, blocks either way, the viewer's mutes, and the current modes and follow rule. Cap the list and leave it absent when empty. Verify with Views tests:
  - a Followers-only partner is hidden from a non-follower;
  - a Map-only-me partner is hidden from everyone but its owner;
  - an owner who can't see the partner sees none;
  - a muted partner is hidden;
  - an unfollow under Connected hides the pair at once;
  - nothing served reveals a leave or the grouping choice.

## 6. Client: data layer

- [x] 6.1 Add `GroupPrefsRepository` (`core/data/.../segments/`), modelled on `SegmentPrefsRepository`: `GroupMode` states, `current`, `saved`, `load`, `refresh`, `shouldAsk`, `canUse` (the grant includes `actor.groupPrefs` and `feed.segmentTrack`), and a direct-write `save` that keeps the earlier value on failure. Verify with `core-data` jvmTests: offline save fails and keeps the earlier value, an old scope can't use it, and never asked versus declined.
- [x] 6.2 Introduce the shared "tracks wanted" predicate (matching ON or grouping Connected/Anyone) and use it in `SegmentTracks.put` and `SegmentTrackSweeper`. Backfill when it first becomes true, and delete all tracks only when it is false. Merge `GroupPrefsRepository.saved` into `repairRoutesOnSave`. Verify with tests: grouping on and matching off writes tracks; turning matching off with grouping on keeps them; turning both off deletes them; an interrupted backfill resumes.
- [x] 6.3 Add `groupExcluded` / `setGroupExcluded` to `SegmentTrackStore` and `groupExcluded` to `StoredSegmentTrack`. Make every rewrite (`put` from the sweeper, a re-trim, `setExcluded`) keep both marks, and start new and rebuilt combined tracks unmarked. Verify with tests that a re-trim keeps both marks, a leave queued offline is sent on reconnect, and a queued track reports null.

## 7. Client: Settings and the ask

- [x] 7.1 Add `GroupActivitiesSetting` beside `SegmentMatchingSetting` in `feature/settings` (presenter state with three choices, a saving flag through `rememberSingleFlight`, `UiError` on failure, sign-in-again when `!canUse`), and its `SettingsUi` row and strings in `:ui-resources`. Update `:app-fixtures` samples. Verify with presenter and UI tests and refreshed screenshot references (`:androidApp:updateDebugScreenshotTestDefaultTestSuite`).
- [x] 7.2 Add the one-time grouping ask after the segment ask, from `GroupPrefsRepository.shouldAsk`. Its text explains the trimmed-route sharing and who sees partners, and declining stores Off. Verify with presenter tests that it is asked once, never together with the segment ask, and that declining stores Off. Also update the UI test and samples.

## 8. Client: activity page and feed card

- [x] 8.1 Add the "Together with" section to `feature/detail`: contract fields in `-api`, presenter state from `activityView.group`, and a UI row of avatar and name opening `ActivityDetailScreen(partner.uri)`. Omit the section when the list is empty. Verify with presenter and UI tests, `:app-fixtures` samples, `ContentLoadingConformanceTest` if the state changes, and screenshot references.
- [x] 8.2 Add the owner-only Leave group / Rejoin group to the page. It is shown only when the track is stored and grouping isn't Off, goes through `rememberSingleFlight`, and calls `setGroupExcluded`. Show the local choice until the AppView catches up. Verify with presenter tests (a double tap writes once, another viewer sees nothing, a queued track hides the action) and a UI test.
- [x] 8.3 Add the "with Bob" / "with Bob and Carol" / "with Bob and N others" line to `ActivityCard` (`:app-common-ui`) from a `<plurals>` resource. Verify with UI tests for 0, 1, 2 and 3 partners and refreshed screenshot references.

## 9. Integration

- [x] 9.1 Run `./gradlew check` (tests on all targets, lint, ktfmt, detekt, `composeStabilityCheck`, `checkModuleBoundaries`), then `scripts/check-agent-sync.sh` if rules changed. Verify it passes.
- [x] 9.2 End to end on the local stack:
  - run `scripts/dev.sh --seed alice` and create bob (`docker/scripts/create-account.sh bob`);
  - opt both in to Anyone;
  - import overlapping GPX files (alice as Ride, bob as Ride/MTB).

  Verify that both pages and cards show each other. Then Leave on bob's ride and verify both clear. Switch alice to Connected with no follow and verify no pair; add a follow and verify it pairs again.
