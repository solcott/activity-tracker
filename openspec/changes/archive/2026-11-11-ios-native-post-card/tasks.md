# Tasks

## 1. Probe (throwaway code, not merged)

- [x] 1.1 Read the generated Swift for `PostView`, `PostEmbedView`, `QuotedPost`, `CommentsState`, `PostPageState` and `QuoteFields`: which members reach Swift typed, and what needs a getter. Verify by noting each in the roadmap's probes.
- [x] 1.2 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (posts)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [x] 2.1 `PostCardContent` (decision 1) and `QuoteSheetTexts` (decision 2) in `:app-common-api`; `PostCard` and `QuoteSheetHost` ask them. Verify a `commonTest` over every branch and `:app-common-ui:jvmTest`.
- [x] 2.2 `CommentAction`, `CommentsTexts`, `CommentRow` in `:app-common-api` (decision 3); `CommentsUi` and `CommentMenu` ask them. Verify a `commonTest` and the comment UI tests.
- [x] 2.3 `PostPageTexts`, `PostPageState.page` and the `PostPage` getters (decision 4); `ProfileTexts.tab` and `postsEmpty`; the Compose pages ask them. Verify `:feature-post-ui:jvmTest` and `:feature-profile-ui:jvmTest`.
- [x] 2.4 The samples (decision 7). Verify `ContentLoadingConformanceTest` and the Android screenshot tests pass with no reference changed.

## 3. The native views

- [x] 3.1 Type aliases (one appended block) and samples (appended). Verify the text tests read every sample.
- [x] 3.2 `PostCardView`, `.quoteSheet`, `CommentsSection` (decision 5). Verify their previews and snapshot suites.
- [x] 3.3 `PostPage` and `PostQuotesView`, registered (appended entries). Verify `ScreenRegistryTests` names both.
- [x] 3.4 The profile's Posts tab. Verify the profile snapshots of `posts`, `noPosts` and `postsFailed`.
- [x] 3.5 Snapshot tests record on a first run and pass on a second on an iOS 26.0 simulator. Verify each image was looked at, light and dark.

## 4. The whole change together

- [x] 4.1 `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [x] 4.2 The Swift tests on `D6328F65-DD4F-4359-892A-31D995B1C1F9` (iOS 26.0) and `958C0DC3-FF20-44F5-9D02-7A170A295DD3` (iOS 18.4). Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [x] 4.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 5. See it

- [x] 5.1 On the signed-in iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`, as alice on the local stack: her profile's Posts tab, a post page (card, likes, comments), its quotes, and the quote sheet opened and cancelled; any write undone. Verify by noting what was seen; record what needs data the stack lacks under "Not verified".

## 6. Docs

- [x] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (posts)", the open items (the profile's Posts closed; Report on posts and comments; the feed and activity page). Verify `openspec validate ios-native-post-card --strict` passes.
- [x] 6.2 `.agents/rules/compose-screens.md` if a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [x] 6.3 A "Not verified" section listing what wasn't seen and why.

## Walk (iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

The local stack had no posts at all (a post can only share its author's proposal or quote another post), so one was made for the walk:
a public post by rider1.test sharing rider1's own adopted "Wiggly Woods" proposal, written to the local PDS with the seed scripts'
test password, and deleted after.

- **5.1.** alice's You tab: the Activities and Posts choice under her entries; Posts showed "No posts yet" (she has none). rider1's
  profile (from Search, the shared page) showed the choice too, and Posts drew the native card: rider1, Public, the words, "Name for
  segment Wiggly Woods", "Proposes “Wiggly Woods”", "Adopted · 3 up · 0 down" (no votes on a closed proposal), Like, Repost, Quote and
  the counts. A tap on the card pushed the native post page: the card, "Comments · 0 comments" and the field. Like gave a like ("1
  like", Show likes appeared), Show likes listed alice, and Like again took it back ("0 likes", "No likes yet."). Quote opened the
  native sheet over rider1's post, Public offered, Post held back until words were typed; Post closed the sheet and the page showed "1
  quote" and Show quotes, which pushed the native quotes list with alice's quote (the quoted post one level deep with its proposal
  named) and its menu. Delete quote asked "Delete this quote?" in the shared words; Delete removed it and the list showed "No quotes of
  this post yet." Back on the post page a comment typed in the field and sent showed at once with its menu; Delete asked "Delete this
  comment?" and removed it.
- **Put back:** the like, the quote and the comment were undone in the app; rider1's post was deleted from the PDS, and the AppView
  holds no posts, and no likes or comments on posts.
- **iOS 18.4** (`958C0DC3-FF20-44F5-9D02-7A170A295DD3`): the Swift tests ran there (snapshots skipped); not walked signed in, which
  iOS 18 can't be (roadmap open items).

## Not verified

- Report on a post, a comment or a quote: left out natively until the native report sheet lands (ios-native-report-sheet).
- Votes, a repost through someone ("Reposted by"), a quote of a quote, an unavailable quoted post, an unknown embed, hidden comments,
  replies and "View more replies", a failed page of comments and the public-only note on a device: the local stack has none of them
  (no open proposals alice may vote on, no reposts, threads or hidden comments); drawn in snapshots.
- Repost on a device: not tapped, to keep the walk's writes to ones the app undoes in place.
- A comment's count moving at once: the header and the card said "0 comments" while alice's just-sent comment was listed, as
  `CommentsState.count` held it; the shared page reads the same count, so this is the presenter's, not the native view's.
- The feed's posts, which are still the shared UI's.
- VoiceOver, the largest text sizes beyond the card's snapshot, an iPad, and a page beside another column.
- The comment field's caption-free layout (`AppTextField` `.bare`) on a device: it came after the walk, which saw the field with
  its caption over it; the snapshots end above the field.
