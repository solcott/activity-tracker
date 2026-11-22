# Design

## Context

- **Data path:** `getPath` (`SegmentViews.path`) returns the path, its listed `segmentView`s, its sections and its profile. `PathPresenter.contentOf` turns the output into `PathContent`: groups, sections and profile.
- **Leaderboards:** `SegmentViews.leaderboard` serves boards, built from private helpers in the same class:
  - `boardsOf` picks the busiest type.
  - `boardOf` builds the board's `Op`.
  - `bests` reads each athlete's best.
  - `ahead` counts the athletes in front.
  - `entries` makes the profiles and efforts.
- **The Segment page's Leaderboard card** reads `getSegmentLeaderboard` at one entry and words the result with `SegmentPageTexts.leaderboardValue` and `leaderboardYours` ("You: %1$s · #%2$s").
- **Rows:**
  - In Compose, rows are `SelectableRow` (`app/common/ui`), which segment browse and dive-site browse share.
  - On iOS, the Trail page's row is `AppItemRow` with a `RatingSwatch` leading view. `AppItemRow` already takes a trailing view.

## Goals / Non-Goals

**Goals:**
- A Trail page still loads in one request, and its leaders always agree with the segment's own board.
- One shared choice of the row's words: `PathTexts` decides them, and both UIs only lay them out.

**Non-Goals:**
- A new query.
- Leaders in browse or name search.
- Caching or denormalising board leaders in a table.

## Decisions

### 1. A `leaders` array on `getPath`, not a field on `segmentView`
`getPath`'s output gains `leaders: [#segmentLeader]`. Each entry holds `segment` (an at-uri), `board` (the type), `leader` and an optional `viewer`. `leader` and `viewer` are both the existing `feed.defs#leaderboardEntry`, the same type `getSegmentLeaderboard` returns, so clients read rank, athlete and elapsed time exactly as they do on the board. A segment with no leader is left out of the array.

*Alternative:* add the leader to `segmentView`. That type is served by about ten queries, and a leader there would be either absent from most of them or costly to compute everywhere. Keeping it on `getPath` keeps the cost on the one page that shows it.

### 2. Reuse the leaderboard's own queries inside `path`
`path` calls one new private helper, `leadersOf(viewer, uris)`, in the same transaction. For each listed segment that is `active` and ranked (`rankedSegment`), it:
1. Runs `boardsOf(viewer, uri, null)` for the default type.
2. Runs `boardOf(..., since = null, following = false)`.
3. Takes the first row of `bests` ordered `(BEST, author)` with `limit(1)`, plus the viewer's own best.
4. Builds both entries through `entries`, so the profiles, the effort chosen and the rank come out the same as on the board.

Because the code is shared rather than written again, the row can't drift from `getSegmentLeaderboard`. A test asserts the two agree.

*Alternative:* call `leaderboard()` per segment. It opens its own transaction and does a `requireServedPlace` check, which is redundant here because `getPath` has already filtered what's served.

### 3. Client model: `PathContent.leaders`
- `contentOf` passes `leaders` through as an `ImmutableList<GetPath.SegmentLeader>`. It's the lexicon's own `@Immutable` type, which Swift already reads in `LeaderboardEntry`, so no `-api` copy is needed.
- `PathContent.leaderOf(group)` looks up `group.shown`.
- `PathTexts` adds:
  - `leaderTime(leader)`, through `SegmentBoardTexts.time`.
  - `leaderName(leader)`, the athlete's shown name, as the Leaderboard card shows it.
  - `yours(leader)`, through `SegmentPageTexts.leaderboardYours`, so the row and the card are worded alike ("You: 14:02 · #12"; the brief's "You 14:02 · 12th" gives way to the card's wording).

### 4. `SelectableRow` gains an optional `trailing` slot and a `lines` list
`SelectableRow` takes:
- `trailing: (@Composable () -> Unit)?`.
- `extra: AppText?`, the third line.

The row becomes a `Row`:
- A leading swatch box, aligned to the name's first baseline.
- A `Column(weight(1f))` holding the name, the details and the extra line.
- The trailing slot, end-aligned.

The swatch moves out of the name's `Row` into its own column, so wrapped lines start under the name. The trailing column is a `Column(horizontalAlignment = End)`:
- The time, `titleMedium` with `fontFeatureSettings = "tnum"`.
- The handle, `bodySmall` `onSurfaceVariant`, one line, ellipsised, at a maximum width of 96 dp.

Browse and the dive-site list pass neither slot and get the new alignment. That fixes their swatch the same way.

On iOS, `AppItemRow` gains `.figure(_:)`, which lays the row out on the title's first line:
- The leading mark is centred half a capital height above the title's baseline.
- An `AppItemFigure` sits at the end of the first line: the value in `.body.weight(.semibold).monospacedDigit()` over a one-line `.caption`. Its width is the column's natural width, capped at 112 points by a small `Layout`, because a frame's `maxWidth` would stretch it to the full cap.
- The disclosure chevron stays centred on the row.

The Trail row passes the viewer's place as a third line in the primary colour. Rows that don't call `.figure` keep their old layout.

### 5. The ↔ rule lives in `SegmentBrowseTexts.joined`
When the group's display name contains `↔` and every sport group's wording is both ways, `joined` drops the trailing "Both ways" part. Because it sits in shared text, browse, name search and the Trail page all follow it.

## Risks / Trade-offs

- **[Query cost]** A path with many segments now runs about 4 queries per active segment. A large trail family can list 40 or more segments. → The helper only runs for active, ranked segments (proposed segments and lifts are skipped). Each query hits the existing `(segment_uri, author_did)` effort indexes. A test with 50 segments keeps it in check, and if it shows up in timing the per-segment reads can later be batched into one `GROUP BY segment_uri` query without changing the lexicon.
- **[Narrow phones]** The column takes about 72 to 96 dp, so long names wrap more. → This was accepted in the brief. The handle is ellipsised, and the time column never wraps.
- **[Swift export]** The new `PathLeader` must be a class Swift can read, with no `Pair` and no enum lists. → It's a plain data class in `-api` with `String`, `Int` and `Int?` fields.

## Migration Plan

- The lexicon change is additive. Older clients ignore `leaders`, and newer clients treat an absent field as no leaders.
- There's no database migration.
- To roll back, revert the commit. Clients degrade to rows without a column.
