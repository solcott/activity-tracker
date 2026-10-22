# Tasks

## 1. Lexicons

- [x] 1.1 Add `SegmentBoard` to `core/lexicon` beside `SegmentSport` (`of(sport, subSport)`, `E_BIKE`, `all`) and verify unit tests for a road ride, an e-mountain-bike ride, a handcycle, a backcountry ski, a walk and an indoor ride (none) pass (`./gradlew :core-lexicon:jvmTest`)
- [x] 1.2 In `feed/getSegmentLeaderboard.json` and `feed/getSegmentMayors.json`, replace `eBike` with `board` (known values from `SegmentBoard.all`) and add `board` and `boards` to both outputs; mirror them in `Queries.kt`; verify the round-trip tests in `SegmentRecordsTest` and the lexicon-auditor agent

## 2. AppView

- [x] 2.1 Give `boardOf` a `board` type in place of `eBike` (the e-bike flag for `eBike`, else no e-bike and the activity's sport) and validate the token with `InvalidRequest`; build `topRank`'s board from the effort's own type; verify updated `SegmentLeaderboardsTest` cases for the `eBike` board, a handcycle ranked among handcycles, an unknown token, and the activity page's rank on a type board
- [x] 2.2 Add `boardsOf` (types with visible, rankable efforts over all time, most first, ties by token), serve the requested type or the first listed, append a requested type that isn't listed, and return `board` and `boards` from both `leaderboard` and `mayors`; verify AppView tests for the default type, the list ignoring period, scope and brackets, a requested type with no efforts, and an empty segment
- [x] 2.3 Move the e-bike and filter cases in `SegmentMayorsTest` to `board`, and verify `./gradlew :server-appview:test` passes
- [x] 2.4 Update the leaderboards and Mayors lines of `.agents/rules/appview.md` to boards per activity type; verify `scripts/check-agent-sync.sh` passes

## 3. Client data

- [x] 3.1 Return `Page<Entry, BoardMeta<Entry>>` (viewer entry, served board, listed boards) from `leaderboardPage` and `mayorsPage`, taking `board: String?` in place of `eBike`; verify `SegmentRepositoryTest` checks the params sent and the meta returned

## 4. Carrying the type

- [x] 4.1 Add `board: String?` to `SegmentScreen`, set by `ActivityDetailPresenter` from the opened effort and the activity's effective record; verify an `ActivityDetailPresenterTest` case for a canoe trip and an e-bike ride
- [x] 4.2 Pass `screen.board` to the Mayor card's read and to the leaderboard and Mayors screens in `SegmentPresenter`, replacing their `sport` argument with `board`; verify `SegmentPresenterTest` cases for a carried type and for none

## 5. Board screens

- [x] 5.1 Replace `eBike`/`canChooseEBike`/`EBikeChanged` in both board states and presenters with a requested `board` in the paging key, the served `board` and `boards` from the page meta, and `BoardChanged` (ignored for the type already served or an unlisted one); verify presenter tests for opening on the carried type, the default, switching type from the first page, and one listed type
- [x] 5.2 Replace the e-bike chip in `SegmentLeaderboardUi` and `SegmentMayorsUi` with a shared type control (a chip and menu for two or more types, else a label; `sportLabel`, and a new "E-bike" string for `eBike`); verify on the desktop app via Compose Hot Reload (screenshot of a ride segment's menu) and `./gradlew composeStabilityCheck`

## 6. Integration

- [x] 6.1 Run `./gradlew check` and verify it passes
- [x] 6.2 With the local stack, open a ride segment from an activity and from its Mayor card, switch types on both boards, and capture screenshots

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and reviewed.
