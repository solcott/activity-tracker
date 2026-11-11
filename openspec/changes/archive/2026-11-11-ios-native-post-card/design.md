# Design

## Context

Posts (feed/posts, feed/quote-posts, feed/reposts) are drawn by the shared `PostCard` (`:app-common-ui`, `app.posts`) in the feed, on the profile's Posts tab, atop the post page (`PostPageUi`) and in the quotes list (`PostQuotesUi`). On iOS the post page and the quotes list fall back to Compose, and the native profile (`ios-native-profile`) leaves Posts out. Their presenters already run headless in `:app-host`.

The card chooses its words in the composable: "Reposted by …", the audience label, the proposal's place line, "Proposes …", its status and totals, the vote toggle, which buttons show (like and repost on others' posts, Quote once listed), the counts line, the quoted post's header and embed, "Post unavailable", and the menu (Delete on the viewer's own quote, Report on others').

The post page also shows the comment list (`CommentsUi`, `app.interactions`), shared with the activity page, which decides each comment's actions (`commentActions`, private to the UI module) and its words. The quote sheet (`QuoteSheetHost`) draws `QuoteFields`, a plain part of each host's state, not a sub-screen. Report on a post or a comment opens `ReportScreen`, a SubCircuit; another session (`ios-native-report-sheet`) is making that sheet native in parallel.

## Goals / Non-Goals

**Goals:** the card, the post page with its comments, likers and reposters, the quotes list, the quote sheet and delete confirmation, and the profile's Posts tab drawn natively in the shared words, with Kotlin deciding every text and condition both UIs show; snapshots of every case in light and dark; the edits to files the report branch also touches kept to added blocks.

**Non-goals:** Report (the other branch); the feed and the activity page natively; changing what any presenter loads or writes.

## Decisions

1. **`PostCardContent` in `:app-common-api` (`app.posts`), modelled on `ActivityCardContent`.** `PostCardContent.of(post, viewerDid, reason)` gives:
   - `isOwn`, `author()`, `audience()` (`LabelText.visibility`), `text`, `repostedBy()` (you, one, or one and others) and `reposter`;
   - `proposal` (the typed `PostEmbedView.Proposal?`, since Swift can't switch over the sealed embed whose `Unknown` member holds a `JsonObject`), `place()`, `proposes()`, `proposalStatus()` (status and totals), `canVote`, `votedUp`, `votedDown`, `voteUp()`/`voteDown()` (their spoken labels), and `vote(up)`, the `PostEvent.Vote` a tap sends, toggling off the viewer's own vote;
   - `quoted` (`QuotedPost.Post?`), `quotedUnavailable`, `quotedHeader()` ("Carol · Followers"), `quotedProposes()`, `quotedLink` (the `AtUri` a quoted quote links to), and `embedUnknown`;
   - `offersLike`, `liked`, `offersRepost`, `reposted`, `repostLabel()`, `offersQuote` (not pending), `counts()` ("2 likes · 1 comment · 0 reposts · 1 quote");
   - `offersDelete` (the viewer's own quote) and `offersReport(available)` (someone else's post, with a labeler), and `menuLabel(available)` for the shared menu's one item.
   The shared `PostCard` asks all of it, with no visible change. Swift builds the `PostEvent`s itself (`PostEvent.Like(post:)`), as the activity card builds `ActivityListEvent`s.

2. **`QuoteSheetTexts` (`app.posts`)**: the sheet's title, Post, the field's label, `tooLong` (with the limit), the public-only note, the audience row's name, an audience's label, and the delete confirmation's title, message and button. The shared `QuoteSheetHost` asks them. Swift reads `QuoteFields.audiences` with `elements(as: String.self)`, since text crosses an immutable list.

3. **Comments: `CommentsTexts` and rows, in `:app-common-api` (`app.interactions`).** `CommentAction` moves there (same package, so its users don't change) with `CommentsTexts.actions(comment, state, reportingAvailable)`, the rule the shared list used privately, which it now asks. For Swift, `CommentsTexts.rows(state)` flattens the threads into one typed list of `CommentRow`s in the shared order (each thread's root or its "Comment unavailable" placeholder, its replies, its just-sent replies, its "View n more replies" row; then the just-sent comments), each with a stable `key`, its kind (`CommentRowKind`), whether it is a reply, and for a comment its author, text, hidden note, notices, `offersReply`, and its menu as `CommentMenuItem`s (label, action, destructive, in the shared order). Swift drops `Report` from the menu until the native report sheet lands. Also `count()`, `title`, `replyingTo()`, the field's placeholder and Send, the delete and block confirmations' words, `moreReplies(thread)`, and `loading(state)` (a `CommentsLoad`: idle, loading, or failed with its `UiError`).
   - *Alternative:* Swift walking `paging.items` itself. Rejected: the thread structure, the indent and the menu's order would be written twice.

4. **`PostPageTexts` (`:feature-post-api`) and `PostPageState.page`.** `page` is the typed `PostPage?` (the `ContentState` value is erased); `PostPageTexts` has the title, unavailable, `likesToggle(shown)`, `repostsToggle(shown)`, `showQuotes`, `noLikes`, the quotes title and empty message; `PostPage.showsLikesToggle`, `showsRepostsToggle`, `showsQuotes` decide the buttons, as the shared page did inline.

5. **The native views.**
   - `PostCardView` is a list row like `ActivityCardView`: the reposted-by line (a plain button), the author line (avatar, name, audience; a plain button), the text, the embed, and the actions row (`AppIconButton`s for Like, Repost and Quote, and the counts, a button opening the quotes when the host gives `openQuotes`). A tap anywhere else, and the VoiceOver action, opens the post. The menu (`Menu`, "More for this post") shows only with Delete. The proposal's place line is a borderless button; the votes are icon buttons tinted when chosen. The quoted post is a rounded, outlined box that opens it, with the link as a button; "Post unavailable" sits in the same box and opens nothing.
   - `PostPage`: `ContentStateView` over `state.content`, or the unavailable text when `notFound`; then one grouped `List`: the refresh status, the card, the failed action, Show likes and the likers, Show reposts and the reposters (`AppListRow`s opening profiles), Show quotes, and `CommentsSection`. The comments' next page is asked for once one of the last five threads' rows is on screen and `paging.canLoadMore`, as the shared list does.
   - `CommentsSection`: a section headed by "Comments" and the count; each row a `CommentRowView` (replies indented 32 pt), the loading or failure row, and the field section (Replying to with Cancel reply, an `AppTextField` on `draft` and Send, the error). Delete and Block author ask with `.alert`s in the shared words.
   - `PostQuotesView`: a `PagedList` of `PostCardView`s, with the error row and "This post isn't available".
   - `.quoteSheet(fields)`: a view modifier any host puts on its page: a sheet while `quoting` is set (a `NavigationStack` with Cancel and Post in the bar, the quoted author and text, a multiline `AppTextField` on `text`, too long, an `AppPickerRow` of the audiences, the public-only note, the error; Post disabled unless `canPost`), and an alert while `confirmDelete` is set. Dismissing sends `Cancel`.
   - The profile's Posts tab (`ProfilePosts.swift`): a segmented `Picker` of Activities and Posts (`ProfileTexts.tab`) under the actions while `showsActivities`, and, on Posts, its rows built from `PagedShown` (refresh status, the cards, the empty message, the next page's status), asking for more once the last card shows. `ProfilePage` gains one `switch` on `state.tab` and the modifier; the rest of its body is unchanged, so the report branch's menu edit merges beside it.

6. **Report is left out natively, recorded as an open item.** The card's menu, the comment menu and the quotes list offer no Report on iOS (a spec delta adds the exception as its own requirement in `moderation/reports`, so the report branch can drop it in a separate hunk). Whichever branch merges second wires `ReportingEvent.Open` from these views to the native sheet.

7. **Samples.** `PostCardSamples` (`:app-fixtures`, `app.posts`): own and others' posts, voted, adopted, a repost through Bob, the viewer's own repost, a quote, a quote of a quote, an unavailable quote, an unknown embed, a pending post, and the viewer's own quote; with `viewer`. `PostPageSamples.variants` adds likers, a failed action, a hidden comment and a reply in progress, sent comments, more replies, and a failed comments page; `PostQuotesSamples` keeps `all`; `QuoteSheetSamples` gains `tooLong`, `publicOnly`, `failed` and `confirmDelete`; `ProfileSamples.variants` names Posts. Content-loading keys and the Android references stay as they are.

8. **Tests.** `PostCardContentTest`, `CommentsTextsTest`, `QuoteSheetTextsTest` and `PostPageTextsTest` (`commonTest`) over every branch. Swift: text tests reading each sample through the catalog, and snapshot suites for the card, the post page, the quotes list, the quote sheet and the Posts tab, light and dark (iOS 26.0); registry test lines.

## Risks / Trade-offs

- **iOS still has no Report on posts and comments** → recorded in the spec delta and open items; the report branch wires it.
- **Shared Swift files and specs are edited by the report branch too** → appended blocks only (`KotlinTypes.swift`, `Samples.swift`, `ScreenRegistry.swift`, `ScreenRegistryTests.swift`), the profile's posts in a file of its own, and `moderation/reports` as an added requirement.
- **`CommentAction` moving modules** → the same package, so only the import graph changes; the Compose list asks the moved rule.
- **A like, repost, vote, quote or comment on the walk writes to alice.test on the local stack** → each is undone.

## Open Questions

None blocking.
