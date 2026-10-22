# Proposal

## Why

A segment's boards mix every activity type in its sport group: a hike segment ranks hikes, walks, rucks and snowshoe outings together, a paddle segment kayaks against canoes and rows, and a ride segment handcycles against bikes. The only split is a ride segment's "E-bike board" chip. Athletes want to compare themselves with people doing the same thing, so each activity type should have its own board, chosen from a menu that lists the types the segment actually has, and opening on the type of the activity they came from.

## What Changes

- **A board per activity type.** Leaderboards and Mayors boards rank only efforts from one activity type: the activity's sport, with e-bike rides (e-bike and e-mountain-bike) as a type of their own, as today. **BREAKING:** the mixed board and the separate e-bike board go away; `feed.getSegmentLeaderboard` and `feed.getSegmentMayors` replace their `eBike` parameter with a `board` parameter.
- **The types a segment has.** Both queries also answer with the board they served and the types the viewer can see efforts of on the segment over all time, whatever the period, scope or brackets asked for. Asked for no board, they serve the type with the most such efforts. A type asked for by name is served, and listed, even with no efforts.
- **A type menu instead of the e-bike chip.** The leaderboard and Mayors screens show the board's type as a menu of the listed types, or as a plain label when there's only one. Choosing a type shows its board from the first page, as a new period does.
- **Opening on the activity you came from.** Opening a segment from an activity's segment list carries that activity's type to the segment screen, its Mayor card and both boards. Opened any other way (search, stars, a new segment), they open on the type with the most efforts.
- **The activity page's top-10 rank** comes from the effort's own type's board.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `segments/segment-leaderboards`: Boards are per activity type instead of standard and e-bike; the queries list the segment's types and pick a default; the leaderboard screen offers a type menu.
- `segments/segment-mayors`: Mayors boards are per activity type like leaderboards; the Mayor card and the Mayors screen follow the type you came from and offer the same menu.
- `segments/segment-efforts`: An effort's top-10 rank is on its own activity type's board.

## Impact

- **Lexicons:** `feed.getSegmentLeaderboard` and `feed.getSegmentMayors` drop `eBike` and gain `board`; both outputs gain `board` and `boards`. Mirrored in `core/lexicon`, with a helper that names an activity's board type (beside `SegmentSport`).
- **AppView:** the shared board predicate filters on the effort's activity's sport (already joined) and the e-bike flag; a grouped count lists a segment's types for the viewer. No migration: efforts keep their activity's row, and a sport change already rematches.
- **Client:** `SegmentScreen`, `SegmentLeaderboardScreen` and `SegmentMayorsScreen` carry an optional board type; the activity page passes its activity's; both board screens swap the e-bike chip for a type menu; the Mayor card asks for the carried type.
- **Docs:** `.agents/rules/appview.md` describes boards per activity type.
- **Dependencies:** `segment-leaderboards`, `segment-mayors` (both archived).
