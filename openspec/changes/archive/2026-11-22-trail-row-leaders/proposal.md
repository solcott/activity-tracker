# Proposal

## Why

The Trail page lists a trail's segments, but nothing in a row says how fast anyone rides them, or how the viewer stands. To learn either, the athlete opens each segment in turn. The design canvas ([Trail & Segment pages](https://claude.ai/artifact/3fh7CDY6oiG6qbripg1cVy)) put leaders on Trail rows after the page layout, filters and profile. All three are now in place, so this comes next. The rows have also drifted between Compose and iOS: the swatch, the chevron and the indents differ, so this change gives them one anatomy.

## What Changes

- **Leaders on Trail rows:** a segment-group row on the Trail page shows its shown segment's leader in a trailing column: the fastest time, then the leader's handle under it. Times are right-aligned and lead (DESIGN.md's Numbers Lead rule).
- **The viewer's own effort:** when the viewer is on that board, the row adds a third line with their time and place, in the Segment page Leaderboard card's words ("You: 14:02 · #12").
- **Which board:** the segment's default board, the busiest type, all time, everyone, no brackets. These are the leaderboard's own rules, so the row and the Segment page's Leaderboard card always agree.
- **No column** for a lift, a proposed segment, or a board with no efforts the viewer may see. The name and details then take the full width.
- **Served by `getPath`:** each listed segment's leader and the viewer's own entry, worked out per viewer under every leaderboard rule (visibility, hidden athletes, excluded activities, the speed cap, blocks). A Trail page still loads in one request. The lexicon, its Kotlin mirror and the AppView change together.
- **One row anatomy on Compose and iOS:**
  - The rating swatch sits on the name's first line.
  - A wrapped name and the lines under it start under the name's text.
  - The trailing column comes next. iOS keeps its disclosure chevron after it, and Android and desktop have none.
- **A shorter details line:** a segment-group row whose name already shows ↔ leaves out a lone "Both ways". This applies to every list that shows segment-group rows, so browse and the Trail page read the same.
- **Out of scope:** Mayors on rows, bracket boards on rows, restyling the Leaderboard and Mayors screens, and leaders in browse's lists.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `segments/path-page`: segment rows show their board's leader and the viewer's own place, and rows share one anatomy across platforms.
- `segments/segment-leaderboards`: `getPath` serves each listed segment's leader and the viewer's own entry, under the board's rules.
- `segments/segment-groups`: a row whose name shows ↔ leaves out a lone "Both ways" in its details.

## Impact

- **Lexicon:** `app.activitytracker.feed.getPath` output gains `leaders`, an array of per-segment entries naming the board, the leader's time and athlete, and the viewer's own time and place. Older clients ignore it.
- **`core/lexicon`:** the `GetPath` mirror gains the new types.
- **AppView:** `SegmentViews.path` (`server/appview/.../api/SegmentViews.kt`) reuses the leaderboard's board and rank queries. There's no migration and no new table.
- **Client:**
  - `PathContent` (`feature/segment/api`) carries the leaders.
  - `PathTexts` words the column and the viewer's line.
  - `SelectableRow` (`app/common/ui`) gains an optional trailing figure and leading-line alignment.
  - `SegmentBrowseTexts.details` gets the ↔ rule.
  - `PathPage.swift` and its row view follow.
- **Tests and references:**
  - AppView route tests.
  - `PathTexts` and presenter tests.
  - `PathUiTest`.
  - Android screenshot references and iOS `path-sample.*` snapshots, both re-recorded.
  - `:app-fixtures` path samples, which gain leaders.
