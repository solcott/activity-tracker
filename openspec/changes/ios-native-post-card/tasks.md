# Tasks

## 1. Probe (throwaway code, not merged)

- [ ] 1.1 Read the generated Swift for `PostView`, `PostEmbedView`, `QuotedPost`, `CommentsState`, `PostPageState` and `QuoteFields`: which members reach Swift typed, and what needs a getter. Verify by noting each in the roadmap's probes.
- [ ] 1.2 Write the results into `openspec/explorations/ios-native-ui.md` under "Phase 4 probes (posts)". Verify it records each result.

## 2. Kotlin rules (no visible change)

- [ ] 2.1 `PostCardContent` (decision 1) and `QuoteSheetTexts` (decision 2) in `:app-common-api`; `PostCard` and `QuoteSheetHost` ask them. Verify a `commonTest` over every branch and `:app-common-ui:jvmTest`.
- [ ] 2.2 `CommentAction`, `CommentsTexts`, `CommentRow` in `:app-common-api` (decision 3); `CommentsUi` and `CommentMenu` ask them. Verify a `commonTest` and the comment UI tests.
- [ ] 2.3 `PostPageTexts`, `PostPageState.page` and the `PostPage` getters (decision 4); `ProfileTexts.tab` and `postsEmpty`; the Compose pages ask them. Verify `:feature-post-ui:jvmTest` and `:feature-profile-ui:jvmTest`.
- [ ] 2.4 The samples (decision 7). Verify `ContentLoadingConformanceTest` and the Android screenshot tests pass with no reference changed.

## 3. The native views

- [ ] 3.1 Type aliases (one appended block) and samples (appended). Verify the text tests read every sample.
- [ ] 3.2 `PostCardView`, `.quoteSheet`, `CommentsSection` (decision 5). Verify their previews and snapshot suites.
- [ ] 3.3 `PostPage` and `PostQuotesView`, registered (appended entries). Verify `ScreenRegistryTests` names both.
- [ ] 3.4 The profile's Posts tab. Verify the profile snapshots of `posts`, `noPosts` and `postsFailed`.
- [ ] 3.5 Snapshot tests record on a first run and pass on a second on an iOS 26.0 simulator. Verify each image was looked at, light and dark.

## 4. The whole change together

- [ ] 4.1 `./gradlew format`, `./gradlew check` and `./gradlew -p build-logic :convention:test`. Verify all pass, with no Android screenshot reference changed.
- [ ] 4.2 The Swift tests on `D6328F65-DD4F-4359-892A-31D995B1C1F9` (iOS 26.0) and `958C0DC3-FF20-44F5-9D02-7A170A295DD3` (iOS 18.4). Verify they pass, snapshots compared on 26.0 and skipped on 18.4.
- [ ] 4.3 The Release configuration for the simulator. Verify it builds and passes the stub check.

## 5. See it

- [ ] 5.1 On the signed-in iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`, as alice on the local stack: her profile's Posts tab, a post page (card, likes, comments), its quotes, and the quote sheet opened and cancelled; any write undone. Verify by noting what was seen; record what needs data the stack lacks under "Not verified".

## 6. Docs

- [ ] 6.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (posts)", the open items (the profile's Posts closed; Report on posts and comments; the feed and activity page). Verify `openspec validate ios-native-post-card --strict` passes.
- [ ] 6.2 `.agents/rules/compose-screens.md` if a rule changed. Verify `scripts/check-agent-sync.sh` and `scripts/check-public-safety.sh` pass.
- [ ] 6.3 A "Not verified" section listing what wasn't seen and why.
