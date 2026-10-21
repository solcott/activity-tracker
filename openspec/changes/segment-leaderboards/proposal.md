# Proposal

## Why

`segments-core` records efforts and shows an athlete their own, but nobody can compare times yet. This change is the fourth step of the segments roadmap, after `segments-core` and `segment-creation`. It adds per-segment leaderboards on active segments, with the opt-outs and plausibility checks that make a public ranking fair and safe.

## What Changes

- **Leaderboard query.** `feed.getSegmentLeaderboard` takes a segment, a period (all time, this year, this month) and a scope (everyone, people I follow). It returns one best effort per athlete, fastest first, each with its rank, plus the viewer's own entry.
  - It shows the effort's date only, never the time of day.
  - Each viewer sees the efforts their tier admits, by the same rule as `segments-core`: the narrowest of the activity's visibility and its Map and Pace/Speed categories, through `visibleTo`. The Public board holds only fully public efforts.
  - Only `active` segments have leaderboards.
- **Separate boards.** A segment has one sport group, so it has one board. A ride segment also has an e-bike board, and e-bike efforts (already marked by `segments-core`) appear only there.
- **Opt-outs.**
  - **Leaderboards hidden:** a field in `actor.segmentPrefs` (in `self`, indexed by the AppView). The athlete keeps their efforts and PRs on their own activities but never appears on a leaderboard. The preference stays private.
  - **Per activity:** "exclude from leaderboards", for a group ride or a stretch in a car. It is a field on the activity's `feed.segmentTrack` in `self`, so it stays private and exists only while matching is on. Turning matching off forgets it with the tracks.
- **Plausibility caps.** An effort whose average speed over the segment is faster than its sport group's cap stays on the athlete's activity but off the board. The matcher's corridor constants stay as they are: nothing yet shows they need tuning.
- **Rank and PR badges.**
  - An activity's best effort on a segment shows its rank on the viewer's all-time board when it is in the top 10.
  - The athlete sees why one of their own efforts isn't ranked (hidden, excluded, or faster than plausible).
- **Client.**
  - A leaderboard screen from an active segment's screen.
  - Rank badges on an activity's efforts.
  - The opt-out in the segment matching Settings.
  - The per-activity exclusion in the activity editor.

## Capabilities

### New Capabilities
- `segments/segment-leaderboards`: Leaderboards per segment, board and period. Covers who appears on them and who sees them, the opt-out, the per-activity exclusion, and plausibility caps.

### Modified Capabilities
- `segments/segment-efforts`: An activity's efforts gain a top-10 rank and, for their athlete, why one isn't ranked.
- `segments/segment-track`: The consent prompt mentions leaderboards, and a segment track carries and keeps the per-activity exclusion.

## Impact

- **Lexicons:**
  - `feed.getSegmentLeaderboard` and `#leaderboardEntry`;
  - `hideFromLeaderboards` on `actor.segmentPrefs`;
  - `excludeFromLeaderboards` on `feed.segmentTrack`;
  - `rank` and `notRanked` on `effortView`.
- **AppView:**
  - a migration for the two flags and an index;
  - leaderboard queries over `segment_efforts` (H2-portable), honoring tiers, blocks, moderation, the opt-out, the exclusion and caps.
- **Client:**
  - a `SegmentLeaderboardScreen` (via the `new-screen` skill);
  - rank badges;
  - the opt-out toggle in the segment matching Settings holder;
  - the per-activity exclusion in the activity editor, written to the segment track;
  - the sweeper keeping that exclusion when it rewrites tracks.
- **Docs:** AGENTS.md and `.agents/rules/appview.md` list the two new private flags the AppView holds.
- **Dependencies:** `segments-core`, `segment-creation` (both applied).
