# Proposal

## Why

`feed.getActivity` returns every comment on an activity in one unpaged array, oldest first. The detail screen loads them all before anything else shows. A popular activity's comments make it slow to open and heavy to serve, and every refresh fetches them all again. Comments need their own paged query.

Best after `comment-non-public-activities`, so that paging is built on the final rule for who sees each comment.

## What Changes

- **A new query, `app.activitytracker.feed.getComments`.** It takes `uri` (the activity), `limit` and `cursor`, and returns comment views oldest first. Paging is by keyset over `(created_at, uri)`. It applies the same rule for who sees each comment as the comment count does. An activity the viewer may not see is `NotFound`, as `getLikes` treats it.
- **BREAKING:** `feed.getActivity` stops returning `comments`. Its output keeps `activity`, which already carries `commentCount`. All our clients change in the same release. The AppView keeps answering old clients with an empty `comments` array until they're gone, so the field becomes optional rather than removed outright.
- **One serving rule.** The count in `hydrate` and the list in `getComments` share one filter on the comments table, so the number shown always matches what the list returns.
- **Detail screen.** It loads the first page of comments separately from the activity, then loads more as the viewer scrolls near the end. A comment the viewer has just sent is added at the end without fetching again.
- **Out of scope:** newest-first ordering, and replies (`comment-replies`, which pages by top-level comment).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/comments`: adds requirements that comments are listed oldest first in pages, and that the count matches the list. This capability is introduced by `comment-non-public-activities`.

## Impact

- **Lexicons:** a new `feed/getComments.json`, and `feed/getActivity.json` with `comments` optional and deprecated.
- **core/lexicon:** the `GetComments` descriptor and output, and the `GetActivity.Output` change.
- **AppView:** a `getComments` route and view; comments removed from `activity()` in `Views`; index use checked on `comments_subject_idx (subject_uri, created_at)`; tests.
- **Client data:** a comments pager in `ActivityRepository` or a new comments store.
- **Presenters and UI:** `ActivityDetailPresenter` (page state and a load-more event), `ActivityDetailUi`, and presenter tests.
- **Docs:** `.agents/rules/appview.md`.
