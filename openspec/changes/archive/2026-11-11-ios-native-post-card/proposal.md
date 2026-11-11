# Proposal

## Why

Phase 4 of the native iOS UI (`openspec/explorations/ios-native-ui.md`) moves features one change at a time. The native profile (`ios-native-profile`) left its Posts tab out because posts had no native card, and the post page and its quotes list are still the shared UI's, so every post a native page could show would fall back to Compose. With the activity card native and the presenter host running sub-screens (`ios-host-subcircuits`), a native post card, the post page and the quotes list are next, and they let the native profile list posts again.

## What Changes

- **A native post card** (`PostCardView`), drawn from Kotlin's `PostCardContent` (`:app-common-api`), which the shared `PostCard` asks too: "Reposted by" over it, the author with the post's audience, the text, the embed (a proposal with its place, name, status and totals and the up and down votes when the viewer may vote; a quote with the quoted post one level deep, its proposal named or a link when it is itself a quote, or "Post unavailable" when the viewer can't see it; or the shared "can't be shown" line for an embed this version doesn't know), then Like and Repost on someone else's post, Quote on any listed post, and the counts. Its menu offers Delete on the viewer's own quote. Taps do what the shared card's do.
- **The post page is native** (`PostPage`): the card, a failed action, Show likes and Show reposts with their lists, Show quotes, and the comments (the count, threads with their replies and "View more replies", the viewer's just-sent comments, hidden comments dimmed with their note, Reply, a menu with Delete, Hide or Unhide, Mute author and Block author after the shared confirmations, the next page as the viewer scrolls, and the field with "Replying to"). "This post isn't available" for a post the AppView doesn't serve.
- **The quotes list is native** (`PostQuotesView`), its quotes as native cards a page at a time, with the empty message and "This post isn't available".
- **The quote sheet and the delete confirmation are native**: `QuoteFields` is a plain presenter state, not a sub-screen, so a SwiftUI sheet draws it (the quoted post, the athlete's words, who may see it, too long, the public-only note, the error) and an alert asks before Delete.
- **The native profile lists posts again**: the Activities and Posts choice under the header, and Posts as native cards a page at a time with the empty message, as the shared page does.
- **Report waits on every one of them.** Another change (`ios-native-report-sheet`) is making the report sheet native in parallel. Until it lands, the native post card, its comments and the quotes list offer no Report, as the native profile already doesn't; whichever branch merges second wires it.
- **Kotlin chooses the words and conditions, both UIs ask.** `PostCardContent`, `QuoteSheetTexts` (`:app-common-api`, `app.posts`), `CommentsTexts` with the comment rows and their actions (`app.interactions`; the shared `CommentsUi` asks it, and `CommentAction` moves to `:app-common-api`), `PostPageTexts` and typed getters on `PostPageState` (`:feature-post-api`), and `ProfileTexts.tab`/`postsEmpty`. No visible change on Android, desktop or web.
- **Samples, previews and snapshots** of the card in every case, the post page, the quotes list, the quote sheet and the profile's Posts tab, light and dark. Registry entries and their test.

Out of scope: Report (above), the feed and the activity page (still the shared UI's; the native comment list is ready for the activity page when it goes native), a post composer, and any change to what the post screens load or write.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/posts`: the native profile on iOS lists posts again; the post card is native on iOS.
- `feed/post-page`: the post page and its quotes list are native on iOS.
- `feed/quote-posts`: the quote sheet and the delete confirmation are native on iOS.
- `moderation/reports`: native post cards, comments and the quotes list on iOS don't offer Report until the native report sheet lands.
- `ui/ios-design-system`: a native post card and comment list.
- `dev/shared-display-text`: the post card's words and conditions, and which actions a comment offers, are chosen in shared code.

## Impact

- **`:app-common-api`:** `PostCardContent`, `QuoteSheetTexts`, `CommentsTexts`, `CommentRow`, `CommentAction` (moved from `:app-common-ui`); tests. **`:app-common-ui`:** `PostCard`, `QuoteSheetHost` and `CommentsUi` ask them.
- **`:feature-post-api`:** `PostPageTexts`, `PostPageState.page`. **`:feature-profile-api`:** `ProfileTexts.tab`, `postsEmpty`. **`:app-fixtures`:** card samples and post page variants.
- **`iosApp`:** `PostCardView`, `QuoteSheet`, `CommentsSection`, `PostPage`, `PostQuotesView`, the profile's Posts tab, aliases, samples, previews, snapshot and text tests, two registry entries.
- **Docs:** the roadmap's phase 4 entry, probes, findings and open items.
- **Risk carried:** the shared Swift files and specs are also edited by the report-sheet branch, so edits there are additive blocks; iOS 18 can't be walked signed in; snapshots compare only on iOS 26.0; a like, repost, vote, quote or comment on the walk writes to the local stack and is undone.
