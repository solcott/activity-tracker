# Design

## Context

`PostCardContent` (`:app-common-api`) already decides the card's menu with Report: `offersReport(reportAvailable)`,
`offersMenu(reportAvailable)`, `menuItem()` (Delete quote or Report) and `menuEvent()` (`PostEvent.Delete` or `PostEvent.Report`). The
shared post event sink (`rememberPostEventSink`) turns `PostEvent.Report` into `ReportingEvent.Open(ReportTarget.Post…)` on the host's
`Reporting`, so the card needs nothing Swift can't send. `PostPage.reporting` and `PostQuotesState.reporting` carry it.

Comments differ: `CommentMenuItem.event()` is null for Report, and the Compose list builds the comment's `ReportingEvent.Open` itself,
in UI code, from the `CommentView`. `CommentsTexts` (`:app-common-api`) can't build it: `Reporting` is `:feature-report-api`'s.

`PostCardView` is placed as it is by the native feed (another session), so its initialiser must stay.

## Goals / Non-Goals

**Goals:** Report on the native card, comments and quotes wherever the shared UI offers it, opening the native sheet for the right
target; no `ReportTarget` built in Swift.

**Non-Goals:** the feed's posts (the other session's), the activity page's comments (still the shared UI's), changing the sheet.

## Decisions

1. **The card takes `canReport`, defaulting to false.** `PostCardView(content:send:openQuotes:canReport:)`: a caller that says nothing
   keeps today's card. The menu shows when `offersMenu(canReport)` and its one item is `menuItem()` sending `menuEvent()`, destructive
   only for Delete. *Alternative:* reading `Reporting` from the environment. Hidden coupling, and the feed carries its own. Rejected.
2. **`Reporting.reportComment(comment, authorName)` builds a comment's Open.** A member of `Reporting` in `:feature-report-api`, which
   Swift reaches through the page's state, as `ProfileState.reportEvent` is reached. The Compose list asks it too. The author's name is
   the one the Compose list passes (display name, else `@handle`), resolved by each UI.
3. **`CommentsSection` takes an optional `Reporting`.** `CommentsSection(state:reporting:)` with `reporting` defaulting to nil, so a
   caller without one (the activity page, later) offers no Report. With one, `rows` is asked with `reporting.available`, and a Report
   item sends `reportComment` to it. The page that places the list also adds `.reporting(_:)`.

## Risks / Trade-offs

- [The feed places cards without `canReport`] → no Report there until the feed passes it; the feed's own change decides.
