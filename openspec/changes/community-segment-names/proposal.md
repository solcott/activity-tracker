# Proposal

## Why

A segment keeps the name its creator gave it. For imported segments that's an OSM or agency name such as "Trail 214 Connector", and for athletes' segments it's whatever they typed. The people who ride a trail most often usually know its real local name, but they can't change it: the record is in someone else's repo, and the AppView never writes to repos. Letting regular riders propose names, and the segment's other riders vote on them, gets segments the names locals actually use. The original name stays visible.

## What Changes

- An athlete with efforts on at least 5 distinct days on an `active` segment can propose a new name for it. A proposal is a public `feed.segmentNameProposal` record in their repo and names its proposer. Eligibility counts efforts of every visibility, so the client warns before submitting that proposing shows others they're a regular on the segment.
- `feed.getSegment` gives a signed-in viewer a viewer-only flag saying whether they may propose, and the client shows Propose only when it's set. The AppView checks eligibility again when it indexes a proposal and ignores one from an athlete who isn't eligible.
- The proposer can delete their open proposal, which withdraws it along with its votes. They can also edit its name, which clears all of its up and down votes and restarts its time open.
- Athletes with at least one effort on the segment vote up or down with a `feed.segmentNameVote` record in their `interactions` space. Votes are private: the AppView serves only each proposal's up and down totals, never who voted.
- The AppView adopts a proposal once it has been open at least 7 days, its upvotes reach max(5, 10% of the distinct athletes with efforts on the segment in the last 365 days), and at least two-thirds of its votes are up. A proposal whose net score falls to −5 is rejected, and one still open after 90 days expires. An athlete has at most one open proposal per segment. After an adoption, no new proposals are accepted for 90 days. Going back to an earlier name, the original included, is an ordinary proposal.
- The AppView works out each segment's **current name**: the last adopted name, or the record's name when none has been adopted. The record's `name` stays as the **original**. A creator's edit or the importer's `refine` changes only the original, and an adopted name stays on top.
- Every segment view (search, efforts, leaderboards, Mayors, stars) shows the current name. The segment screen also shows "originally …" and a name history of adopted names with dates and final tallies. It lists open proposals with their totals and the viewer's own vote.
- A `!hide` or `!takedown` label on a proposal takes it out of voting. On an adopted proposal, it removes that name, and the segment falls back to the name before it.
- Out of scope: telling a segment's riders when a proposal opens. That needs notifications, which don't exist yet (see BACKLOG.md).

## Capabilities

### New Capabilities
- `segments/segment-names`: community naming of segments. Covers who may propose and vote, the proposal and vote records, how proposals are adopted, rejected or expire, the current and original names and the name history, labels on proposals, and how clients show and submit proposals and votes.

### Modified Capabilities
- `segments/segment-records`: `feed.getSegment` serves the current name, the original name, the name history, open proposals and the viewer's eligibility and votes. Deleting a segment also removes its proposals, votes and name history.

## Impact

- **Lexicons:** new `feed.segmentNameProposal` and `feed.segmentNameVote` records. `feed.defs#segmentView` gains `name` (current), name history and open proposals, and `#segmentViewerState` gains `canProposeName` and the viewer's votes. `#segmentViewBasic.name` becomes the current name. `Nsid.indexedCollections` and the interactions-space collections gain the new records.
- **AppView:** a Flyway migration for proposals, votes and adopted names plus a current-name column on `segments`. Indexing for both records, including the eligibility check. An adoption, rejection and expiry pass alongside the matcher's hourly status pass. Segment views read the current name. Label handling for proposals.
- **Client:** `SegmentRepository` writes proposals (repo) and votes (`interactions` space) through the outbox. The segment screen gets the original name, name history, open proposals with voting, and a Propose sheet with the disclosure warning. Fixtures, screenshot tests and presenter tests change to match.
- **Importer:** no code change. `refine` keeps rewriting the record's name, which now updates only the original.
