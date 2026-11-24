# Proposal

## Why

An activity's comments sit at the very end of its page, under every metric card, segment effort, lift and the stats list, so on a long ride the conversation and the way to join it are screens away. A post's page ends with the same list. Athletes should see at a glance what people said and be one tap from replying, without the comment list pushing the activity's figures down.

## What Changes

- The activity page shows a comments block right after its likes (in the side pane at wide widths): the comment count, a preview of the first two top-level comments, "View all N comments", and an "Add a comment" row. The full threaded list and its field leave the page.
- A new Comments screen lists a subject's comments in full (an activity's or a post's): threads a page at a time with their replies, Reply, Delete, Hide and Unhide, Report, Block and Mute, as the page did, with the comment field docked at the bottom. Opened from "Add a comment" or Reply, the field takes focus.
- The post page gets the same block under its card in place of its list, and opens the same Comments screen.
- The comment count a page shows follows the viewer's sends, deletes, hides and unhides made on the Comments screen, at once and before the AppView catches up.
- The Comments screen has a link path under its subject's (`/profile/{did}/activity/{rkey}/comments`, `/profile/{did}/post/{rkey}/comments`), opening with its subject's page under it.
- On iOS the block and the Comments screen are drawn natively, the field docked above the keyboard.

## Capabilities

### New Capabilities

None. The Comments screen belongs to `feed/comments`.

### Modified Capabilities

- `feed/comments`: the detail screen's list requirements move to the Comments screen; new requirements for the page's comments block, the Comments screen and its field, and the count across screens.
- `feed/post-page`: the post page shows the comments block and opens the Comments screen instead of listing comments; its native requirement follows.
- `feed/activity-page-layout`: the page's order puts the comments block after likes, at every width and natively on iOS, instead of comments last.

## Impact

- New feature modules `feature/comments/{api,presenter,ui}` with `CommentsScreen`, joined to `composeApp`, `desktopApp`, `webApp` and `:app-graph`.
- `:app-common-api` and `:app-common-presenter` `interactions/` (preview contract and texts, shared count over `LocalComments`), `:app-common-ui` `CommentsUi.kt` (list without its field, the block).
- `feature/detail` and `feature/post` presenters and UIs: comments block in place of the list; `CommentMoves` and the post page's moved count replaced by the shared count.
- `:app-links` routes and samples; `:app-fixtures` samples; screenshot references for the activity and post pages.
- iOS: `CommentsView` (new, registered), `CommentsSection`, `ActivityPage`, `PostPage`, snapshot suites.
- No lexicon, AppView or data-layer changes.
