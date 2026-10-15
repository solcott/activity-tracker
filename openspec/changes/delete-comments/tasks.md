# Tasks

## 1. AppView coverage

- [x] 1.1 Sync-path coverage: `SpaceSyncTest.interactionsSpaceIsSyncedForItsComments` already checks a comment deleted from the `interactions` space is dropped after the sync notification and a repo-origin delete of the same rkey leaves it, and the AppView's `ActivityVisibilityTest.commentsOnANonPublicActivityAreServedToItsAudience` checks the space delete at `getActivity` level with the count; verify with `./gradlew :server-appview:test --tests "*.SpaceSyncTest"`
- [x] 1.2 In the AppView's `ActivityVisibilityTest` (which has the viewer-auth and count-checking `commenters` helper), add `aDeletedCommentIsGoneForEveryViewerWhicheverDeleteArrivesFirst`: a repo comment on a Public activity survives a space-origin delete, then a Jetstream delete drops it from `getActivity`'s list and `commentCount` for signed-out, other and owner viewers; a space comment deleted space-first then repo is gone, and the activities are untouched; verify with `./gradlew :server-appview:test --tests "*.ActivityVisibilityTest"`

## 2. Client data layer

- [x] 2.1 Add deleted-comment URIs to `LocalDeletes` (`deleteComment(uri)`), and have `FeedRepository.activity` drop them from `output.comments` and lower `activity.commentCount` by the number dropped; verify with a `core/data` jvm test that a reload while the AppView still lists the comment hides it and counts one fewer, and counts unchanged once the AppView no longer lists it
- [x] 2.2 Add `ActivityRepository.deleteComment(activity, comment)`: ignore a comment whose author isn't the session's DID, queue `writer.delete(feed.comment, rkey)` for the repo and, when `canWriteToSpaces(feed.comment)`, for the `interactions` space, then add the URI to `LocalDeletes`, lower the cached `commentCount` (floored at 0) and drain; verify with `ActivityVisibilityTest` tests that a non-public comment is deleted from the space and the repo delete succeeds, a Public comment with no `interactions` space leaves the outbox empty (`SpaceNotFound` counts as done), a session lacking the `feed.comment` space scope queues the repo delete only, and someone else's comment writes nothing
- [x] 2.3 Update the likes/comments bullet in `.agents/rules/data-and-auth.md` to say deleting a comment queues deletes from both places like unliking, with the space one only under `canWriteToSpaces(feed.comment)`, and is hidden through `LocalDeletes`; verify `scripts/check-agent-sync.sh` passes

## 3. Screen contract and presenter

- [x] 3.1 Add `ActivityDetailEvent.DeleteComment(uri: AtUri)`, `ActivityDetailState.Loaded.viewerDid: Did` and `UiError.Kind.DeleteComment` in `:app-screens`; verify `./gradlew :app-screens:composeStabilityCheck` passes
- [x] 3.2 Handle `DeleteComment` in `ActivityDetailPresenter`: remove the comment from `comments` at once, call `activities.deleteComment` in `flights.launch(event.uri)`, and on failure restore it at its position and set `commentError` to `DeleteComment`; fill `viewerDid` from `profiles.did`; verify with `ActivityDetailPresenterTest` tests that the comment leaves the list and the count drops at once, a double event while the first is in flight queues one delete pair (gated fake), a failure puts it back with the error, and a retry reload still hides it

## 4. UI

- [x] 4.1 In `ActivityDetailUi`'s `Comments`, give each comment whose `author.did == state.viewerDid` an `AppMenu` with a destructive Delete `AppMenuItem` that opens an `AppAlertDialog` confirmation, sending `DeleteComment` only on confirm; add the menu, dialog and `DeleteComment` error strings to `strings.xml` and map the error kind in `errorText`; verify by running `./gradlew :desktopApp:hotRun --auto` and checking via the hot-reload MCP semantic tree that only own comments show the menu and that cancel leaves the comment while confirm removes it

## 5. Integration

- [x] 5.1 Run `./gradlew check` and verify it passes (tests on all targets, lint, ktfmt, detekt including `UseAdaptiveComponents` and `UseSingleFlight`, stability)
- [x] 5.2 End to end on the local stack, signed in as bob in the desktop app: deleted his comment written in his `interactions` space for a Followers-era copy of alice's now-Public activity, and a repo comment on that Public activity; verified each left the list and count at once, the AppView row was dropped (space `notifyWrite` sync, Jetstream repo delete), bob's repo holds no `feed.comment`, the outbox drained, and the signed-out `getActivity` lists only alice's remaining comment

## Workflow follow-up

- Archive the change with `/opsx:archive` after review, syncing the `feed/comments` delta into the main spec.
