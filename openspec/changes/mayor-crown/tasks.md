# Tasks

## 1. Effort start instants

- [x] 1.1 Add a Flyway migration (`V76__segment_effort_started_at.sql`):
  - Add `segment_efforts.started_at BIGINT`.
  - Backfill it from `activities.started_at + start_offset_s * 1000`, with a correlated `UPDATE` that runs on both H2 and Postgres. The column is `NOT NULL DEFAULT 0`, so a row with no activity keeps its offset alone.
- [x] 1.2 Add `startedAt` to `SegmentEfforts` (`db/Tables.kt`), documented as never served, and write it in `SegmentMatcher.insertEfforts` from the activity's start plus the offset.
- [x] 1.3 Add a matcher test that an effort's `started_at` is its activity's start plus its offset, and check the backfill on the dev database, where the migration ran over efforts written before it (all 85 filled from their activity).

## 2. One Mayor per board

- [x] 2.1 Give `CountBoard` optional extra order keys:
  - the keyset, ordering and cursor carry them (`"<count>::<date>::<keys…>::<did>"`), and a cursor with the wrong number of parts reads from the start;
  - when the keys are set, ranks are positions (`ahead + 1`) and never shared;
  - without them it behaves as now.
- [x] 2.2 Pass `MAX(started_at)` ascending and `MIN(started_at)` ascending to `mayorBoard` (`SegmentViews.kt`), and update its KDoc and the `mayors` KDoc. Leave `regularsBoard` without keys.
- [x] 2.3 Add tests to `SegmentMayorsTest`:
  - equal counts on one day rank by the latest effort's start;
  - equal latest starts rank by the first effort;
  - no rank is shared, across a page boundary too;
  - the viewer's own entry has its unshared rank;
  - an old three-part cursor reads page one;
  - no entry carries a time.
  Confirm the Regulars tests still pass unchanged.
- [x] 2.4 Update `.agents/rules/appview.md` where it describes the count boards' order and ranks.

## 3. Crown rule and texts

- [x] 3.1 Add strings `mayors_mayor` ("Mayor") and `mayors_reached_mayor` ("Mayor · Reached %1$s") to `strings.xml`.
- [x] 3.2 Add `SegmentMayorsState.crowned(entry)` (rank 1), and make `SegmentBoardTexts.reached(entry)` give `mayors_reached_mayor` for a crowned entry.
- [x] 3.3 Test both in `SegmentBoardTextsTest` and the states test, and add the new names to `SegmentBoardTextNamesTest`.
- [x] 3.4 Add `AppIcon.Crown`:
  - a filled crown on Material's 24-unit grid, in `MaterialIcons`;
  - `crown.fill` for iOS.
  Run `generateIosResources` for the icon and the strings.

## 4. Compose row

- [x] 4.1 Give `RankRow` (`:app-common-ui`) a `crowned` parameter. It draws the crown medallion in the rank cell's fixed width, with `tertiaryContainer` and `onTertiaryContainer`, the content description "Mayor", and no rank text.
- [x] 4.2 Pass `crowned = state.crowned(entry)` from `SegmentMayorsUi`'s rows. A pinned own row can't be crowned, since the Mayor is always on page one.
- [x] 4.3 Add a `SegmentBoardsUiTest` test:
  - the first Mayors row has a node described "Mayor" and the text "Mayor · Reached …";
  - the second row shows "2";
  - the leaderboard's first row isn't crowned.
- [x] 4.4 Make sure the Mayors samples (`SegmentSamples`) have a first entry, then re-record the Android Mayors screenshot references and look at them in light, dark and large text.

## 5. iOS row

- [x] 5.1 Give `RankRow` (`RankBoards.swift`) `crowned:`. It draws `Image(AppIcon.crown)` in a `LikeColor`-tinted circle in the rank column's width, with the accessibility label "Mayor". Pass it from `SegmentMayorsView`.
- [x] 5.2 Update `SegmentListsTextTests` for the Mayor's line.
- [x] 5.3 Re-record the `mayors*` snapshots on the iOS 26 simulator (record run, then a compare run), and look at the crowned row in light and dark.

## 6. Verify

- [x] 6.1 Run `./gradlew format`, then `./gradlew check`.
- [x] 6.2 Against the dev stack, give two seeded riders equal counts on one day. Confirm the earlier one is the only one crowned, on Android and on the iOS 26 simulator, and that the segment page's Mayor card names the same athlete.
- [x] 6.3 Run `openspec validate mayor-crown`.

## Workflow follow-up

- Archive `segment-board-restyle` before archiving this change. Both modify `segments/segment-mayors`.
