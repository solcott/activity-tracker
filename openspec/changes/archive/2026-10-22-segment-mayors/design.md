# Design

## Context

See proposal.md for why, and `specs/segments/segment-mayors/spec.md` for the behavior. Today:

- `SegmentViews.leaderboard` (`server/appview/.../api/SegmentViews.kt`) builds a board as one predicate, `boardOf(viewer, segment, eBike, since, following)` with `bracketsOf(params)` ANDed in. That predicate is `effortsVisibleTo` + `rankable()` (active segment, track not `leaderboard_excluded`, author not `hide_from_leaderboards`, `plausible()` speed cap) + period start + following scope. It runs over `rankedEfforts()` (efforts joined to activity, segment and segment track).
- `bests(board, having)` groups by `author_did` with `MIN(elapsed_s)`; pages use a `HAVING` keyset on `(best, author_did)`; `entries` computes competition ranks by counting groups ahead (no window function, for H2); the viewer's entry is the same query narrowed to the viewer.
- `periodStart` maps `allTime`/`year`/`month` to a start date from the AppView's UTC clock; `started_on` is a `YYYY-MM-DD` varchar, so date ranges are string comparisons.
- Clients: `SegmentRepository.leaderboardPage` returns `Outcome<Page<Entry, Entry?>>`; `SegmentLeaderboardPresenter` pages it into `PagedState`, and `SegmentScreen` offers `OpenLeaderboard` for active segments.

## Goals / Non-Goals

**Goals:**
- One shared board predicate for time and Mayors boards, so any future rankability, visibility or filter rule applies to both without being written twice.
- Same paging, ranking and viewer-entry shape as the time board, and portable SQL (H2 in PostgreSQL mode).

**Non-Goals:**
- A "You're the Mayor" badge on activities or in `getActivitySegments` (a later change could add a `mayor` flag).
- Notifications when someone loses or takes a title.
- New periods on `getSegmentLeaderboard`.
- Histograms or counts of how many efforts others need, as Strava shows.

## Decisions

### A separate query, not a `metric` param on `getSegmentLeaderboard`
`feed.getSegmentMayors` with its own `defs#mayorEntry { rank, athlete, efforts, reachedOn }`.
- A Mayors entry has a count and a date, not an effort; reusing `leaderboardEntry` would need an optional effort and an optional count, and its docs would describe two boards.
- Its periods differ (eight values, `last90Days` by default) from the time board's (three, `allTime` by default). One `period` param with metric-dependent known values and default would be confusing in the lexicon.
- *Alternative:* `metric: time | efforts` on the existing query. Rejected for the two reasons above.

Params: `segment`, `period` (knownValues `last90Days`, `allTime`, `thisYear`, `lastYear`, `thisMonth`, `lastMonth`, `thisWeek`, `lastWeek`; default `last90Days`), `scope`, `eBike`, `ageGroup`, `weightClass`, `gender`, `limit` (1–100, default 30), `cursor`. Scope and bracket params reuse the leaderboard's known values and Kotlin constants (`LeaderboardScope`, `LeaderboardAgeGroup`, …). A new `MayorPeriod` constants object holds the windows. Errors: `NotFound`, `InvalidRequest`, as for the leaderboard.

### A window with an end, shared with the time board predicate
`boardOf` gains `until: String?` (exclusive, `started_on < until`), passed as null by the time board. A `mayorWindow(period): Pair<String?, String?>` maps each window from the UTC date `today`:

| period | since | until (exclusive) |
|---|---|---|
| `last90Days` | today − 89 days | — |
| `allTime` | — | — |
| `thisYear` / `lastYear` | Jan 1 this / last year | — / Jan 1 this year |
| `thisMonth` / `lastMonth` | 1st this / last month | — / 1st this month |
| `thisWeek` / `lastWeek` | Monday this / last week | — / Monday this week |

"This" windows have no upper bound, so efforts dated in the future (a client's clock ahead of UTC, or an athlete in a time zone ahead) still count, as on the time board.

`filtersOf` and `bracketsOf` take their inputs as plain values instead of `GetSegmentLeaderboard.Params`, so both queries validate scope, following-without-viewer and brackets the same way.

### Aggregate and order
Group `rankedEfforts()` where `board` by `author_did`, selecting `COUNT(segment_efforts.id)` as `EFFORTS` and `MAX(started_on)` as `REACHED`. Order `EFFORTS DESC, REACHED ASC, author_did ASC`.
- With every effort counted, the effort that took an athlete to their count is their latest one in the window, so `MAX(started_on)` is the date it was reached. An earlier date wins the tie ("first to reach it").
- Keyset `HAVING`, after `(c, d, did)`: `EFFORTS < c OR (EFFORTS = c AND REACHED > d) OR (EFFORTS = c AND REACHED = d AND author_did > did)`. Cursor `c::d::did`; dates and DIDs contain no `::`.
- Rank = 1 + groups with `EFFORTS > c OR (EFFORTS = c AND REACHED < d)`. Equal `(count, date)` share a rank; `author_did` orders them stably.
- The rank/position arithmetic in `entries` is generalized over the two predicates ("strictly ahead of", "listed before"), so the time and Mayors boards share it. The time board passes `BEST < b` and `BEST < b OR (BEST = b AND did < x)`.
- *Alternative:* order by `MIN(started_on)` of the Nth effort, or count distinct activities. Rejected in exploration: every effort counts, and the latest effort is the one that reached the count.

### Viewer entry and the segment screen
As on the time board: the board narrowed to the viewer, ranked with the same arithmetic. The segment screen asks for `last90Days`, `everyone`, `limit 1`, standard board, and shows `entries.first()` as Mayor and `viewer` as the viewer's count. A ride segment's screen shows the standard board's Mayor; the e-bike board is on the Mayors screen.

### Index
`segment_efforts_board_idx (segment_uri, e_bike, elapsed_s)` already narrows to one segment's board; the window filter then scans that segment's efforts. Boards are per segment and efforts per segment are modest, so no new index in this change. If a popular segment's board is slow in practice, add `(segment_uri, e_bike, started_on)` in a later migration.

### Client
- `SegmentRepository.mayorsPage(segment, period, scope, eBike, cursor, brackets, limit)`: `Outcome<Page<MayorEntry, MayorEntry?>>`, like `leaderboardPage`.
- `SegmentState` gains `mayor: MayorSummary?` (the leader and the viewer's own entry), loaded only for an active segment after the segment loads. A failed load leaves it null and doesn't fail the screen: the row is extra to the segment. `SegmentEvent.OpenMayors` navigates.
- `SegmentMayorsScreen(uri, name, sport)` with State/Event/Presenter/UI modelled on the leaderboard ones (`PagedState`, filters restart paging, pinned viewer entry). Its window picker lists the eight windows; entries show rank, avatar and handle, "N efforts" and the reached-on date, and open `ProfileScreen`.
- The name "Mayor" lives in string resources only, so it can be renamed without touching the lexicon, whose NSID also says Mayors.

## Risks / Trade-offs

- [Counts show how often someone visits a place] → Only efforts the viewer may already see count, and hiding from leaderboards or excluding an activity removes them. A count reveals no more than listing the efforts would.
- [Different viewers see different Mayors] → Intended, as on time boards: each viewer's board is built from what they may see.
- [Every effort counting rewards laps] → Chosen deliberately. The speed cap still applies. If farming becomes a problem, a later change can count distinct activities.
- [Riding again without passing anyone can lose a tie, especially as old efforts leave a rolling window] → A consequence of "first to reach it" with a moving window. The spec states it (the reached-on date is the latest counted effort), and the screen shows the date so it is visible.
- [Same-day ties are common on group rides] → They share a rank, and the segment screen shows the first listed. No wall-clock time is stored, by design, so there is nothing finer to break the tie with.

## Migration Plan

No data migration and no new tables. Clients that don't know the query never call it. Rolling back removes the route and the screen.
