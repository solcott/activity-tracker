# Proposal

## Why

The segment Leaderboard and Mayors screens are the last pieces of the [Trail & Segment pages](https://claude.ai/artifact/3fh7CDY6oiG6qbripg1cVy) canvas (artboard 8) that are still as first built.

- Their rows don't line up: times and counts sit in proportional figures at body weight.
- The viewer's own place sits after the loaded rows, so on a long board it's a scroll away.
- Nothing says how far the viewer is from the top.
- The Leaderboard stacks two segmented controls above its menus, while Mayors mixes one segmented control with menus.

So the question riders open a board to answer, "where am I, and what would it take?", isn't answered at a glance.

## What Changes

- **One row anatomy on every ranked board, on Compose and iOS:**
  - A fixed-width rank, the avatar, then the name with the date under it.
  - The time or count at the trailing edge in tabular figures, heavier than the name, so values line up down the list.
  - The accented ranks stay as they are: the first three on a leaderboard, the first on Mayors and Regulars.
  - iOS keeps its chevron; Android, desktop and web show none.
  - The viewer's own row, when it's among those loaded, is marked as theirs.
  - The rows belong to the Leaderboard, Mayors and dive-site Regulars boards.
- **Your place is pinned:**
  - While the viewer is on the board but not among the loaded rows, their row shows in a bar fixed to the bottom of the screen, under "Your place", instead of after the list.
  - The list leaves room so the bar never covers its last row.
- **The gap to the top**, a line in that bar:
  - Leaderboard: "51 s behind 1st" (under a minute), or "1:05 behind 1st".
  - Mayors: "8 more efforts to be Mayor". This is the leader's count less the viewer's, plus one, since ties go to whoever reached the count first.
  - The gap is measured to the first entry of the board as filtered.
  - It's left out when the first entry hasn't loaded or the viewer is first.
  - Regulars pins the bar but gets no gap line.
- **Filters:**
  - The Leaderboard keeps the period as its only segmented control. Scope (Everyone, Following) becomes a menu chip beside the type, and the age, weight and gender brackets fold into one Filters chip ("Filters · 2" while two narrow the board).
  - The type shows only when there are two or more to choose from; a lone type label is dropped.
  - Mayors shows every choice as a menu chip, the window first.
  - Chips wrap onto further lines and never scroll sideways.
- **Out of scope:**
  - A podium or a leader card on either board.
  - An explainer line on Mayors.
  - Any change to board rules, paging, what a row opens, or the Leaderboard and Mayor cards on the Segment page.
  - Any server or lexicon change.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `segments/segment-leaderboards`:
  - Rows share one anatomy.
  - The viewer's place is pinned with their gap to first.
  - Scope moves from a segmented control to a menu on iOS.
- `segments/segment-mayors`:
  - The viewer's place is pinned with the efforts needed to be Mayor.
  - Scope moves from a segmented control to a menu on both platforms.
- `dive-sites/site-regulars`: the viewer's place is pinned.

## Impact

- **`:app-common-ui`, `RankBoards.kt`:** `RankRow` gets the new anatomy and an `own` mark. `ownPlace` and `rankBoardRows` give way to a pinned bar that the boards place under their list.
- **`:app-common-api`:** `RankBoardTexts` gains the gap wording.
- **`feature/segment/api`:**
  - `SegmentBoardTexts` words the leaderboard and Mayors gaps.
  - The states gain the getters both UIs ask: `isOwn(entry)` and `gap`.
- **`feature/segment/ui`:** `SegmentLeaderboardUi` and `SegmentMayorsUi` change their filters and place the bar.
- **`feature/divesite/ui`:** `DiveSiteRegularsUi` places the bar.
- **iOS:**
  - `RankBoards.swift` changes `RankRow` and `YourPlace`.
  - `SegmentLeaderboardView`, `SegmentMayorsView` and `DiveSiteRegularsView` follow.
- **Strings:** new strings for the two gap lines, with plurals for efforts. The iOS catalog is regenerated.
- **Tests:**
  - Texts tests for the gaps.
  - Updated UI tests for the boards.
- **References and snapshots:**
  - The Android screenshot references for all three boards, re-recorded.
  - The iOS `leaderboard-*`, `mayors-*` and Regulars snapshots, re-recorded.
