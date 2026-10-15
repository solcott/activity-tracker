# Proposal

## Why

An athlete has no control over the comments on their own activities. Every comment is a record in its commenter's repo or `interactions` space, which the athlete can't delete. Once comments reach Public activities and their whole audience (`comment-non-public-activities`), spam and abuse under someone's activity stay up for as long as the commenter wants them to. Athletes need a way to hide a comment from their activity without the commenter's cooperation.

`comment-non-public-activities` and `delete-comments` have landed, and this change builds on both.

## What Changes

- **Hide a comment.** On their own activity, an athlete can hide any comment someone else wrote. A hidden comment leaves the activity's comment count for every viewer, and the comment list for everyone except the athlete and its commenter. The athlete can unhide it later.
- **Hidden, not deleted.** The athlete sees a hidden comment in the list, marked as hidden, with Unhide on it. The commenter sees their own comment marked as hidden too, so the hide isn't a silent shadow-ban. Nobody else sees it.
- **The hide is a record the athlete owns.** A hide is a new record type, `feed.commentHide`, which names the comment. Its record key is derived from the comment, so hiding is idempotent and unhiding deletes a known record. It's written through the outbox. The AppView indexes it and counts a hide only when its author is the athlete whose activity the comment is on.
- **Hides are stored like the athlete's likes and comments.** On a Public activity the hide goes in the athlete's repo, so it's public, as Bluesky's hidden replies are. On a Followers, Mutuals or Only-me activity it goes in the athlete's `interactions` space, which only they and the AppView read. A hide stays where it was written when the activity's visibility changes, and the AppView honours it from either place. Unhiding deletes it from both places, as deleting a comment does.
- **Sign in again for non-public hides.** The `interactions` space declaration gains `feed.commentHide`. A session whose `space:` grant predates that is refused before writing, and told to sign in again. Hiding comments on Public activities keeps working.
- **Out of scope:**
  - Stopping a commenter from commenting again. Muting and blocking commenters is a later change of its own.
  - Reporting comments to anyone but the activity's athlete, and moderation across the AppView.
  - Hiding your own comment, since you can delete it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/comments`: adds requirements that an activity's athlete can hide and unhide comments on it, and that hidden comments aren't counted or listed. This capability is introduced by `comment-non-public-activities`.
- `spaces/appview-syncer`: the AppView indexes `feed.commentHide` from `interactions` spaces, as well as likes and comments.

## Impact

- **Lexicons:**
  - a new `feed/commentHide.json` record;
  - `hidden` on `feed/defs.json#commentView`;
  - `feed.commentHide` in `space/athlete.json`'s `collections`, which means another re-sign-in.
- **core/lexicon:** `CommentHideRecord`, `CommentView.hidden`, and `Nsid.FEED_COMMENT_HIDE` in `indexedCollections`.
- **AppView:**
  - a `comment_hides` table and its migration;
  - the `Indexer`, for both the repo and `interactions` spaces;
  - comment counts and lists in `Views`, which check that a hide's author is the activity's athlete;
  - tests.
- **Client data, screens and UI:** hide and unhide actions on comments under the athlete's own activities, with presenter tests.
- **Docs:** `.agents/rules/appview.md` and `data-and-auth.md`.
