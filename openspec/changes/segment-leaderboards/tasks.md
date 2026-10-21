# Tasks

## 1. Lexicons and Kotlin mirror

- [ ] 1.1 Add the lexicon changes per design decision 1. Verify the JSON parses with the other lexicons (`./gradlew :core-lexicon:jvmTest`).
  - `feed/getSegmentLeaderboard.json`;
  - `#leaderboardEntry`, plus `rank` and `notRanked` on `#effortView`, in `feed/defs.json`;
  - `hideFromLeaderboards` on `actor/segmentPrefs.json`;
  - `excludeFromLeaderboards` on `feed/segmentTrack.json`, with descriptions saying both are private and stay in `self`.
- [ ] 1.2 Mirror them in `core/lexicon`. Verify with `SegmentRecordsTest` cases: the new fields round-trip, are dropped when null, and old JSON without them decodes.
  - `Nsid.FEED_GET_SEGMENT_LEADERBOARD`;
  - `GetSegmentLeaderboard` in `Queries.kt`;
  - `LeaderboardEntry` and the `EffortView` fields;
  - the `SegmentPrefsRecord` and `SegmentTrackRecord` fields;
  - `LeaderboardPeriod`, `LeaderboardScope` and `NotRanked` constants.
- [ ] 1.3 Add `SegmentSportGroup.maxSpeedMmPerS`. Verify a unit test pins each group's cap (ride 27 778, run 11 111, hike 8 333, ski 33 333, paddle 6 944 mm/s).
- [ ] 1.4 Run the `lexicon-auditor` agent on the changed NSIDs and fix every mismatch it reports.

## 2. AppView: flags and indexing

- [ ] 2.1 Add `V31__segment_leaderboards.sql` per design decision 4, with H2-portable SQL, plus the Exposed columns on `SegmentPrefs` and `SegmentTracks`. Verify `MigrationVersionsTest` and `./gradlew :server-appview:test`.
- [ ] 2.2 Index the two flags in `index/SegmentIndex.kt`. A segment-track upsert with an unchanged `data_cid` and zones revision updates the row without queuing `track`. Verify `SegmentTracksTest` cases: both flags are stored; a flag-only change queues no job; a changed blob still queues one; deleting the prefs record resets the opt-out.

## 3. AppView: leaderboards

- [ ] 3.1 Add `rankable()` and the per-row `notRanked` reason in `api/SegmentViews.kt` per design decision 5, reusing `effortsVisibleTo`. Verify through 3.2's and 3.3's tests.
- [ ] 3.2 Add `SegmentViews.leaderboard` and the `getSegmentLeaderboard` route per design decision 6. Verify `SegmentLeaderboardsTest` (H2, `SegmentTestBase`). It covers:
  - one entry per athlete, fastest first;
  - competition ranks on ties, with the earlier effort first;
  - paging, with ranks continuing across pages;
  - the viewer entry off-page;
  - all-time, year and month by `started_on` with a fixed clock;
  - the `following` scope (effective follows only, with an approve-policy follow pending) and its refusal when signed out;
  - the e-bike board split;
  - tiers: the Followers map, Only me seen only by the author, and an activity narrowed after matching;
  - blocks both ways, and a taken-down account;
  - the opt-out, which hides the athlete even from themselves and leaves their efforts on their activity;
  - the exclusion, falling back to the next best effort;
  - the cap at and above the limit;
  - proposed, archived and duplicate segments returning empty;
  - an unknown segment as `NotFound`.
- [ ] 3.3 Add `rank` and `notRanked` to `getActivitySegments` per design decision 7. Verify `SegmentEffortsTest` cases:
  - rank 3 is served;
  - rank 11 and a non-best effort carry no rank;
  - ranks are per viewer (a follower sees a different rank than a stranger);
  - `notRanked` goes only to the author, with hidden taking precedence over excluded and excluded over implausible;
  - there's none on a proposed segment.
- [ ] 3.4 Update `.agents/rules/appview.md` (Segments and efforts, and the list of private data held) and AGENTS.md's architecture paragraph:
  - the leaderboard query;
  - `rankable`;
  - the two private flags indexed from `self`;
  - the read-time caps.

  Verify `scripts/check-agent-sync.sh` passes.

## 4. Client data

- [ ] 4.1 Add `SegmentRepository.leaderboard(...)`. Verify a `MockEngine` test of its params and decoding.
- [ ] 4.2 Have `SegmentPrefsRepository` keep both fields: `save(matching)` keeps `hideFromLeaderboards`, and a new `saveLeaderboards(hidden)` keeps `matching`. Both stay direct writes that report failure. Verify jvm tests: each save keeps the other field, and a failed save leaves the earlier value.
- [ ] 4.3 Make the segment-track writes keep the exclusion. Verify `SegmentTrackTest` cases:
  - a re-trim by new zones keeps `excludeFromLeaderboards`;
  - a publish and a combined rebuild write it false;
  - `setExcluded` re-puts the record with the same blob ref and no upload;
  - `setExcluded` refuses while a put for the rkey is queued.

  The changes:
  - `SegmentTrackStore.list()` returns the revision and the flag;
  - `put` takes `excluded`;
  - `SegmentTrackSweeper.write` passes the stored flag;
  - add `setExcluded(activity, excluded)` and `excluded(activity): Boolean?` per design decision 2.

## 5. UI

- [ ] 5.1 Create `SegmentLeaderboardScreen` with the `new-screen` skill per design decision 8: filters, paging, the pinned viewer entry, opening an activity, and error with retry. Verify a presenter test covering the first load, a filter change reloading from the first page, load-more, retry and opening an activity. Check it visually on desktop with the hot-reload MCP against the local stack (`docker/scripts/seed-segment.sh` plus several accounts).
- [ ] 5.2 Add `OpenLeaderboard` to `SegmentScreen`/`SegmentPresenter`/`SegmentUi`, offered only for active segments. Verify `SegmentPresenterTest`: shown for active, hidden for proposed, and navigating to the leaderboard.
- [ ] 5.3 Show the rank chip and the owner's not-ranked reason in `EffortRow`, with ordinal strings. Verify `ActivityDetailSegmentsTest`, which passes `rank` and `notRanked` through, and a desktop screenshot of an activity with a ranked effort.
- [ ] 5.4 Add the leaderboard toggle to `SegmentMatchingSetting`, `SettingsScreen` and `SettingsUi`, shown only while matching is on, and add the leaderboard sentence to the consent prompt and Settings copy. Verify `SettingsPresenter` tests: hidden while matching is off, saving keeps matching, and a failure reverts.
- [ ] 5.5 Add the exclusion toggle to `EditActivityScreen`, `EditActivityPresenter` and `EditActivityUi`. It's offered only on an own activity with a readable segment track, and saved through `setExcluded` only when it changed. Verify `EditActivityPresenter` tests: not offered without a track, loaded state shown, and only a changed value is written.

## 6. Integration

- [ ] 6.1 Run `./gradlew check` and `./gradlew composeStabilityCheck`, and fix what they report.
- [ ] 6.2 End to end on the local stack (`scripts/dev.sh`):
  - three accounts ride a seeded segment so it becomes active;
  - the board ranks them;
  - one hides from leaderboards and disappears;
  - one excludes a ride and falls back to their next effort.

  Record the commands used in the local-stack skill if they aren't already there.
