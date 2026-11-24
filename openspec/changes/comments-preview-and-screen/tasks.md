# Tasks

## 1. Shared count and preview rules

- [x] 1.1 Add the shared count helper to `:app-common-presenter` `interactions/` (a loaded view's count moved by the viewer's `LocalComments` changes on its subject since it loaded, each once) and unit-test sends, deletes, hides and a reload after the AppView caught up; verify with `./gradlew :app-common-presenter:jvmTest`
- [x] 1.2 Add `CommentsPreview` (count, rows, `canComment`, `OpenAll`/`Compose` events) and `CommentsTexts.preview` (first two listed, unhidden, unlabelled top-level comments, else the viewer's just-sent ones) to `:app-common-api`, with `@Immutable` types and tests for hidden, labelled, placeholder, loading and just-sent cases; verify with `./gradlew :app-common-api:jvmTest` and `composeStabilityCheck`
- [x] 1.3 Split `CommentField` out of `LazyListScope.comments` into a public `CommentComposer`, and add `CommentsPreviewBlock` (count link, two-line rows, View all, field-shaped Add a comment button) to `:app-common-ui` from `ui/adaptive` pieces, with new strings in `strings.xml` and `SharedTextNamesTest`; verify with `./gradlew :app-common-ui:jvmTest`

## 2. Comments screen (Compose)

- [x] 2.1 Scaffold `feature/comments/{api,presenter,ui}` with the `new-screen` skill: `CommentsScreen(subject, focus = false)`, `CommentsScreenState` (the subject view's `ContentState`, `CommentsState`, `Reporting`, focus), and join the modules to `composeApp`, `desktopApp`, `webApp`, `:app-graph` and the screenshot suite; verify `./gradlew checkModuleBoundaries` passes
- [x] 2.2 Write `CommentsPresenter`: read the activity or post view by the URI's collection, `rememberComments`, `CommentActions`, the field and replying state, Report, and the shared count; move the comment scenarios of `ActivityDetailCommentsTest`, `ActivityDetailRepliesTest` and the post page's tests here, plus focus-once and double-send cases; verify with `./gradlew :feature-comments-presenter:jvmTest`
- [x] 2.3 Write `CommentsUi`: `AppScaffold` with `AppTopBar` (title and subject subtitle), the list in `ReadableList` with `LoadMoreNearTheEnd`, `CommentComposer` in the bottom bar above the keyboard, focused when `focus` is set or Reply is chosen; add `CommentsSamples` to `:app-fixtures`, a UI test, the `ContentLoadingConformanceTest` entry and `CommentsScreenshots.kt`; verify with `./gradlew :feature-comments-ui:jvmTest :composeApp:jvmTest --tests "*ContentLoadingConformanceTest" --tests "*ScreenshotCoverageTest"`
- [x] 2.4 Add the `/comments` routes under the activity and post paths to `AppLinkRoutes` (chain: subject page, then Comments; `focus` left out) with `LinkSample`s; verify with `./gradlew :app-links:jvmTest` (`LinkRoundTripTest`, `LinkCoverageTest`)

## 3. Activity and post pages

- [x] 3.1 Replace `CommentsState` with `CommentsPreview` in `ActivityDetailPage`; `ActivityDetailPresenter` keeps the first page only, uses the shared count instead of `CommentMoves` (removed), and opens `CommentsScreen` on `OpenAll`/`Compose`; update the remaining detail presenter tests and add one for the count after a send on the Comments screen; verify with `./gradlew :feature-detail-presenter:jvmTest`
- [x] 3.2 Place `CommentsPreviewBlock` after Likes in `ColumnPage` (both widths) and `PanedPage`'s side pane, drop `comments(...)` and `LoadMoreNearTheEnd`, update the detail samples and UI tests; verify with `./gradlew :feature-detail-ui:jvmTest`
- [x] 3.3 Do the same on the post page (`PostPagePresenter`, `PostPageUi`, post samples and tests); verify with `./gradlew :feature-post-presenter:jvmTest :feature-post-ui:jvmTest`
- [x] 3.4 Rerecord and inspect the activity, post and new Comments screenshots; verify with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite` then `:androidApp:testDebugScreenshotTestDefaultTestSuite`
- [x] 3.5 Walk the flow on desktop with Compose Hot Reload at Compact and Expanded widths: count, View all, Add a comment focused, send, back with the count moved; capture screenshots

## 4. iOS

- [x] 4.1 Add `CommentsPreviewSection` and place it after the likes section on `ActivityPage` (both layouts) and after the card on `PostPage`; drop the field from `CommentsSection`; verify the iOS build (`xcodebuild … build`)
- [x] 4.2 Add `CommentsView` (native list, `CommentsSection`, composer in `safeAreaInset(edge: .bottom)` focused with `@FocusState` when `focus` is set or Reply is chosen), alias its types in `KotlinTypes.swift` and register it in `ScreenRegistry`; verify `./gradlew checkIosScreenRegistry` and the iOS build
- [x] 4.3 Add previews and a `CommentsSnapshotTests` suite, update the activity and post page snapshots, and check them on an iOS 26.0 simulator in light and dark (`xcodebuild test`)
- [x] 4.4 Run the app in the simulator: Add a comment opens the focused field above the keyboard, send, back with the count moved, on iPhone and iPad

## 5. Integration

- [x] 5.1 Update `CLAUDE.md`/`compose-screens.md` where they name the activity page's comments or `CommentsSection`'s field, then run `./gradlew check` and confirm it passes

## Workflow follow-up

- Archive the change once it lands.
