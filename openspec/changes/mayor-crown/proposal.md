# Proposal

## Why

A segment has one Mayor, but its Mayors board can put two athletes at 1st. That happens when their counts are equal and were reached on the same day. The board also marks its first place with a plain "1", like any leaderboard. The title should be decided exactly, by who reached the count first, and the board should show it as a title rather than a number.

## What Changes

- **The Mayors board breaks ties by time, not by day.**
  - Among equal counts, the athlete whose latest counted effort started earlier ranks first.
  - If those times are equal, the earlier first counted effort in the window wins, then the athlete's DID.
  - Every place on a Mayors board belongs to one athlete. The board no longer shares ranks, so it always has exactly one Mayor.
  - No time of day is served: entries still give only the date the count was reached.
- **The AppView stores when each effort started.** `segment_efforts` gets a `started_at` column (epoch milliseconds). The matcher writes it, and a migration fills it for efforts already stored. The Mayors board orders and pages by it.
  - **BREAKING (cursor only):** a Mayors cursor issued before this change is malformed afterwards, so it reads the board from its first page.
- **Dive site Regulars keeps its rules.** Equal counts reached on one date still share a rank there.
- **The Mayor is crowned on the Mayors screen.** The 1st entry's rank becomes an amber crown medallion, the same width as the rank column, and screen readers read it as "Mayor". Its line reads "Mayor · Reached" with the day. Every other place keeps its plain number, as now.
  - This works the same on Compose (Android, desktop, web) and iOS.
  - The rule for when a row is crowned, and what its line says, is chosen once in Kotlin.
  - The crown is a new `AppIcon`: a filled crown drawn in `MaterialIcons` on Material's grid, and SF Symbols' `crown.fill`.
- **The segment screen's Mayor card is unchanged.** Its "shared Mayor" case goes away.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `segments/segment-mayors`:
  - Ties are broken by the time the count was reached, then by the first counted effort, then by athlete.
  - Ranks are never shared, so there is one Mayor.
  - The segment screen's shared-Mayor case goes away.
  - The Mayors screen crowns its Mayor on every platform, including the native iOS screen.

## Impact

- **AppView:**
  - A Flyway migration adds `segment_efforts.started_at`.
  - `SegmentEfforts` (`db/Tables.kt`) and `SegmentMatcher.insertEfforts` write it.
  - `CountBoard` (`api/CountBoards.kt`) learns finer tie-breaks and unshared ranks, which the Mayors board uses and Regulars doesn't.
  - Tests: `SegmentMayorsTest` and the count-board tests.
- **Shared Kotlin:**
  - `SegmentBoardTexts` gains the Mayor's detail line, and `SegmentMayorsState` decides which row is crowned.
  - `AppIcon.Crown` is added, with a `MaterialIcons` crown vector.
  - New strings: the "Mayor" label and the line reading "Mayor · Reached …".
  - The iOS catalog is regenerated.
- **UIs:**
  - `RankRow` (`:app-common-ui`) and `RankRow` (`RankBoards.swift`) take a crowned rank, and `SegmentMayorsUi` and `SegmentMayorsView` pass it.
  - The Android Mayors screenshot references and the iOS `mayors*` snapshots are re-recorded.
- **No lexicon change.** `mayorEntry.rank` stays an integer, now unique per board.
