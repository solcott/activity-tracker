# Design

## Context

See proposal.md for why, and the delta specs for behavior. Today:

- `segment_efforts` stores the segment's sport group (`sport`) and an `e_bike` flag, set from `SegmentSport.of(sport, subSport)` when the matcher records an effort. It doesn't store the activity's own sport.
- Every board query runs over `rankedEfforts()`, which already joins each effort to its `activities` row, and `activities` has `sport` and `sub_sport`. A sport or sub-sport change already queues a `track` job (`index/MatchInputs.kt`), and the row is read live, so a board filtered on `activities.sport` follows an edited activity at once.
- `boardOf(viewer, segment, eBike, since, following, until)` is the one predicate behind `leaderboard`, `mayors` and `topRank` (the activity page's top-10 rank). `bracketsOf` and `following` validate their params.
- Clients reach both boards only from `SegmentScreen`, which is opened from an activity's segment list (`ActivityDetailEvent.SegmentOpened`), search, stars, a new segment or a duplicate's original. `SegmentLeaderboardScreen` and `SegmentMayorsScreen` carry the segment's group only to offer the e-bike chip (`canChooseEBike`).
- The repository returns `Outcome<Page<Entry, Entry?>>`, the viewer's own entry as the page's meta.

## Goals / Non-Goals

**Goals:**
- One definition of an effort's board type, shared by the matcher-free read path, the client and the activity page's rank.
- The menu is stable as the viewer changes period, scope or brackets, and never names a type the viewer can't see efforts of, except the one they asked for.

**Non-Goals:**
- Sub-sport boards (road, gravel, mountain): types are sports, with only e-bikes split out.
- A combined "all types" board.
- Remembering a viewer's last chosen type across segments.

## Decisions

### A board type is the activity's sport, or `eBike`
`SegmentBoard.of(sport, subSport): String?` in `core/lexicon`, beside `SegmentSport`: `eBike` when `SegmentSport.of(sport, subSport)` marks an e-bike, else the sport when it maps to a group, else null. `SegmentBoard.E_BIKE = "eBike"` and `SegmentBoard.all` (the sports `SegmentSport` maps, `ski` included, plus `eBike`) give the known tokens. `eBike` can't collide with a sport: it's a sub-sport token, and no sport has that value.
- *Alternative:* store a `board` column on `segment_efforts`. Rejected: it would need a migration and a rematch of every effort, and would go stale between an activity edit and its `track` job; the join is already there.

### Lexicon: `board` replaces `eBike`
`getSegmentLeaderboard` and `getSegmentMayors` drop `eBike` and take an optional `board` (known values from `SegmentBoard.all`). A token not in that list is `InvalidRequest`. Both outputs gain `board` (the type served; absent when none) and `boards` (the listed types, most efforts first). No other client exists, so the param is replaced rather than kept alongside.

### The predicate and the type list
- `boardOf` takes `board: String` in place of `eBike`: `e_bike = true` for `eBike`, else `e_bike = false AND activities.sport = board`.
- `boardsOf(viewer, segment)`: the same visibility and `rankable()` conditions without type, period, scope or brackets, grouped by `(e_bike, activities.sport)`, counted, mapped to tokens in Kotlin (several sports can't fold into one token, and `eBike` rows fold together), sorted by count desc then token. Read on every page so each response is complete; it is one grouped query over one segment's efforts.
- A requested type missing from the list is appended to it. With no type requested, the first listed is served; with an empty list, the response has no entries and no `board`.
- `topRank` builds the board from the effort's own type: the effort row's `e_bike` and its activity's sport, read alongside the effort.

### Carrying the type from the activity
- `SegmentScreen(uri, board: String? = null)`. `ActivityDetailPresenter` sets it from the effort (`eBike` when `effort.eBike`) and the activity's effective record (`SegmentBoard.of(sport, subSport)`), so a combined activity uses its own sport.
- `SegmentLeaderboardScreen(uri, name, board: String? = null)` and `SegmentMayorsScreen(uri, name, board: String? = null)`: the group parameter goes away with the chip.
- `SegmentPresenter` passes `screen.board` to the Mayor card's read and to both screens it opens.

### Client state
- The repository pages become `Outcome<Page<Entry, BoardMeta<Entry>>>`, with `BoardMeta(viewer, board, boards)`. One class serves both boards.
- Each board presenter keeps the *requested* type (`rememberSaveable`, starting from the screen's) as part of its paging key, and shows the *served* type and list from the latest page's meta. Choosing a type sets the requested one; choosing the type already served changes nothing, so the default board isn't loaded twice.
- The type control: an `AppFilterChip` with an `AppMenu` of the listed types when there are two or more, else a plain label. Labels come from `sportLabel` for sports and a new "E-bike" string for `eBike`.

## Risks / Trade-offs

- [Smaller boards: a few handcycles or walkers now each lead their own board] → Intended: like-for-like comparison is the point. The menu shows the busy types first.
- [A viewer asking for a type of another group (a `kayak` board on a ride segment) gets an empty board with `kayak` listed] → Clients never ask for one, since the carried type comes from an activity matched to the segment; validating group membership would add a segment lookup to the error path for no user benefit.
- [The list costs a grouped query per page] → One segment's efforts, filtered by the existing `segment_efforts_board_idx`; it could move to the first page only if it shows up in profiling.
- [Old leaderboard screens saved in navigation state carry a `sport` argument] → Circuit screens are restored from saved state only within an app run; an update restarts the app, so no saved screen outlives the change.

## Migration Plan

No data migration. The AppView and clients change together (one repository, no other clients); deploy the AppView first in a shared environment so no client asks for `board` on an AppView that doesn't know it.
