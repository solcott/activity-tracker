# Proposal

## Why

Segment leaderboards (`segments/segment-leaderboards`) reward speed, so only the fastest athletes ever see their names at the top. This change rewards showing up instead, over the last 90 days, as **Mayor**: the athlete with the most efforts on a segment is its Mayor ("Mayor of Hill Road climb"). It reuses everything the time boards already settle (which efforts are rankable, who may see them, the scope, e-bike and bracket filters), so it needs no new private data.

## What Changes

- **A Mayors board per segment.** A new query, `feed.getSegmentMayors`, ranks athletes by how many efforts they have on an active segment in a window, most first. Rank 1 is the Mayor. Every effort counts, including repeats within one activity.
- **Ties go to whoever reached the count first.** Among equal counts, the athlete whose last counted effort came on an earlier date ranks ahead. Efforts carry only a local date, so the same count reached on the same day shares a rank.
- **Windows.** The last 90 days by default, plus all time, this year, last year, this month, last month, this week and last week. They are worked out from the AppView's UTC date, like the time boards, and weeks start on Monday.
- **The time boards' rules and filters.** Only efforts the time boards would rank count: on an active segment, seen by the viewer, not from an excluded activity, not by an athlete who hides from leaderboards, and within the speed cap. The everyone/following scope, the e-bike board and the age, weight and gender filters work as they do on the time boards.
- **Client.** An active segment's screen shows its Mayor for the last 90 days and the viewer's own count, and opens a Mayors screen with the window picker and the same filters as the leaderboard screen.
- `feed.getSegmentLeaderboard` and its periods are unchanged.

## Capabilities

### New Capabilities
- `segments/segment-mayors`: The Mayors board: what counts, how athletes are ordered and ranked, the windows, the filters it shares with leaderboards, paging and the viewer's own entry, and the Mayor on the segment screen and the Mayors screen.

### Modified Capabilities
None. The new board refers to `segments/segment-leaderboards` for which efforts are rankable and to `segments/leaderboard-brackets` for the filters, and changes neither.

## Impact

- **Lexicons:** a new `feed.getSegmentMayors` query and `feed.defs#mayorEntry`, mirrored in `core/lexicon` and `Nsid`. No record changes.
- **AppView:** the query in `SegmentViews`, reusing the time board's visibility, rankability, scope, e-bike and bracket predicates, with a count aggregate and a window that can end before today. Possibly an index on `segment_efforts (segment_uri, e_bike, started_on)`. No new tables or indexed data.
- **Client:** `SegmentRepository`, the segment screen's Mayor row, and a new Mayors screen across `:app-screens`, `:app-presenters` and `composeApp`.
- **Docs:** `.agents/rules/appview.md` gets a line on Mayors.
- **Dependencies:** `segment-leaderboards`, `segment-leaderboard-brackets` (both archived).
