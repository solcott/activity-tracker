# Proposal

## Why

The native posts on iOS (`ios-native-post-card`) left Report out of the post card's menu, the post page's comments and the quotes list,
because the report sheet couldn't yet be shown from a native page. The native report sheet (`ios-native-report-sheet`) is that way in
now: a page that carries `Reporting` adds `.reporting(_:)`, and its menu sends a `ReportingEvent.Open` that Kotlin builds. Both are on
the migration branch, so the native posts can offer Report wherever the shared UI does.

## What Changes

- The native post card offers Report in its menu on someone else's post when a labeler is configured, as the shared card does, sending
  the shared `PostEvent.Report`, which the presenters already turn into the sheet for the post. The viewer's own quote still offers
  Delete. The card's existing initialiser is kept; Report is an added parameter that defaults to off.
- The native post page and quotes list pass their `Reporting`'s availability to their cards and show the native report sheet
  (`reporting(_:)`); the profile's Posts tab passes the profile's, whose page already shows the sheet.
- The native comment list offers Report on others' comments (`CommentsTexts.rows` already decides it), opening the sheet for the
  comment. Kotlin builds the comment's `ReportingEvent.Open` (`Reporting.reportComment`), which the Compose comment list asks too, so
  Swift builds no `ReportTarget`.
- The `moderation/reports` requirement that native posts and comments wait for the report sheet is removed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `moderation/reports`: native posts, comments and quotes on iOS offer Report; the waiting requirement goes.
- `dev/shared-display-text`: what a comment's Report opens is decided in shared code.

## Impact

- `:feature-report-api` (`Reporting.reportComment`), `:app-common-ui` (`CommentsUi` asks it).
- `feature/post/presenter` JVM tests: a post's and a comment's Report open the sheet for the right target.
- `:app-fixtures`: a `reportable` post page variant, outside the shared screenshots' `all`.
- `iosApp`: `Screens/PostCardView.swift`, `Screens/PostPage.swift`, `Screens/CommentsSection.swift`, `Screens/ProfilePosts.swift`,
  snapshot and text tests of the menus.
- The roadmap `openspec/explorations/ios-native-ui.md`.
