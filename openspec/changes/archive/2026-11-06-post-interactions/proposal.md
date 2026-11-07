# Proposal

## Why

`feed-posts` puts shared name proposals in the timeline, but people can't respond to a post there. Riders who want to say something about a proposed name have to say it somewhere else. Likes and comments already exist for activities, with their audience and privacy rules worked out. Extending them to posts lets people talk about the name in front of the audience the proposer chose. This is the second of four post changes, after `feed-posts`.

## What Changes

- **Likes on posts.** A `feed.like` can name a `feed.post`. The AppView tells it apart from a like on an activity by the subject's collection.
  - A like on a Public post goes in the liker's repo.
  - A like on a Followers or Mutuals post goes in the liker's `interactions` space, as for activities.
  - A like is counted and listed only while both the viewer and its giver may see the post now.
  - An author can't like their own post.
- **Comments on posts.** A `feed.comment` can name a post, and it works as a comment on an activity does:
  - A top-level comment's `audience` is the post's visibility as the commenter's view showed it.
  - A reply's audience is capped by its parent's.
  - Threads are one level deep.
  - A comment is shown only to the viewers its audience admits.
  - The post's author can hide comments on it (`feed.commentHide`).
- **A post page.**
  - A new `feed.getPost` query serves one post.
  - The page shows the post's card, who liked it, and its comment threads, a page at a time. It follows the content-loading pattern.
  - Tapping the card now opens the post page. The segment or site on the card still opens its own screen.
- **Counts on the card.**
  - `postView` gains `likeCount`, `commentCount` and `viewer.like`.
  - The card shows both counts and a like button.
- **Blocks and mutes.** They apply to likes and comments on posts as they do on activities.

Out of scope:
- Notifications.
- Reposts and quotes (`post-reposts`, `post-quotes`).
- Comments on a post whose proposal has been withdrawn. The post goes, and its comments go with it.

## Capabilities

### New Capabilities
- `feed/post-page`: the post page, with its query, the card it opens from, likers and comment threads.

### Modified Capabilities
- `feed/posts`:
  - New: likes and comments on posts, their storage and audience, who sees them, and hiding.
  - New: the view's counts and the viewer's like.
  - Changed: the card opens the post page, while the segment or site on it still opens its own screen.
- `graph/blocks`: likes and comments on posts are hidden across a block, as on activities.
- `graph/mutes`: a muted athlete's comments on posts are hidden from the muter.

## Impact

- **Lexicons:**
  - The descriptions of `feed.like`, `feed.comment`, `feed.commentHide`, `getLikes` and `getComments` allow a post as the subject.
  - `postView` gains `likeCount`, `commentCount` and `viewer` (a new `#postViewerState`).
  - A new query, `feed.getPost`.
- **AppView:**
  - Like and comment serving joins the post a like or comment names, as well as an activity.
  - The audience and hide checks use the post's tier and author.
  - `getLikes`, `getComments` and the post views count and list them.
  - A new `getPost` route.
  - The indexer and the `likes` and `comments` tables already accept any subject URI, so there's no migration.
- **Client:**
  - The like, comment, delete and hide writes take a subject that may be a post.
  - Posts get optimistic count updates.
  - The comment list moves from `feature/detail` to the shared modules.
  - A new `feature/post` (api, presenter and ui).
  - The card gains its counts and like button.
  - New fixtures and screenshots.
