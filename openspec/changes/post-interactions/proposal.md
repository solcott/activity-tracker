# Proposal

## Why

`feed-posts` puts shared name proposals in the timeline, but a post can't be discussed there. Riders who have something to say about a proposed name have to say it elsewhere. Likes and comments already exist for activities, with audience and privacy rules worked out. Extending them to posts makes a post somewhere people can talk about the name, in front of the same audience the proposer chose. This is the second of four post changes, after `feed-posts`.

## What Changes

- **Likes on posts.** `feed.like` can name a `feed.post`, told apart from an activity by the subject's collection.
  - A like on a Public post goes in the liker's repo, and a like on any other post goes in their `interactions` space, as for activities.
  - A like is shown only to viewers who, like its giver, may see the post now.
- **Comments on posts.** `feed.comment` can name a post. The rules match comments on activities:
  - a comment's `audience` is the post's visibility as the commenter's view showed it;
  - a reply's audience is the narrower of that and the comment it answers;
  - threads are one level deep;
  - a comment is shown only to viewers its audience admits;
  - an author can hide comments on their own posts (`feed.commentHide`).
- **A post page.** It opens from the card and shows the post, its likes and its comment thread, in the content-loading pattern. The card gains like and comment counts and a like button.
- `postView` gains `likeCount`, `commentCount` and the viewer's like.
- Blocks, mutes, labels and reports apply to likes and comments on posts as they do on activities.

Out of scope: notifications, reposts and quotes (`post-reposts`, `post-quotes`).

## Capabilities

### New Capabilities
- `feed/post-page`: the post page, with the post, its likes and its comments.

### Modified Capabilities
- `feed/posts`: the view's counts and viewer like, and the card's like and comment affordances.
- `feed/comments`: a comment's subject may be a post, and its audience comes from the post's visibility.
- `spaces/appview-syncer`: likes, comments and hides naming posts are indexed from `interactions` spaces.
- `graph/blocks`, `graph/mutes`: likes and comments on posts are hidden as on activities.
- `moderation/reports`: Report on comments on posts.

## Impact

- **Lexicons**: the `feed.like`, `feed.comment` and `commentHide` descriptions now allow a post subject; `postView` gains counts; `getLikes` and `getComments` accept a post URI.
- **AppView**: the like and comment indexers accept post subjects, and audience checks use the post's tier. This needs post-subject columns or a generic subject on `likes` and `comments`, plus a migration.
- **Client**: like and comment repositories with post subjects, a new `feature/post` (api, presenter and ui), the card's counts, fixtures, and screenshots.
