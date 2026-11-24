# Proposal

## Why

Athletes can like an activity or a post but not a comment on one, so the only way to acknowledge a comment is to reply to it.

## What Changes

- A `feed.like` may name a comment (`app.activitytracker.feed.comment`) as its subject, besides an activity or a post. Self-likes stay dropped.
- `commentView` gains `likeCount` and `viewer.like`, so every comment the AppView lists says how many athletes liked it and whether the viewer did.
- The AppView counts a like on a comment only while its giver may still see the comment: the subject (activity or post) at its current visibility, and the comment's audience and its thread root's audience, judged against the subject's author, with no block between the giver and the viewer.
- Clients like and unlike a comment from the Comments screen, storing the like in the repo when the comment's effective tier is public and in the interactions space otherwise, and show the change at once.
- Each comment row on the Comments screen shows a like button with its count after Reply; the viewer's own comments show the count only. The preview rows on the activity and post pages stay without actions.

## Capabilities

### New Capabilities
- `feed/comment-likes`: liking comments on activities and posts, who is counted, where a like is stored, and how the Comments screen shows it.

### Modified Capabilities

## Impact

- Lexicons: `feed/like.json` (description), `feed/defs.json` (`commentView.likeCount`, `commentViewerState`).
- `core/lexicon` models; AppView comment views (`api/CommentQueries.kt`) and their callers; no migration (the `likes` table already takes any subject).
- `core/data`: placing and toggling a like on a comment, the local like overrides applied to comments, cached counts left alone for comments.
- `:app-common-api`/`-presenter`/`-ui` comment rows and events; iOS `CommentsSection`.
