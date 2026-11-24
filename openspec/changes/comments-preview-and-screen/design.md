# Design

## Context

Today both subject pages own their comments. `ActivityDetailPresenter` and `PostPagePresenter` each call `rememberComments` (`:app-common-presenter` `interactions/Comments.kt`) for the paged threads, run `CommentActions` for sends, deletes, hides, blocks and mutes, and keep the field (`draft`, `replyingTo`) in a `CommentsState`. The shared Compose UI draws it with `LazyListScope.comments` and `LoadMoreNearTheEnd` (`:app-common-ui` `CommentsUi.kt`), the native page with `CommentsSection`. Each page moves its own comment count by the actions taken on it (`CommentMoves` on the activity page, `moved` on the post page). The viewer's own comment changes already live app-wide in `FeedRepository.localComments` (`LocalComments`), which `rememberComments` lays over every page it serves.

The look follows the impeccable `shape` pass (Operate mode): see proposal.md and the specs for what shows.

## Goals / Non-Goals

**Goals:**
- One Comments screen for any subject, holding every comment behaviour the pages hold today.
- Pages keep only what the block needs: the count and the first page's first two top-level comments.
- The count on a page stays right after changes made on the Comments screen.

**Non-Goals:**
- No change to how comments are stored, served, paged or moderated, and no lexicon or AppView change.
- No new preview query: the preview is read from the first page of threads.
- Notifications keep opening the subject's page; pointing them at the Comments screen is a later choice.

## Decisions

**A feature of its own, `feature/comments`.** `CommentsScreen(subject: String, focus: Boolean = false)` in `:feature-comments-api`, presenter and UI in their modules, scaffolded with the `new-screen` skill. Both `feature/detail` and `feature/post` reach it through its `-api` only. Alternative: a screen inside `feature/detail` that `feature/post` opens; rejected because a feature may reach another only through its `-api`, and comments belong to neither page.

**The screen reads its subject's view.** The Comments screen needs the count, the subject's author (whose hides count, "Hidden by"), whether it is the viewer's own, the subject's visibility (a reply's audience cap) and a subtitle. It reads the activity or post view by the URI's collection through the same repository reads the pages use, which the page just loaded, so coming from a page shows at once and a link loads it. The screen's content is that view's `ContentState`; the threads are a `PagedState` as today. Alternative: pass the author and count as screen arguments; rejected because links must carry IDs only and a passed count goes stale.

**`focus` says how the screen was reached.** "Add a comment" opens it with `focus = true`, which the field takes once; it is left out of the link. Reply on the Comments screen sets `replyingTo` and focuses the field as today.

**The pages keep `rememberComments` for the preview, without paging on.** The page presenters still call `rememberComments(subject, …)` for its first page and never `loadMore`. A small `CommentsPreview` contract in `:app-common-api` (`count`, `rows`, `canComment`, an `eventSink` for `OpenAll` and `Compose`) replaces `CommentsState` in `ActivityDetailPage` and the post page's state. `CommentsTexts.preview(threads, sent)` picks the rows (first two listed, not hidden, unlabelled top-level comments, falling back to the viewer's just-sent ones) so Compose and Swift ask the same rule. Alternative: a dedicated `limit=2` read; rejected, the first page is already fetched and cached with the subject.

**One count rule for every screen.** `CommentMoves` and the post page's `moved` are replaced by one helper in `:app-common-presenter` that moves a loaded view's count by the viewer's `LocalComments` changes on that subject made since the view loaded and not yet reflected by it, the rule `CommentMoves` follows today but read from the app-wide store instead of this screen's actions. The Comments screen, the activity page and the post page all use it, so a send on the Comments screen moves the page's count when the viewer comes back. Alternative: reload the page on return; rejected, it refetches the whole activity and still misses changes the AppView hasn't indexed.

**The list loses its field; the field docks.** `LazyListScope.comments` drops its `CommentField` item, which becomes a `CommentComposer` composable the Comments screen places in `AppScaffold`'s bottom bar (inside the IME insets, so it rides the keyboard). The list runs in `ReadableList` with `LoadMoreNearTheEnd` as now. On iOS `CommentsSection` drops its field; `CommentsView` places the native composer in `safeAreaInset(edge: .bottom)` and focuses it with `@FocusState` when `focus` is set.

**The block's shape.** Compose `CommentsPreviewBlock` (`:app-common-ui`) sits in the item after Likes on both pages; its "Add a comment" row is an outlined, field-shaped button (not an `AppTextField`), so the page never holds a keyboard. iOS `CommentsPreviewSection` is a list `Section` after the likes section on `ActivityPage` and after the card on `PostPage`.

## Risks / Trade-offs

- [A comment written on the page is now one screen away] → the "Add a comment" row opens the screen with the keyboard up, so it stays one tap.
- [Two screens read the same first page] → both use the retained, cached first page of `rememberComments`; the Comments screen shows it at once when it opens.
- [Count rule moved to the app-wide store] → keep `ActivityDetailCommentsTest`'s and the post page's count scenarios, and add ones that send on the Comments screen and assert the page's count on return.
- [Screenshot references for the activity and post pages change] → update them in the same change after looking at them.

## Migration Plan

Client-only. No data migration; ships with the next app release. Rollback is reverting the change.
