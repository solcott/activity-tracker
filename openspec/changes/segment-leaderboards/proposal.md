# Proposal

## Why

`segments-core` records efforts and shows an athlete their own, but nobody can compare times yet. This change is the fourth step of the segments roadmap. It adds per-sport leaderboards on active segments, with the opt-outs and plausibility checks that make a public ranking fair and safe.

Design, specs and tasks are written once `segments-core` and `segment-creation` are applied: leaderboards rank only `active`, non-duplicate segments, which `segment-creation` introduces.

## What Changes

- **Leaderboard query.** `feed.getSegmentLeaderboard` takes a segment, a period (all time, this year, this month) and a scope (everyone, people I follow), and returns one best effort per athlete, fastest first.
  - It shows the effort's date only, never the time of day.
  - Each viewer sees the efforts their tier admits, by the same rule as `segments-core`: the narrowest of the activity's visibility and its Map and Pace/Speed categories, through `visibleTo`. The Public board holds only fully public efforts.
  - Only `active` segments have leaderboards.
- **Separate boards.** One board per sport group, and e-bike efforts (already marked by `segments-core`) on their own board.
- **Opt-outs.**
  - **Leaderboards hidden:** a field in `actor.segmentPrefs` (in `self`, indexed by the AppView). The athlete keeps their efforts and PRs on their own activities but never appears on a leaderboard. The preference stays private.
  - **Per activity:** "exclude from leaderboards", for a group ride or a stretch in a car.
- **Plausibility caps.** Efforts faster than a per-sport cap (speed, and the matcher's named corridor constants tuned together) stay on the athlete's activity but off the board.
- **Client.** A leaderboard screen from the segment screen, and PR and rank badges on an activity's segment efforts.

## Capabilities

### New Capabilities
- `segments/segment-leaderboards`: Leaderboards per segment, sport group and period; who appears on them and who sees them; opt-outs and plausibility caps.

### Modified Capabilities
- `segments/segment-efforts`: Efforts gain leaderboard eligibility (per-activity exclusion, plausibility) and a rank.
- `segments/segment-track`: `actor.segmentPrefs` gains the leaderboard opt-out.

## Impact

- **Lexicons:** `feed.getSegmentLeaderboard`; a leaderboard opt-out on `actor.segmentPrefs`; a per-activity exclusion (on the activity record or its details); rank on the effort view.
- **AppView:** leaderboard queries over `segment_efforts` (H2-portable), honoring blocks, the opt-out, the exclusion and caps.
- **Client:** a `SegmentLeaderboardScreen` (via the `new-screen` skill), PR/rank badges, the opt-out toggle in the segment matching Settings holder, and the per-activity exclusion in the activity editor.
- **Dependencies:** `segments-core`, `segment-creation`.
