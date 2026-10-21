# Proposal

## Why

Strava lets athletes filter leaderboards by age group and weight class. This is the last, later step of the segments roadmap, after `segment-leaderboards`. It needs profile details the AppView never indexes today: age and weight live in `actor.privateDetails` in the athlete's tier spaces. Even "Only me" details would leak through a bracket (a 40–49 board reveals an age range), so the AppView may use them only for athletes who consent to this feature.

Design, specs and tasks are written together with BACKLOG.md's "AppView as a consented member for aggregation", once `segment-leaderboards` is applied.

## What Changes

- **Consent.** A separate opt-in (in `actor.segmentPrefs` or its own record in `self`) lets the AppView index an athlete's age group and weight class, from only the tiers it needs. Turning it off drops them.
- **Brackets, not values.** The AppView stores the bracket, not the birth date or weight.
- **Filters.** `feed.getSegmentLeaderboard` gains age-group and weight-class filters. Athletes without consent never appear on a filtered board.
- **Client.** The consent toggle in Settings and the filters on the leaderboard screen.

## Capabilities

### New Capabilities
- `segments/leaderboard-brackets`: The consent, what the AppView may store from it, and the bracket filters.

### Modified Capabilities
- `segments/segment-leaderboards`: Filters by age group and weight class.

## Impact

- **Lexicons:** the consent field or record; filter parameters on `feed.getSegmentLeaderboard`.
- **AppView:** reading the needed details from tier spaces for consenting athletes only; a bracket table and migration.
- **Docs:** BACKLOG.md's consented-aggregation section moves into this change's design; `.agents/rules/appview.md` and AGENTS.md list the new private data the AppView holds.
- **Dependencies:** `segment-leaderboards`.
