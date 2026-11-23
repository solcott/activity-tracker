# Design

## Context

- **Three boards share their pieces.**
  - Compose: `RankBoards.kt` in `:app-common-ui` holds `RankRow`, `BoardSegments`, `BoardChoiceChip`, `rankBoardRows` and `ownPlace`.
  - iOS: `RankBoards.swift` holds `RankRow` (over `AppItemRow`), `BoardFilters`, `BoardSegments`, `BoardMenu` and `YourPlace`.
  - The boards are the segment Leaderboard and Mayors (`feature/segment`) and the dive-site Regulars (`feature/divesite`).
- **The viewer's place:**
  - Each state has `viewerEntry`, which the presenter sets from the board's meta (`viewerApart`) only while the viewer's entry isn't among those loaded.
  - The board's meta (`BoardMeta`, in `entries`) also carries the viewer's own entry at every point, loaded or not.
  - Today `ownPlace` puts the viewer's row as a list item after the rows. iOS puts it in `PagedList`'s `after:` section.
- **Frames:**
  - `ListScreen` wraps `AppScaffold`, which already has a `bottomBar` slot. `ListScreen` doesn't pass it through yet.
  - On iOS, `PagedList` is a system `List`.
- **The row's value** is `bodyLarge` Medium in Compose and `.fontWeight(.medium).monospacedDigit()` in the summary slot on iOS.

## Goals / Non-Goals

**Goals:**
- The gap rules and the own-row rule are decided once in Kotlin and asked by both UIs.
- The three boards keep sharing one row and one bar.

**Non-Goals:**
- New presenter logic beyond reading what the board's meta already holds.
- Any change to paging or what a row opens.

## Decisions

### 1. The gap is a state getter over loaded data
- **Leaderboard:** `SegmentLeaderboardState.gap: AppText?` is computed from `entries.items.firstOrNull()` and `viewerEntry`. It's null when:
  - there's no viewer entry,
  - the first entry isn't rank 1,
  - or the viewer's rank is 1.
  The difference is `viewerEntry.effort.elapsed - first.effort.elapsed`, worded by `BoardGapTexts.behindFirst(seconds)`:
  - "%1$s s behind 1st" under 60 seconds,
  - "%1$s behind 1st" with `StatText.duration` from 60 seconds.
- **Mayors:** `SegmentMayorsState.gap` uses `first.efforts - viewer.efforts + 1`, worded by the plural `mayors_more_to_be_mayor` ("%1$s more efforts to be Mayor").
- The gap shows only in the pinned bar, and the bar shows only while `viewerEntry` is set, so it never needs the leader to be off screen.

*Alternative:* have the AppView serve the gap. That's rejected: the first page always holds the board's first entry, so the client has everything it needs, and the server would need a lexicon change.

### 2. Own-row marking reads the meta's viewer
- `isOwn(entry)` on each state compares `entry.athlete.did` with the meta's viewer entry's athlete.
- The pinned bar's row is the viewer's own row too.
- `RankRow` gains `own: Boolean`.
  - Compose: a row background of the brand colour at 8% (lighter than `primaryContainer`, so the avatar placeholder's circle still shows on it) and the name in SemiBold.
  - iOS: the list row background in the accent's tint at low opacity, and the name in semibold.
- Both platforms add "You" to the row's accessibility label rather than changing its text, since the row already shows the viewer's name.

### 3. A pinned bar, not a list item
- **Compose:**
  - `ListScreen` gains `bottomBar`, passed to `AppScaffold`.
  - A new `OwnPlaceBar(own, gap, row)` in `RankBoards.kt` draws:
    - the surface container step and a top hairline (no shadow, per DESIGN.md),
    - "Your place" in `labelLarge`,
    - the row, with the gap under its date as the row's second line, in `bodySmall` `onSurfaceVariant`.
  - `rankBoardRows` drops its `ownPlace` call.
  - The scaffold's padding already insets the list by the bar's height, so the last row clears it.
- **iOS:**
  - `YourPlace` becomes a `safeAreaInset(edge: .bottom)` view over the `PagedList`: a bar material background with a top divider, the heading, and the `RankRow` with the gap line. The list scrolls under it and insets to it.
  - `PagedList`'s `after:` is left empty for the boards.

*Alternative:* keep the row in the list and make it sticky with a sticky footer. Lazy lists in Compose support sticky headers, not footers, and SwiftUI's section footers don't pin to the bottom. A bar is the same on both platforms.

### 4. Filters: one row of chips under the bar
- **Layout:** both boards draw the same row, pinned under the screen's bar and outside the list, so it can scroll sideways (a list cell clips a sideways scroll) and the list's first row starts right under it. No segmented control, and no chip holding several choices (an earlier "Filters" chip folding age, weight and gender was dropped after a device look: it hid the brackets behind a second step).
  - Order: date range, scope, type (only with two or more types), age, weight, gender.
  - Compose: `BoardChipRow` in `RankBoards.kt`, a `LazyRow`-free horizontally scrolling `Row` of `BoardChoiceChip`s placed in `ListScreen`'s top slot under the bar, with a hairline under it.
  - iOS: `BoardChipBar`, a horizontal `ScrollView` of `BoardMenu`s in `safeAreaInset(edge: .top)` over the `PagedList`, with the list's top content margin removed.
- **Date ranges:** the leaderboard and Mayors share one set of windows (`BoardPeriod` in `core/lexicon`): today, this and last week, this and last month, the last 90 days, this and last year, all time, shortest first. The leaderboard opens on all time and Mayors on the last 90 days.
  - Lexicon: both queries list the same `knownValues` plus `today`; the leaderboard also keeps `year` and `month` for older clients, which the AppView reads as `thisYear` and `thisMonth`.
  - AppView: the leaderboard takes the Mayors window function (`boardWindow`, which gains `today`) instead of its own start-only `periodStart`, so last-week and last-month boards have an end.
  - "Today" is the AppView's UTC date, as every window is. An athlete west of UTC late in the evening sees tomorrow's board; this matches how the other windows already count.
- **Regulars:** its filters stay as they are. Only the row and the bar change.

### 5. Row anatomy
- **Compose `RankRow`:**
  - The rank keeps its fixed 36dp column.
  - The name is `bodyLarge` and may wrap (no `maxLines`).
  - The value is `titleMedium` SemiBold with the `"tnum"` font feature, never wrapping, aligned to the end.
- **iOS `RankRow`:** keeps `AppItemRow`'s layout. The summary goes to `.headline.monospacedDigit()`, and `.fixedSize()` keeps it whole. The chevron stays.
- Accent colours are unchanged: `state.accents`.

## Risks / Trade-offs

- **[The bar takes height on short screens]** → It holds one row plus a heading, about 88 points. It shows only when the viewer is off the loaded rows, and the list insets to it.
- **[The gap can go stale]** → If the leader's entry is refreshed while the viewer is pinned, the gap follows the next state, since it is a getter over the same `entries`.
- **[Mayors windows roll]** → "8 more efforts" assumes no one else's count changes. That holds for the moment the board was loaded, which is all a board claims.
