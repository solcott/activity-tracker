# Proposal

## Why

An athlete has no control over the comments on their own activities. Every comment is a record in its commenter's repo or `interactions` space, which the athlete can't delete. Once comments reach Public activities and their whole audience (`comment-non-public-activities`), spam and abuse under someone's activity stay up for as long as the commenter wants them to. Athletes need a way to hide a comment from their activity without the commenter's cooperation.

Depends on `comment-non-public-activities`, which must land first.

## What Changes

- **Hide a comment.** On their own activity, an athlete can hide any comment. A hidden comment drops out of the activity's comment list and count for every viewer. They can unhide it later.
- **The hide is a record the athlete owns.** A hide is a new record type, `feed.commentHide`, which names the comment (and the activity it's on) and is written through the outbox. The AppView indexes it and leaves out hidden comments when it serves comments. A hide counts only when its author is the author of the comment's activity.
- **Open questions for design:**
  - **Where hides are stored.** One option is the repo for comments on Public activities and the athlete's `interactions` space otherwise. Hiding a public comment would then be public, as Bluesky's hidden replies are. The other is always the `interactions` space: private, but even moderating a Public comment needs the `space:` scope.
  - **What the commenter sees.** Either the comment is hidden from everyone, or the commenter still sees their own comment, marked as hidden.
  - **Blocking.** Whether a hide should also stop the commenter from commenting again. Muting or blocking commenters is probably a change of its own.
- **Out of scope:** reporting comments to anyone but the activity's athlete, and moderation across the AppView.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/comments`: adds requirements that an activity's athlete can hide and unhide comments on it, and that hidden comments aren't counted or listed. This capability is introduced by `comment-non-public-activities`.
- `spaces/appview-syncer`: if hides are stored in `interactions` spaces, the AppView indexes `feed.commentHide` from them.

## Impact

- **Lexicons:** a new `feed/commentHide.json` record. Possibly `space/athlete.json` (`collections`, which would mean another re-sign-in) and `Nsid.indexedCollections`.
- **core/lexicon:** `CommentHideRecord`, and `Nsid.FEED_COMMENT_HIDE`.
- **AppView:**
  - a `comment_hides` table and its migration;
  - `Indexer`, which checks that a hide's author is the activity's author;
  - comment counts and lists in `Views`;
  - tests.
- **Client data, screens and UI:** hide and unhide actions on comments under the athlete's own activities, with presenter tests.
- **Docs:** `.agents/rules/appview.md` and `data-and-auth.md`.
