# Tasks

## 1. Lexicons

- [x] 1.1 Add `lexicons/app/activitytracker/actor/leaderboardBrackets.json` (rkey `self`, optional `ageGroup`/`weightClass`/`gender` open enums with the spec's known values, `updatedAt`) and list it in `space/athlete.json`'s collections and description; verify with the lexicon-auditor agent
- [x] 1.2 Add `ageGroup`, `weightClass`, `gender` params to `feed.getSegmentLeaderboard.json` (with `InvalidRequest` covering unknown values)
- [x] 1.3 Mirror both in `core/lexicon` (`LeaderboardBracketsRecord`, `Nsid.ACTOR_LEADERBOARD_BRACKETS`, the bracket constants objects, `GetSegmentLeaderboard.Params`) and verify a round-trip test in `SegmentRecordsTest` passes (`./gradlew :core-lexicon:jvmTest`)

## 2. AppView

- [x] 2.1 Add Flyway `V34__leaderboard_brackets.sql` and the `LeaderboardBrackets` table in `Tables.kt`; verify the migration runs in `:server-appview:test`
- [x] 2.2 Index the record only from `self` (upsert with unknown values as NULL, delete on record delete, `dropSpace(self)` and account deletion); verify with AppView tests for each, including a repo copy being ignored
- [x] 2.3 Add the three filters to `SegmentViews.leaderboard` (`InvalidRequest` on unknown values, filtered ranks, viewer entry only when in the bracket) and the route params; verify AppView tests for each `segment-leaderboards` filter scenario pass
- [x] 2.4 Update `.agents/rules/appview.md` and AGENTS.md's Architecture paragraph to list the brackets among the private data the AppView holds; verify `scripts/check-agent-sync.sh` passes

## 3. Client data

- [x] 3.1 Add pure `LeaderboardBrackets` computation (age by whole years on the athlete's date, weight by floored kg, known gender values only); verify unit tests for the boundary, birthday and missing-detail scenarios
- [x] 3.2 Add `LeaderboardBracketsRepository` (read once per session, `save(on)` writing or deleting the record as a direct call, `refresh()` rewriting only on change, gated by `canWriteToSpaces`); verify jvm tests with the existing space test fixtures
- [x] 3.3 Run `refresh()` once a session (`refreshOnSave`, from `App`) and after a profile save that changes birth date, weight or gender, never failing the save; verify tests for "Turning 45", "Weight edit" and "Nothing changed"
- [x] 3.4 Document the brackets record in `.agents/rules/data-and-auth.md` beside segment tracks

## 4. Settings

- [x] 4.1 Add the brackets toggle to Settings (state, event, presenter following `SegmentMatchingSetting`, UI with the disclosure text), shown only while matching is on and the session can write it; verify `SettingsPresenterTest` cases for on, write failure, matching off and old session

## 5. Leaderboard screen

- [x] 5.1 Add `ageGroup`, `weightClass`, `gender` to `SegmentLeaderboardState` with change events that restart paging, passed to `SegmentRepository`; verify presenter tests for choosing an age group and combined filters
- [x] 5.2 Add the three filter choices to `SegmentLeaderboardUi`, weight classes labelled in the viewer's units; verify on the desktop app via Compose Hot Reload (screenshot) and with `./gradlew composeStabilityCheck`

## 6. Wrap-up

- [x] 6.1 Remove BACKLOG.md's "AppView as a consented member for aggregation" section (now in this design) and verify no other doc links to it
- [x] 6.2 Run `./gradlew check` and verify it passes
