# Tasks

## 1. Lexicons

- [x] 1.1 Add `lexicons/app/activitytracker/feed/getSegmentMayors.json` (params, eight `period` known values with `last90Days` default, output `entries`/`viewer`/`cursor`, `NotFound`/`InvalidRequest`) and `feed/defs.json#mayorEntry` (`rank`, `athlete`, `efforts`, `reachedOn` as `YYYY-MM-DD`); verify with the lexicon-auditor agent
- [x] 1.2 Mirror them in `core/lexicon` (`Nsid`, `GetSegmentMayors` in `Queries.kt`, `MayorEntry` in `Views.kt`, a `MayorPeriod` constants object) and verify a round-trip test passes (`./gradlew :core-lexicon:jvmTest`)

## 2. AppView

- [x] 2.1 Give `boardOf` an exclusive `until` bound and make `filtersOf`/`bracketsOf` take plain values, with the time board passing what it does today; verify `SegmentLeaderboardsTest` still passes unchanged
- [x] 2.2 Generalize `entries`' rank and position arithmetic over "ahead of" and "listed before" predicates, with the time board using its current ones; verify `SegmentLeaderboardsTest` (ties, paging, viewer entry) still passes
- [x] 2.3 Add `mayorWindow` (the design's window table, from the UTC clock, ISO weeks) and verify unit tests for each window on a mid-week date, a Monday, 1 January and a leap year
- [x] 2.4 Add `SegmentViews.mayors` (count/`MAX(started_on)` grouping, the three-part `HAVING` keyset, `c::d::did` cursor, viewer entry, active segments only, `NotFound`) and its `xrpcQuery` in `segmentRoutes`; verify a new `SegmentMayorsTest` on `SegmentTestBase` covering each spec scenario for counting, ordering, shared ranks, windows, filters, paging and the viewer entry
- [x] 2.5 Add a sentence on Mayors to the leaderboards part of `.agents/rules/appview.md` (the same rankable, viewer-visible efforts, counted; no new data held); verify `scripts/check-agent-sync.sh` passes

## 3. Client data

- [x] 3.1 Add `SegmentRepository.mayorsPage` (like `leaderboardPage`, with `limit`); verify a `SegmentRepositoryTest` case with `MockEngine` checking the params sent and the page returned

## 4. Segment screen

- [x] 4.1 Add the Mayor summary to `SegmentState` and `SegmentEvent.OpenMayors`, loaded by `SegmentPresenter` for active segments only (last 90 days, everyone, limit 1) and left null on failure; verify `SegmentPresenterTest` cases for an active segment, no Mayor yet, a proposed segment and a failed load
- [x] 4.2 Show the Mayor row in `SegmentUi` (leader, their count, the viewer's count, "No Mayor yet") opening the Mayors screen, with strings in resources; verify on the desktop app via Compose Hot Reload (screenshot)

## 5. Mayors screen

- [x] 5.1 Scaffold `SegmentMayorsScreen` with the `new-screen` skill: state (window, scope, e-bike, brackets, `PagedState` of entries, viewer entry), events, and a presenter modelled on `SegmentLeaderboardPresenter`; verify `SegmentMayorsPresenterTest` cases for the default window, choosing a window, the pinned viewer entry, retry and opening a profile
- [x] 5.2 Build `SegmentMayorsUi` from `ui/adaptive` wrappers (window picker with the eight windows, scope, e-bike on ride segments, bracket choices as on the leaderboard screen, rows with rank, athlete, "N efforts" and date); verify on the desktop app via Compose Hot Reload (screenshot) and with `./gradlew composeStabilityCheck`

## 6. Integration

- [x] 6.1 Run `./gradlew check` and verify it passes
- [x] 6.2 With `scripts/dev.sh --seed alice`, open an active segment on the desktop app, check its Mayor and the Mayors screen across windows, and capture screenshots

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed.
