# Proposal

## Why

Comments are a flat list, so a conversation under an activity is hard to follow, and replying to one person means writing an @-style note to everyone. Replies let athletes answer a specific comment, and let viewers read a conversation as a thread.

Depends on `comment-non-public-activities` and `paginate-comments`. Replies need a rule for who sees each comment, and they page by top-level comment.

## What Changes

- **A reply is a comment with a parent.** `feed.comment` gains an optional `reply` field holding a strong ref to the parent comment. `subject` still names the activity, so every comment and reply on an activity shares one root. A reply is stored where any comment on that activity would be (the repo or the `interactions` space), by the same rule.
- **A reply's audience is capped by its parent's.** The client sets a reply's `audience` to the narrower of the activity's visibility as the viewer sees it and the parent comment's audience. A reply can never reach viewers its parent was hidden from.
- **Serving.** `getComments` pages over top-level comments, and each comes with its replies, or the first few plus a reply count. A reply is shown only if its parent is shown to the viewer.
- **Missing parents.** When a parent can't be shown (it was deleted, hidden by the athlete, out of the viewer's audience, or its commenter can no longer see the activity), its replies need a defined result. Design decides between two options:
  - hide the whole subtree;
  - show a "comment unavailable" placeholder that reveals nothing about the parent.
- **Thread depth is a design decision.** Options are a single level (replies to replies attach to the top-level comment, as Instagram and YouTube do) or arbitrary nesting. The proposed default is a single level, since it's simpler to page and reads well on phones.
- **UI.** The detail screen gets a Reply action on comments, shows replies indented under their parent, and has a "View n more replies" control.
- **Out of scope:** notifying commenters of replies, @-mentions, and editing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/comments`: adds requirements for replying to a comment, capping a reply's audience by its parent's, how replies are served and paged, and what happens to replies whose parent can't be shown. This capability is introduced by `comment-non-public-activities`.

## Impact

- **Lexicons:** `feed/comment.json` (the optional `reply` field), `feed/defs.json` (reply fields on `#commentView`, such as `parent` and `replyCount`) and `feed/getComments.json`.
- **core/lexicon:** `CommentRecord.reply`, and the `CommentView` fields.
- **AppView:** a `comments.parent_uri` column and its index (a migration); the `Indexer`; `getComments` grouping and filtering by parent; tests for audiences, deleted and hidden parents, and paging.
- **Client data:** `ActivityRepository.comment` takes a parent, and computes the capped audience and the storage place.
- **Screens, presenters and UI:** reply state and events in `ActivityDetailScreen`, a threaded comment list in `ActivityDetailUi`, and presenter tests.
- **Docs:** `.agents/rules/appview.md` and `data-and-auth.md`.
