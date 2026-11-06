# Design

## Context

See proposal.md for why. What shapes the approach:

- **The timeline is already an open union.** It holds `activityView` and `importCardView` at every layer: the lexicon, `TimelineItem` with its `OpenUnionSerializer`, the Room cache keyed by prefix, and `FeedUi.timelineItems`. Older clients skip unknown kinds.
- **The AppView builds a page from one table.** `Views.cardedTimeline` keyset-pages `activities` on `(sort_at, uri) DESC` and merges import cards into the same sort in memory. Its cursor is `<position>::<key>`.
- **Tier visibility is shared code.** `visibleTo`, `admits`, `served` and `unhidden` take visibility, author, record and CID columns, so a second table can reuse them.
- **Activities move between tiers already.** A rewrite at the same rkey in another tier is handled by `place()` and `RecordShadows`, which keep the narrowest copy.
- **Proposals are indexed only from repos, and only once accepted.** `SegmentNameIndex.upsertNameProposal` handles this. Closed proposals keep their row with a status, and the segment-name views already serve totals and the viewer's vote.
- **The client writes proposals through `SegmentNames`.** It shows each change at once. `propose` creates a TID rkey, and `edit` and `delete` go through the outbox. Tier-space writes go through `AthleteSpaces`.

## Goals / Non-Goals

**Goals:**
- A post kind that the later changes (likes and comments, reposts, quotes) extend without reshaping.
- No new privacy surface. The text is the only thing a narrower audience protects, since the proposal is public already.
- Voting from the card reuses the existing vote record and rules.

**Non-Goals:**
- Showing posts in the Mine scope. That scope reads the author-activities query, and the Posts tab on the profile covers own posts.
- Paging the timeline on the client. The client still fetches only the first page.

## Decisions

### 1. A post is its own record, not a field on the proposal
Proposals are public repo records. Putting a Followers audience and a reason on them would publish the reason to everyone. A separate `feed.post` goes in the repo or a tier space by its audience, like an activity. *Alternative:* `share` and `text` fields on `segmentNameProposal`. Rejected: they would leak the text, and later posts (quotes) would need the record anyway.

### 2. A post's rkey is its proposal's rkey
The client writes the post at the same rkey as the proposal it embeds. The AppView accepts a proposal post only when:
- the embed's URI names a `feed.segmentNameProposal` in the post author's repo;
- that proposal has the post's rkey.

This makes "one post per proposal" structural. Changing the audience becomes a rewrite at the same rkey in another tier, which `place()` already handles, keeping the narrowest copy and shadowing the rest. *Alternative:* any rkey, keeping the earliest post per proposal. Rejected: it needs a tie-break and has no clean way to move a post between tiers.

### 3. The proposal is joined when a post is read
The `posts` table stores the author, the proposal URI, the text, `created_at`, `sort_at`, the visibility, the origin, the record and the CID. Serving a post joins it to `segment_name_proposals` by URI. A post whose proposal isn't indexed is simply not served, whether it arrived first, the proposal was ignored, or the proposal was deleted. A later proposal makes the post appear with no reindex. Deleting the proposal deletes nothing from `posts`: the client deletes the post too, and the join hides it until then. The embed names the proposal by AT-URI, not strong ref: the client queues the post with the proposal, before the PDS has given the proposal a CID, and an edit changes the CID anyway while the card follows the latest version.

### 4. Post position and timeline merge
`sort_at` is the record's `createdAt`, capped at index time (the same capping `FeedPosition` applies to `postedAt`). `cardedTimeline` runs a second query on `posts` with the same author set (the viewer plus `effectivelyFollowedBy`), `notMuted`, visibility, block and label filters, and keyset bound, limited to one page. It merges the result into the existing in-memory sort. A post's key is its `at://` URI, which never equals an activity URI (different collection) or a card key (`batch:`), so the order stays total. A filtered request (type, since or until) skips the post query.

### 5. The post view
`#postView` holds:
- `uri`, `cid` and `author`;
- `text`, `visibility`, `createdAt` and `labels`;
- `embed`, an open union whose first member is `#postProposalEmbedView`.

The embed view holds:
- the segment or site as `segmentViewBasic` or the dive-site equivalent, with its current name;
- the proposal's URI, CID, name and `openedAt`;
- a `status` (`open`, `adopted`, `rejected`, `expired` or `superseded`);
- the totals (final ones once closed);
- `viewerVote`;
- `canVote`: the viewer has an effort on the segment or a visit to the site, and the proposal is open. This is computed in one batch per page.

Proposals hidden by labels follow `segments/segment-names`: a post whose proposal no longer may be shown is not served.

### 6. Client writes
`SegmentNames.propose` and `edit` take an optional `PostShare(visibility, text)`.
- With a share, they enqueue the post `put` after the proposal `put`, in the same outbox group and at the same rkey. The destination comes from `AthleteSpaces` for Followers or Mutuals, or the repo for Public.
- Changing the audience enqueues a delete from the old place before the put. The AppView would keep the narrowest copy anyway; the delete removes the stale wider copy.
- Turning sharing off enqueues the post delete.
- `delete` enqueues the post delete before the proposal delete.

The optimistic overlay (`changes` / `applyTo`) gains the post, so the feed and profile show it at once. A PDS without Spaces offers Public only, with the same explanation voting uses.

### 7. Feed cache and UI
`FeedRepository` handles the new kind in its three per-kind places, under a `post:` key prefix. `PostCard` lives in `:app-common-ui` so the feed and the profile share it, and the vote buttons send the existing vote event through `SegmentNames.vote`. The profile gains an Activities and Posts switch (`ui/adaptive` segmented buttons), with posts loaded on their own in the content-loading pattern.

## Risks / Trade-offs

- **[Risk] Timeline query cost.** A second keyset query on every unfiltered page. → Mitigation: an index on `posts (author_did, sort_at DESC, uri)`, limited to one page like the activity query.
- **[Risk] A follower sees the post before the proposal is accepted, or never.** → Mitigation: the join serves nothing until the proposal is accepted. The sheet offers sharing only when `canProposeName` is true, so normally both land together.
- **[Trade-off]** The Mine scope doesn't show the athlete's own posts. They see them in Following and on their profile.
- **[Risk] The card shows status changes, such as adoption, only on refresh.** Acceptable, since activities' like counts behave the same way.

## Migration Plan

- V57 adds the `posts` table and its index. No backfill is needed, since no posts exist yet.
- `space/athlete.json` gains the collection. Spaces declared before the change accept the new collection once the declaration is republished, as previous additions did.
- Rollback: older AppViews ignore the collection, and older clients skip `#postView` as an unknown union member.
