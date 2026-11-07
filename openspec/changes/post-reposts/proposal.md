# Proposal

## Why

A shared name proposal reaches only its proposer's audience. Riders who agree with it can't pass it on to their own followers, who may ride the same trail. A repost lets them pass it on. This is the third of four post changes, after `feed-posts` and `post-interactions`.

## What Changes

- **New record `app.activitytracker.feed.repost`,** naming a `feed.post` by strongRef. Only posts can be reposted. Activities can't be reposted in this change.
  - A repost of a Public post goes in the reposter's repo.
  - A repost of a Followers or Mutuals post goes in the reposter's `interactions` space, so the repo never reveals that they can see a non-public post.
  - A repost stays where it was written when the post's audience changes later, as likes on posts do.
  - The AppView ignores a repost of a post the reposter can't see when it's indexed.
- **Repost is offered on every post the viewer can see except their own,** on the card in the timeline and on the post page. Undoing a repost deletes the record.
- **Who sees a repost.** A repost is shown and counted for a viewer only while the viewer may see the post and the reposter may see it at its current tier. A repost never widens a post's audience: a viewer outside the post's audience never sees it, however many people they follow reposted it.
- **Reposts go with their post.** Once a post isn't served to anyone (deleted, its proposal withdrawn, or taken down), its reposts aren't shown or counted anywhere, as its likes and comments aren't.
- **Reposts in the timeline.** A viewer's timeline includes reposts by the athletes they effectively follow. It shows a repost as the original post with "Reposted by …", at the repost's position.
  - A post appears at most once in a timeline. That item sits at the latest of the original's position (when the viewer would see the original there) and the reposts' positions.
  - When the latest of those is a repost, the item says "Reposted by …", naming one reposter and how many others the viewer follows reposted it.
- `getTimeline`'s open union gains a repost item: the reposted `postView` with a `#reasonRepost` (the reposter shown, how many others, and the repost's position). Older clients skip it.
- `postView` gains `repostCount` and the viewer's repost. The card gains Repost and Undo repost, and shows the repost count.
- **The post page lists reposters.** Tapping the repost count lists the athletes who reposted the post that the viewer is shown, from a new `feed.getReposts`, as the likes list does.
- Blocks, mutes and labels apply to the reposter and the author separately. A block between the viewer and either one, a mute of either one, or a label hiding either the reposter's account or the post hides the repost.

Out of scope: reposting activities, reposting one's own post, reposts on profiles (Posts lists only an athlete's own posts), quote posts (`post-quotes`), and notifications.

## Capabilities

### New Capabilities
- `feed/reposts`: the repost record, where it's stored, who sees it, how the timeline shows and dedupes it, and `feed.getReposts`.

### Modified Capabilities
- `feed/posts`: repost counts and the viewer's repost, the card's Repost action, and reposts going with their post.
- `feed/post-page`: the Repost action and count, and the reposters list.
- `feed/timeline-order`: a repost's position and deduping.
- `spaces/appview-syncer`: reposts are indexed from `interactions` spaces.
- `graph/blocks`, `graph/mutes`: reposts by blocked or muted athletes, and reposts of their posts, are hidden.
- `moderation/labels`: a label on an account hides that account's reposts, and a label on a post hides its reposts.

## Impact

- **Lexicons**: a new `feed/repost.json` and `feed/getReposts.json`. `feed/defs.json` gains the repost timeline item, `#reasonRepost`, and `postView`'s `repostCount` and viewer repost. `getTimeline`'s union gains the repost item.
- **AppView**: a `reposts` table and migration, indexing from repos and `interactions`, `getReposts`, repost counts in `postView`, and a third source merged into `cardedTimeline` with dedupe by post URI.
- **Client**: repost writes through the outbox, the card's action, count and "Reposted by" header, the post page's reposters list, the feed cache, fixtures, and screenshots.
