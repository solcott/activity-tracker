# Proposal

## Why

Once a comment is posted, there's no way to take it back. Comments have no delete action in any client. That's more of a problem once comments are written for a specific audience (`comment-non-public-activities`): a comment posted to the wrong activity, or one the commenter regrets, stays readable by its whole audience indefinitely.

Depends on `comment-non-public-activities`, which must land first.

## What Changes

- **Delete your own comment.** The detail screen offers Delete on the viewer's own comments, and only on those, after a confirmation. The comment leaves the list and the count drops at once, and the delete goes through the outbox.
- **Delete from both places.** A comment's URI is repo-style wherever it's stored, and the activity may have changed visibility since the comment was written, so the client can't tell where it is. A delete removes the rkey from the repo and from the commenter's `interactions` space, as unliking does. The space delete happens only when the session can write `feed.comment` to spaces. A missing record, or `SpaceNotFound`, counts as done.
- **AppView.** It already removes a comment's row when the record is deleted from that row's origin. This change adds tests for deleting from both places and for the double delete.
- **Out of scope:** removing other people's comments on your own activity (`hide-comments-on-own-activities`), and editing comments.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/comments`: adds a requirement that a commenter can delete their own comment, and that it's removed for every viewer wherever it was stored. This capability is introduced by `comment-non-public-activities`.

## Impact

- **Client data:** `ActivityRepository.deleteComment`, with deletes queued for both places and an optimistic `commentCount` update.
- **Screens and presenters:** a `DeleteComment` event, and a confirmation in `ActivityDetailScreen` and its presenter, with presenter tests.
- **UI:** a delete action on the viewer's own comments in `ActivityDetailUi`, built from the `ui/adaptive` wrappers, and new strings.
- **AppView:** tests only.
- **Lexicons:** none.
