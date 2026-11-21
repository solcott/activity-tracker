# Design

## Context

See proposal.md for the motivation; the design canvas is [Trail & Segment pages](https://claude.ai/artifact/3fh7CDY6oiG6qbripg1cVy), artboards 2–4.

Current state:
- **Compose Segment page:** `SegmentUi` is one `LazyColumn`. It holds the map, the group chips, one item holding everything from the summary to the Mayor card, then Trail details, names and efforts.
- **iOS Segment page:** `SegmentPage.swift` draws the same parts as sections of a `List`.
- **Board reads:** `SegmentPresenter` already reads the Mayors board at one entry (`mayorsPage(..., limit = 1)`) and keeps it as `MayorSummary`. There is no matching read of the leaderboard. `SegmentRepository.leaderboardPage` has no `limit`, although `GetSegmentLeaderboard.Params` and the lexicon do.
- **Trails a segment lies on:** `SegmentView.paths` lists their URIs. `SegmentRepository.pathViews(uris)` (`app.activitytracker.feed.getPaths`) already reads their views for area browse.
- **Trail details:** `TrailTexts.rows(trail, sport)` gives a flat `List<TrailDetail>` that both UIs draw: `TrailDetails` in Compose and `TrailDetailsSection.swift` on iOS. The Trail page draws it in `PathList`'s header item. On iOS it is a section of `PathPage`.

## Goals / Non-Goals

**Goals:**
- Every choice of what shows, in what order and with what words is made in Kotlin, in `feature/segment/api`. That covers the figure row, the trail-details groups, the folded summary, the heading line and the board cards. Compose and SwiftUI only lay them out.
- The changed parts get new samples, so the screenshot and snapshot suites show the new layout.

**Non-Goals:**
- **No overlay on the map:** the bar stays the platform's own. The `segments/segment-efforts` requirement for iOS (system bar, title, Star in the bar) stands, and an overlay would need its own scroll-under behavior on three platforms.
- **No new reads beyond the two below:** none for the boards screens, and no change to their layouts.
- **No layout by width:** Segment and Trail page widths behave as today (`ReadableList`, `PathUi`'s list/map split).

## Decisions

### Shared texts and shapes in `-api`
- **`SegmentPageTexts.figures(segment, profile, units): List<SegmentFigure>`**: each `SegmentFigure` has a value and a label as `AppText`, and an optional secondary line (the loss under the gain). It leaves out what's unknown, which keeps the "a figure it lacks is left out" rule in one place. This replaces `summary`, `elevation` and `extremes` on the page. `extremes` keeps only the high and low points, for the profile card's heading.
- **`SegmentPageTexts.heading(segment): AppText`**: "Ride segment · Active", or the lift wording `summary` gives today.
- **`TrailTexts.groups(trail, sport): List<TrailDetailGroup>`**: a group's title is `AppText` and its rows are the `TrailDetail`s `rows` makes today, partitioned by a fixed attribute → group table. `rows` stays as the flat order, so the words, filters and `TrailTextsTest` cases are unchanged. An empty group is dropped.
- **`TrailTexts.summary(trail): AppText?`**: up to three of surface, width, dogs and season, joined with " · ". It reuses the existing `surface`, `width` and `season` functions.
- **Lists for Swift:** each list getter returns a copy (`toList()`), never the immutable library's shared empty instance (compose-screens.md).

*Alternative:* the grouping lives in each UI. Rejected, because the two UIs would drift. Rule: dev/shared-display-text.

### Leaderboard card data
- **`LeaderboardSummary(leader: LeaderboardEntry?, viewer: LeaderboardEntry?)`**: lives in `SegmentScreen.kt`, beside `MayorSummary`.
- **The read:** `SegmentPresenter` reads it the way it reads the Mayor: in a `LaunchedEffect(active, shown, loadKey)`, with `leaderboardPage(uri, ALL_TIME, EVERYONE, screen.board, limit = 1)`. A failure leaves the last one shown, or `null`.
- **Limit:** `leaderboardPage` gains `limit: Int? = null`, passed through to `Params.limit`.
- **State:** `SegmentState.leaderboard: LeaderboardSummary?`. `offersLeaderboard` stays the rule for whether the card shows, so the card shows even when the summary is `null` (spec: "Board fails to load").

*Alternative:* serve the leader in `getSegment`. Rejected for now, because it is an AppView change. This change stays client-only, and one extra small read matches the Mayor's.

### Trail links in the heading
- **The read:** `SegmentPresenter` reads `pathViews(segment.paths)` once the segment has loaded, keyed on the URIs. It keeps `SegmentState.trails: ImmutableList<SegmentTrail>` (URI and name). A failure leaves the list empty, so the "on" line is left out.
- **Opening a trail:** `SegmentEvent.OpenTrail(uri)` pushes `PathScreen(uri, sport = segment.record.sport)`, the screen and link route that already exist, so no `:app-links` change.

### Trail page folding
- **State and event:** `PathState.detailsExpanded: Boolean`, plus `PathEvent.ToggleDetails`. The presenter keeps the flag with `rememberRetained`, not saved, so a new visit opens folded as the spec says. Coming back from a segment keeps it, as a retained state does.
- **Compose:** the header item shows the summary row with a chevron control, and `TrailDetailGroups` under it when expanded.
- **iOS:** `PathPage` shows a row in the header section and the group cards after it, behind `if state.detailsExpanded`.

### Cards on each platform
- **Compose:** groups, the figure row and the board cards use `AppCard` (`ui/adaptive`). The figure row is a `Row` of weighted cells inside one card, and the boards row a `FlowRow` of two weighted cards.
- **iOS:** the figure row is one `List` row holding an `HStack` of cells, and the board cards are `LeaderCardView`s (`RankBoards.swift`) side by side in one row. Each trail-details group is a `Section` with the group's title as its header: the system's grouped list already draws sections as cards, so there's no custom card.

## Risks / Trade-offs

- **[One more read per segment open (the leaderboard)]** → Limited to one entry and skipped for non-active segments and lifts, like the Mayor read.
- **[`getPaths` for the heading]** → It is a batched read the app already uses for browse. It is skipped when `paths` is empty (athletes' segments, lifts).
- **[About 900 Android screenshot references and the iOS segment snapshots change]** → Regenerate them in the change's last tasks, and look at the Segment and Trail references before committing.
- **[iOS `List` sections and Compose cards look different]** → Intended: each platform uses its own grouped look (adaptive-ui.md), and only the order and words are shared.
