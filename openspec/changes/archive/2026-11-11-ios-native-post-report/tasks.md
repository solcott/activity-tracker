# Tasks

## 1. Kotlin

- [x] 1.1 `Reporting.reportComment(comment, authorName)` in `:feature-report-api`; `CommentsUi` asks it. Verify a `commonTest` of the target and name, and the comment UI tests.
- [x] 1.2 `PostPagePresenterTest`: the card's Report (`menuEvent()` on someone else's post) opens the sheet for the post, and a comment's `reportComment` opens it for the comment. Verify `:feature-post-presenter:jvmTest`.

## 2. Swift

- [x] 2.1 `PostCardView` gains `canReport` (default false) and shows `menuItem()` when `offersMenu(canReport)`; `PostPage` and `PostQuotesView` pass their `Reporting`'s availability and add `.reporting(_:)`; the profile's Posts tab (`ProfilePosts`) passes the profile's.
- [x] 2.2 `CommentsSection(state:reporting:)` offers Report from `rows(reportAvailable:)` and sends `reportComment`; `PostPage` passes its `Reporting`.
- [x] 2.3 Snapshot tests of the card's menu with Report and of a comment's menu with Report, recorded and looked at on an iOS 26.0 simulator.

## 3. The whole change together

- [x] 3.1 `./gradlew format` and `./gradlew check` pass, with no Android screenshot reference changed.
- [x] 3.2 Swift tests pass on `D6328F65-DD4F-4359-892A-31D995B1C1F9` (iOS 26.0) and `958C0DC3-FF20-44F5-9D02-7A170A295DD3` (iOS 18.4).
- [x] 3.3 The Release configuration for the simulator builds and passes the stub check.

## 4. See it

- [x] 4.1 On the signed-in iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`: Report on someone else's post and comment opens the native sheet; Send only to the local stack's labeler.

## 5. Docs

- [x] 5.1 The roadmap: a Phases item 4 bullet, "Phase 4 findings (post report)", the open items. Verify `openspec validate ios-native-post-report --strict`.
- [x] 5.2 A "Not verified" section.

## Found on the walk (4.1)

- **The profile's Posts tab offered no Report.** The shared profile passes its `Reporting`'s availability to its post cards; the
  native tab didn't. `ProfilePosts` passes `canReport: state.reporting.available` now (the page already had `reporting(_:)`).

## Results (3)

- `./gradlew check` passed on a second run. The first failed once in `PostPagePresenterTest.repostingAFollowersPost…` (an existing
  test that reads the outbox right after the optimistic state), under the load of a simulator walk beside it; it passed alone and in
  the rerun. No Android screenshot reference changed (the `reportable` sample is outside `all`).
- Swift tests: 330 in 57 suites on `D6328F65-DD4F-4359-892A-31D995B1C1F9` (iOS 26.0), snapshots compared once recorded and looked at,
  light and dark: the new `reportablePostCard` and `postPage` `reportable`, and the quotes list's `loaded`, `loadingMore`, `end`,
  `pageFailed`, `refreshing` and `refreshFailed`, re-recorded because others' quotes now show their menu (their samples have a
  labeler). On `958C0DC3-FF20-44F5-9D02-7A170A295DD3` (iOS 18.4), snapshots skipped.
- The Release simulator build succeeded and passed the stub check.

## Walk (iPhone 17 Pro `E426A46D-6243-41B7-8FB3-1A6CF2AFB295`, iOS 26.0, signed in as alice on the local stack; this change's Debug build)

The local stack had no posts, so one was made: a public post by rider1.test sharing rider1's adopted "Wiggly Woods" proposal
(rkey `3mzq1`, the proposal's own, which the indexer requires) and a public comment by rider1 on it, written to the local PDS with
the seed scripts' test password.

- Search, rider1's profile, Posts: the native card. Its page: the card's menu (…) and the comment's menu both showed. The card's
  menu offered only Report; Report opened the native sheet "Report rider1"; Spam, Send; the thanks offered Block rider1, and Done
  closed the sheet without blocking.
- The comment's menu offered Report comment (with Mute author and Block author); it opened the sheet afresh (no reason, Send off);
  Spam, Send, Done.
- Both reports reached the local stack's Ozone (`createReport` 200; the build's labeler DID is the one `scripts/dev.sh status`
  shows), and Ozone's `moderation_event` holds a report for `…/feed.post/3mzq1` and one for `…/feed.comment/3walkreport2`.
- **Put back:** rider1's post and comment were deleted from the PDS; the AppView holds no posts and only the comment it held
  before. alice mutes and blocks no one. The two reports stay in the local Ozone.

## Not verified

- **The profile's Posts tab's Report on a device**: fixed after the walk (above); covered by the card's snapshot and text tests, not
  seen in the app.
- **Report in the quotes list on a device**: the walk's post had no quotes; drawn in the quotes snapshots.
- **Taking the Block offer** after a post's or comment's report: not taken, to keep rider1 unblocked; `ReportThroughHostTest` covers
  the offer reaching the carrier.
- **A reply's Report**, and Report on a hidden comment: the stack had no threads; the text test reads every row's menu.
- **The feed's posts**, which are still the shared UI's (the native feed is another session's).
- **iOS 18 signed in** and an iPad: the Swift tests ran on iOS 18.4; the signed-in iPad belongs to another session.
