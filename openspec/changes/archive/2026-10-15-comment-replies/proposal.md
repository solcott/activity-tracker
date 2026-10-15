# Proposal

## Why

Comments are a flat list, so a conversation under an activity is hard to follow, and replying to one person means writing an @-style note to everyone. Replies let athletes answer a specific comment, and let viewers read a conversation as a thread.

Depends on `comment-non-public-activities` and `paginate-comments`. Replies need a rule for who sees each comment, and they page by top-level comment.

## What Changes

- **A reply is a comment with a parent.** `feed.comment` gains an optional `reply` field holding strong refs to the comment replied to (`parent`) and to the top-level comment of its thread (`root`). `subject` still names the activity.
- **Threads are one level deep.** Every reply belongs to a top-level comment's thread, and replying to a reply adds to the same thread (as Instagram and YouTube do). The record still names the reply it answers.
- **A reply's audience is capped by its parent's.** The client sets a reply's `audience` to the narrower of the activity's visibility as the viewer sees it and the parent comment's audience. A reply can never reach viewers its parent was hidden from. The AppView also never shows a reply wider than its thread's top-level comment.
- **A comment is stored by its audience.** A comment written for Public goes to the commenter's repo, and any other to their `interactions` space. For a top-level comment this is the same as today's rule, since its audience is the activity's visibility. A reply to a Followers comment on an activity since made Public is therefore kept out of the repo.
- **Serving.** `getComments` pages over threads, oldest first. Each thread comes with its first few replies and a reply count, and a new mode pages through the rest of one thread's replies. Replies count toward the activity's comment count.
- **Missing top-level comments.** When a thread's top-level comment isn't listed for the viewer (deleted, hidden by the athlete, or its commenter can no longer see the activity) but some of its replies are, the thread shows a "comment unavailable" placeholder that reveals nothing about it, with the replies under it.
- **UI.** The detail screen gets a Reply action on comments and replies, shows replies indented under their thread, and has a "View n more replies" control. A comment sent this session that the AppView hasn't listed yet can't be replied to until it is.
- **Out of scope:** notifying commenters of replies, @-mentions, "replying to" labels on replies, and editing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/comments`: adds requirements for replying to a comment, capping a reply's audience by its parent's, how threads and replies are served and paged, and the placeholder for an unavailable top-level comment. Changes where a comment is stored (by its audience), and how the count, paging and the detail screen account for replies.

## Impact

- **Lexicons:** `feed/comment.json` (the optional `reply` field, a `#replyRef`), `feed/defs.json` (`#threadView`, and `root`, `parent` and `audience` on `#commentView`) and `feed/getComments.json` (the `root` parameter and `threads` output).
- **core/lexicon:** `CommentRecord.reply`, `ReplyRef`, `ThreadView`, the `CommentView` fields, and `GetComments`.
- **AppView:** `thread_uri` and `parent_uri` columns on `comments`, with an index (migration `V22`); the `Indexer`; `getComments` grouped by thread; tests for audiences, unavailable top-level comments, and paging.
- **Client data:** `ActivityRepository.comment` takes the comment replied to, and computes the capped audience and the storage place. `LocalComments` keeps a sent reply's thread.
- **Screens, presenters and UI:** reply state and events in `ActivityDetailScreen`, a threaded comment list in the detail UI, and presenter tests.
- **Docs:** `.agents/rules/appview.md` and `data-and-auth.md`.
