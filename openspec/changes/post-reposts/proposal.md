# Proposal

## Why

A shared name proposal reaches only its proposer's audience. Riders who agree with it can't pass it on to their own followers, who may ride the same trail. A repost lets them pass it on. This is the third of four post changes, after `feed-posts` and `post-interactions`.

## What Changes

- **New record `app.activitytracker.feed.repost`,** naming a `feed.post` by strongRef. Only posts can be reposted. Activities can't be reposted in this change.
  - A repost of a Public post goes in the reposter's repo.
  - A repost of a Followers or Mutuals post goes in the reposter's `interactions` space, so the repo never reveals that they can see a non-public post.
  - The AppView ignores a repost of a post the reposter can't see when it's indexed.
- **Who sees a repost.** A viewer sees it only if they may see both the post and the reposter's repost. A repost never widens a post's audience.
- **Reposts in the timeline.** The timeline shows a repost as the original post with "Reposted by …", at the repost's position.
  - When several athletes the viewer follows repost the same post, it appears once, at the latest repost, naming one reposter and how many others.
  - When the viewer can already see the original in their timeline, they see one item, not two.
  - Undoing a repost deletes the record.
- `postView` gains `repostCount` and the viewer's repost. The card gains Repost and Undo repost.
- Blocks, mutes and labels apply to the reposter and the author separately. A block between the viewer and either one hides the repost.

Out of scope: reposting activities, quote posts (`post-quotes`), and notifications.

## Capabilities

### New Capabilities
- `feed/reposts`: the repost record, where it's stored, who sees it, and how the timeline shows and dedupes it.

### Modified Capabilities
- `feed/posts`: repost counts and the viewer's repost, and the card's Repost action.
- `feed/timeline-order`: a repost's position and deduping.
- `spaces/appview-syncer`: reposts are indexed from `interactions` spaces.
- `graph/blocks`, `graph/mutes`: reposts by blocked or muted athletes are hidden.

## Impact

- **Lexicons**: a new `feed/repost.json`, and `getTimeline` items carry a `reason` (`#reasonRepost`).
- **AppView**: a `reposts` table and migration, indexing from repos and `interactions`, and a third source merged into `cardedTimeline` with dedupe by post URI.
- **Client**: repost writes through the outbox, the card's action and "Reposted by" header, the feed cache, fixtures, and screenshots.
