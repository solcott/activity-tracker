# Design

## Context

A segment's name lives only in `feed.segment#name`, in its creator's repo (often the importer's). The AppView indexes it into `segments.name` and serves it as `segmentView.record.name` and `segmentViewBasic.name`. The AppView never writes repos, so a community name can only be derived and served by the AppView, the same way it derives segment status (`segments.status`, promoted and archived by `SegmentMatcher`'s hourly status pass). See proposal.md for why; the behaviour is in `specs/segments/segment-names` and the `segment-records` delta.

Parts this builds on:
- **Efforts** are in `segment_efforts`, one row per pass, with `author_did`, `visibility` and `started_on` (the activity's local date). Efforts of every visibility are stored, which is what eligibility counts.
- **The interactions space** already carries likes, comments, hides, blocks and mutes (`Indexer.INTERACTION_COLLECTIONS`). The client writes to it through the outbox, and the AppView syncs it as a member.
- **Labels** are stored by `LabelStore` and applied at query time by `labelled(subject, value)`. They can also expire.
- **Blocks and mutes** are applied in views with `blocked()` and the mute helpers.

## Goals / Non-Goals

**Goals:**
- Serving the current name stays as cheap as today's name, with no extra join on every segment row in search, efforts or leaderboards.
- Every threshold is configurable per deployment.
- Votes never reach a public place, and no response serves who voted.

**Non-Goals:**
- Per-segment thresholds set by hand. "More for busy segments" comes from the threshold scaling with riders.
- Name search. There's still no search by segment name.
- Keeping votes across an edit. Editing a proposal's name always clears its votes, so nobody's vote ends up attached to a name they didn't choose.

## Decisions

### Store the current name on `segments`, recompute it on events

Add `segments.current_name`, backfilled from `name`. Every view reads it in place of `name`, and `name` stays the original. The AppView recomputes it for one segment when:
- the naming pass adopts a proposal there;
- an adopted proposal's record is labelled, unlabelled or its proposer's account changes status (a `LabelStore` hook for subjects that are proposal URIs or proposer DIDs);
- the hourly pass finds a label expired.

*Alternative:* compute it at read time as "latest adopted proposal not labelled". That's exact, but it adds a correlated subquery to every segment row in search, efforts, leaderboards and Mayors. Labels on proposals are rare, so recomputing on events costs less.

### Tables

- `segment_name_proposals`: `uri` (PK), `cid`, `segment_uri`, `proposer_did`, `name`, `state` (`open`, `adopted`, `superseded`, `rejected`, `expired`), `opened_at` (when indexed), `closed_at`, `final_up`, `final_down` and `record_gone`. Deleting an open proposal deletes its row (withdrawn). Deleting an adopted one keeps the row with `record_gone = true`, so history keeps the name but drops the proposer. Superseded, rejected and expired rows are kept for the cooldown and duplicate checks, and could be pruned later.
- `segment_name_votes`: `uri` (PK), `voter_did`, `proposal_uri`, `proposal_cid`, `up`, `created_at`. The tally counts, per voter and proposal, the row with the latest `created_at`.
- Deleting a segment deletes its proposals and their votes in the same transaction as its efforts.

### A vote pins the proposal's CID

`feed.segmentNameVote.subject` is a `com.atproto.repo.strongRef`. A vote counts only while its CID matches the proposal's stored CID. That makes clearing votes on an edit free:
- **Accepted edit.** The indexer stores the new CID and name and resets `opened_at`. Old votes stop matching and are never counted again, even if the proposer edits back to the old name, because that's a new CID too.
- **Failed edit.** The indexer keeps the stored CID and name, so the old votes keep matching.
- **Closed proposal.** Only open proposals can be edited. The client never offers an edit for a closed one, but the AppView can't stop a PDS from rewriting the record, so the indexer ignores a rewrite of any proposal that isn't `open` and keeps its stored version.

Votes cast against the old CID can be pruned whenever is convenient; they never count.

`feed.segmentNameProposal.segment` is a plain `at-uri`, not a strongRef. A proposal names the segment, not one version of its line.

### Eligibility is checked once, at indexing

When a proposal is indexed, a single query over `segment_efforts` checks it: count distinct `started_on` for the proposer on the segment, filtered by the speed-cap predicate the leaderboards already use. Other checks in the same transaction: the segment is `active`, it's outside the cooldown (no adoption in the last N days), the proposer has no other open proposal there, and the name isn't the current name or another open proposal's. A proposal that fails is not stored, so it's ignored for good, as the spec says. A rewrite of a stored proposal is an edit. It runs only the name checks, with the proposal's own current name excluded from "another open proposal's name", and skips eligibility, the cooldown and the one-open-per-athlete check, because the proposal already holds its place.

The `canProposeName` flag in `getSegment` runs the same checks for the viewer, with the same query, so the flag and the indexer can't disagree.

*Alternative:* store failed proposals as `ignored` and check them again later. That has no benefit for users, because the client shows Propose only when the flag allows it.

### Tally and voter eligibility at check time

Votes are stored whoever casts them. When the tally runs, a vote counts only if its voter has a row in `segment_efforts` for the segment, isn't the proposer, and its CID matches. Checking at tally time handles votes indexed before the voter's efforts are matched, and votes from riders whose efforts later go away. `getSegment` serves totals computed by the same query. Proposals are few per segment, so this stays cheap.

### The naming pass runs with the status pass

`SegmentMatcher` already runs `updateStatuses()` hourly. A new `updateNames()` runs right after it, for every `active` segment with open proposals. It computes `riders365` (distinct `author_did` with `started_on` ≥ today − 364, in UTC), adopts at most one qualifying proposal (best score, then earliest), supersedes the rest, closes rejected and expired ones, and recomputes `current_name`. It writes `final_up` and `final_down` when it closes a proposal. Adoption takes effect up to an hour after a proposal qualifies, which is fine with a 7-day minimum.

The thresholds join `AppViewConfig` alongside `SEGMENT_ACTIVE_ATHLETES`:

| Setting | Default |
|---|---|
| `SEGMENT_NAME_PROPOSER_DAYS` | 5 |
| `SEGMENT_NAME_MIN_UP` | 5 |
| `SEGMENT_NAME_RIDER_PERCENT` | 10 |
| `SEGMENT_NAME_MIN_OPEN_DAYS` | 7 |
| `SEGMENT_NAME_REJECT_SCORE` | -5 |
| `SEGMENT_NAME_EXPIRY_DAYS` | 90 |
| `SEGMENT_NAME_COOLDOWN_DAYS` | 90 |

The two-thirds ratio stays fixed. Integer maths throughout (`3 * up >= 2 * (up + down)`) keeps it H2-portable.

### Lexicon shape

- `feed.segmentNameProposal`: key `tid`; `segment` (at-uri), `name` (maxGraphemes 100), `createdAt`. Repo only.
- `feed.segmentNameVote`: key `tid`; `subject` (strongRef to the proposal), `direction` (knownValues `up`, `down`; any other value is ignored), `createdAt`. Interactions space only.
- `feed.defs#segmentView` gains `name` (current; clients fall back to `record.name` when it's absent, so older AppViews still work), `nameHistory` (`#segmentNameAdoption`: `name`, `adoptedAt`, `up`, `down`, optional `proposer`) and `nameProposals` (`#segmentNameProposalView`: `uri`, `cid`, `name`, `proposer`, `openedAt`, `up`, `down`, optional `viewerVote`). `record.name` is the original.
- `#segmentViewerState` gains `canProposeName` and `canVoteOnNames`.
- `#segmentViewBasic.name` keeps its shape but now carries the current name.

### Client

- `SegmentRepository` gains `proposeName`, `editProposal` and `deleteProposal` (outbox `putRecord`/`deleteRecord` in the repo, with the edit keeping the proposal's rkey) and `vote`/`clearVote` (outbox writes into the `interactions` space, the way likes on non-public activities are written). A vote is one record per proposal: changing it rewrites the same rkey, which the client remembers from `viewerVote`, and taking it back deletes it.
- `SegmentScreen.State` gains the original name, history, proposals with optimistic vote state, and the two flags. Propose is a SubCircuit sheet (`ProposeSegmentName`: Screen, State, Event, presenter, UI), with the disclosure notice and `TextFieldState` in its state. The same sheet edits an open proposal: opened with the proposal, it starts from its name and shows the warning that votes will be cleared in place of the disclosure. A proposal is the viewer's own when its proposer is the signed-in DID, so the view needs no extra flag. History is a collapsible section on the segment screen rather than its own screen.
- Voting is disabled with an explanation when `AthleteSpaces` reports the PDS can't hold spaces, matching how other interactions-space writes degrade.

## Risks / Trade-offs

- [Proposals reveal that the proposer rides the segment regularly, private efforts included] → The user chose this. The client states it before submitting. Efforts inside privacy zones never exist, because segment tracks have zones cut out.
- [Fake efforts from hand-made GPX files to gain eligibility or votes] → Requiring distinct days, the speed cap, and a threshold that grows with real riders raise the cost. Moderators can label proposals. Accounts can be labelled.
- [Riders brigading a joke name] → The two-thirds ratio, the 7-day minimum, the reject score and the label fallback. Reverting is an ordinary proposal.
- [Vote secrecy depends on the AppView operator] → Votes sit in a space only the voter and the AppView read. Treat them like the other private data in the AppView database (see BACKLOG "Before deploying anywhere shared").
- [`current_name` goes stale if a recompute trigger is missed] → The hourly pass recomputes it for every segment with adopted proposals, as a backstop.
- [Riders on PDSes without Spaces can't vote] → They can still propose. The vote control explains why voting is off.

## Migration Plan

- One Flyway migration (`V44__segment_names.sql`): create the two tables with indexes on `(segment_uri, state)`, `(proposal_uri)` and `(voter_did, proposal_uri)`, add `segments.current_name`, and backfill it from `name`. It's H2-portable, with no vendor-specific part.
- Add the new collections to `Nsid.indexedCollections` and to the interactions-space set. Proposals are new, so there's nothing to backfill.
- Rollback: older AppView builds ignore the new tables and column, and clients fall back to `record.name` when `name` is absent.
