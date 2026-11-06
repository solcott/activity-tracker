# Proposal

## Why

A segment name proposal only reaches riders who happen to open the segment, so most proposals never get the votes they need to be adopted. Riders who follow the proposer are the people most likely to care. A post in their timeline brings the proposal to them, along with the proposer's reason and a way to vote on the spot. This is the first of four post changes. It brings in posts as a timeline item, though athletes can't write one on their own yet. Later changes add likes and comments (`post-interactions`), reposts (`post-reposts`) and quote posts (`post-quotes`).

## What Changes

- **New record `app.activitytracker.feed.post`** with optional body text and an open-union `embed`. The only embed for now is a proposal: the AT-URI of the author's own `feed.segmentNameProposal`, for a segment or a dive site.
  - **Where it's stored:** a Public post goes in the author's repo. A Followers or Mutuals post goes in that tier's `space.athlete` space. Posts have no Only me tier, and the AppView ignores a post in `self`.
- **Shared from the propose sheet.** Proposing or editing a name offers **Share to feed**, which is off by default. The athlete picks Public, Followers or Mutuals and can add optional text about why.
  - The proposal record itself stays public and unchanged.
  - Turning sharing off deletes the post, and so does deleting the proposal.
  - The app offers no other way to write a post.
- **The AppView serves a post only while its embed points at a proposal it accepted, from the same author.** It serves at most one post per proposal, and the post is gone once the proposal is.
- **Posts in the timeline.** `feed.getTimeline`'s open union gains `#postView`, placed at the post's creation time (capped at index time). Visibility, blocks, mutes and labels apply to posts as they do to activities. Type and date filters leave posts out.
- **The card follows the proposal.** A post's view embeds the proposal's current state: its name, status (open, adopted, rejected, expired or superseded), totals and the viewer's vote, plus the segment or site's current name.
  - A rider who may vote sees up and down buttons on the card. These write the same `feed.segmentNameVote` as the segment screen.
  - Tapping the card opens the segment or site.
- **Posts on the profile.** The profile gains an Activities and Posts switch. A new `feed.getAuthorPosts` query lists an athlete's posts that the viewer may see.
- **Reporting.** Others' posts can be reported, and labels on a post or its author apply to it.

Out of scope: likes, comments, reposts, quote posts, a post page, notifications, free-standing posts, and posts that embed anything but a proposal.

## Capabilities

### New Capabilities
- `feed/posts`: the post record, where each tier stores it, the AppView's acceptance rules for proposal posts, the post view and its live proposal embed, posts in the timeline and on the profile, and voting from a post card.

### Modified Capabilities
- `segments/segment-names`: the propose and edit sheet can share the proposal to the feed with an audience and optional text, and deleting a proposal deletes its post.
- `dive-sites/site-names`: dive-site proposals can be shared the same way.
- `feed/timeline-order`: a post's feed position.
- `feed/feed-filter`: both scopes show posts, and type and date filters leave them out.
- `graph/blocks`: blocked accounts' posts are hidden.
- `graph/mutes`: muted accounts' posts leave the muter's timeline.
- `moderation/labels`: account and record labels cover posts.
- `moderation/reports`: Report is offered on others' posts, and names the post as its subject.
- `spaces/appview-syncer`: the AppView indexes `feed.post` from `followers` and `mutuals` spaces.

## Impact

- **Lexicons**:
  - new `feed/post.json` and `feed/getAuthorPosts.json`;
  - `feed/defs.json` gains `#postView`, `#postProposalEmbedView` and a proposal `status`;
  - `feed/getTimeline.json` adds `#postView` to its union;
  - `space/athlete.json` lists `feed.post` and describes it.
- **core/lexicon**: `PostRecord`, `Nsid.FEED_POST` (in `indexedCollections`), `TimelineItem.Post`, `GetAuthorPosts`, and `AppCollections` gains the new collection.
- **AppView**:
  - a `posts` table (Flyway V57);
  - indexer upsert and delete branches, tier placement and `HELD_ROWS`;
  - the timeline merges a second keyset query over posts;
  - post hydration reuses the segment-name views;
  - a `getAuthorPosts` route.
- **Client**:
  - `SegmentNames` (`core/data`) writes, rewrites and deletes the post through the outbox;
  - `FeedRepository` caches the post kind;
  - the `ProposePlaceName` sheet gains sharing;
  - a new `PostCard` in `:app-common-ui`;
  - the feed and profile render posts;
  - new `:app-fixtures` samples, strings, and screenshot references.
- **Export and leaving** need no new rules. They already walk `AppCollections` and every athlete space, so posts are included once the collection is listed.
