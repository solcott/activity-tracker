# Tasks

## 1. Lexicons

- [x] 1.1 Add `lexicons/app/activitytracker/feed/segmentNameProposal.json` and `segmentNameVote.json`, and extend `feed/defs.json` (`#segmentView.name`, `nameHistory`, `nameProposals`, `#segmentNameAdoption`, `#segmentNameProposalView`, `#segmentViewerState.canProposeName`/`canVoteOnNames`, the description of `#segmentViewBasic.name`) per design.md "Lexicon shape". Mirror them in `core/lexicon` and add both NSIDs to `Nsid` and `Nsid.indexedCollections`. Verify the `core/lexicon` round-trip tests pass for both records and the extended views (`./gradlew :core-lexicon:jvmTest`), including a `segmentView` with no `name`.

## 2. AppView storage and indexing

- [x] 2.1 Add `V44__segment_names.sql` (both tables, their indexes, `segments.current_name` backfilled from `name`) and the matching `Tables.kt` objects. Verify the migration runs on H2 in the AppView tests and that existing segment tests still pass.
- [x] 2.2 Set `current_name` when a segment is first indexed, and leave it alone when a re-write changes only `name` and the segment has an adopted proposal in effect. Delete a segment's proposals and votes together with its efforts. Verify with `SegmentRecordIndex` tests covering "Importer refines a renamed segment", "Renamed after a community name" and "Segment deleted".
- [x] 2.3 Add the naming thresholds to `AppViewConfig` (design.md table) with validation like `SEGMENT_ACTIVE_ATHLETES`. Verify with `AppViewConfig` tests for defaults and an invalid value.
- [x] 2.4 Index `feed.segmentNameProposal` from repos only, with the eligibility, cooldown, one-open-per-athlete and new-name checks in one transaction. Handle deleting an open proposal (withdrawn) and deleting an adopted one (`record_gone`). Verify with indexer tests for every scenario in "A name proposal is a public record", "Only regulars may propose", "A proposal must offer a new name", "One open proposal per athlete per segment" and "Proposals pause after an adoption".
- [x] 2.5 Index a rewrite of a stored proposal as an edit per design.md: name checks only, and on success store the new CID and name and reset `opened_at`. Keep the stored version when the edit fails, and ignore a rewrite of any proposal that isn't open. Verify with indexer tests for "Edited", "Edit to the current name" and "Rewrite after adoption", a rewrite of a rejected and of an expired proposal, plus an edit back to an earlier name, which still doesn't count the old votes.
- [x] 2.6 Index `feed.segmentNameVote` from the `interactions` space only (add it to `INTERACTION_COLLECTIONS`), ignoring unknown directions, and remove it on delete. Verify with indexer tests for "Vote written", "Changed vote" and "Vote in a repo".

## 3. AppView naming pass and labels

- [x] 3.1 Write the tally query (latest vote per voter, matching CID, voter has an effort, not the proposer) as a shared helper used by both the pass and the views. Verify with tests for "Voter without efforts", "Voter's efforts arrive later", a proposer's own vote and a vote on a rewritten proposal.
- [x] 3.2 Add `updateNames()` to `SegmentMatcher` after `updateStatuses()`: riders in the last 365 days, adopt at most one proposal (best score, then earliest), supersede the rest, reject, expire, write final totals, recompute `current_name`. Verify with a `SegmentMatcher` test using an injected clock for "Quiet trail", "Busy trail", "Too soon", "Two qualify at once", "Voted down", "Never decided" and a segment that isn't `active`.
- [x] 3.3 Recompute `current_name` from `LabelStore` when a label arrives for or is negated on a proposal URI or a proposer DID, and in the hourly pass for expired labels. Leave labelled proposals out of tallies and adoption. Verify with tests for "Offensive name adopted" and "Label negated", plus `!hide` on an open proposal.

## 4. AppView views

- [x] 4.1 Serve `current_name` as the name in every segment view: `getSegment`, `searchSegments`, efforts in `getActivitySegments`, `getSegmentEfforts`, leaderboards, Mayors and starred segments. Verify with `SegmentViews` route tests asserting the current name in each response and the original in `record.name`.
- [x] 4.2 Add `nameHistory`, `nameProposals` (totals from the shared tally, the viewer's own vote, filtered for blocks and mutes) and `viewer.canProposeName`/`canVoteOnNames` (sharing the indexer's eligibility query) to `getSegment`. Verify with route tests for "Two renames", "Proposer deletes an adopted proposal", "Viewer voted", "Blocked proposer", "Eligible viewer" and "Signed out", and that no response lists voters.
- [x] 4.3 Run the `lexicon-auditor` agent on both new records and the extended `segmentView`, and fix any mismatches it reports.

## 5. Client data

- [x] 5.1 Read the current name, original name, history, proposals and flags into the client's segment model, falling back to `record.name` when `name` is absent. Verify with `core/data` tests on a `MockEngine` response with and without the new fields.
- [x] 5.2 Add `proposeName`, `editProposal`, `deleteProposal`, `vote` and `clearVote` to `SegmentRepository` through the outbox: proposals to the repo (an edit keeping its rkey), votes to the `interactions` space under one rkey per proposal. Report voting as unavailable when the PDS can't hold spaces. Verify with repository tests for each write, an edit rewriting the proposal's rkey, a vote change rewriting the same rkey, a cleared vote deleting it, and retry after an outbox failure.

## 6. Client screens

- [x] 6.1 Extend `SegmentScreen` State/Event and `SegmentPresenter` with the original name, history, proposals, optimistic votes and edits, delete with confirmation, and the flags. Verify with `SegmentPresenterTest` cases for "Renamed segment", "Up vote" (optimistic, then confirmed), "PDS without Spaces", "Not eligible", "Deleting", "Someone else's proposal" and "Own adopted name".
- [x] 6.2 Add the `ProposeSegmentName` SubCircuit across `:app-screens`, `:app-presenters` and `composeApp` (disclosure notice, `TextFieldState` name, length validation, submit through the repository), which also edits an open proposal (starting from its name, with the votes-cleared warning, refusing an unchanged name) using the `new-screen` conventions. Verify with presenter tests for "Proposing", "Editing", blank, over-long and unchanged names, and UI tests for the notice and the warning.
- [x] 6.3 Update `SegmentUi`: "originally …" under the title, a collapsible name history, proposals with vote controls, edit and delete on the viewer's own open proposal, and Propose only when allowed, all built from `ui/adaptive` wrappers. Add the sample states to `app/fixtures`. Verify with `ContentLoadingConformanceTest`, `./gradlew composeStabilityCheck`, and updated screenshot references (`:androidApp:updateDebugScreenshotTestDefaultTestSuite`) that show each new state.

## 7. Integration

- [x] 7.1 On the local stack (`scripts/dev.sh`), with test accounts that have efforts on one segment on 5+ dates, propose a name, vote from enough riders with thresholds lowered through the AppView env, and wait for the pass. Verify the segment screen shows the new name, "originally …" and the history, and the activity's segment list shows the new name.
- [x] 7.2 Run `./gradlew check` and verify it passes.
