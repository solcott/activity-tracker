# Proposal

## Why

Athletes want to compare themselves with others of similar age, weight and gender. This is the last step of the segments roadmap, after `segment-leaderboards`. It needs profile details the AppView never indexes today: birth date, weight and gender live in the public profile or in `actor.privateDetails` in the athlete's tier spaces. Even "Only me" details would leak through a bracket (a 40–49 board reveals an age range), so a bracket may be used only for athletes who consent to this feature, and the AppView should never see the details themselves.

## What Changes

- **Consent.** A separate opt-in in Settings, offered while segment matching is on, off by default. Turning it on makes the client write a small brackets record to the athlete's `self` space; turning it off deletes it, and the AppView drops what it held.
- **Brackets, computed on the device.** The record holds only the athlete's age group, weight class and gender bucket, each present only when the athlete has that detail. The client computes them from the athlete's own details and rewrites the record when a bracket changes (a birthday, a profile edit). The AppView never reads `birthDate`, `weight` or `gender` from any record.
- **Standard brackets.** Age: 19 and under, 20–24, 25–34, 35–44, 45–54, 55–64, 65–69, 70–74, 75+. Weight: under 55 kg, 55–64, 65–74, 75–84, 85–94, 95–104, 105–114, 115 kg and over. Gender: women, men, non-binary, from the profile's known values only.
- **Filters.** `feed.getSegmentLeaderboard` gains age-group, weight-class and gender filters. Athletes without consent, or without that bracket, never appear on a board filtered by it.
- **Client.** The consent toggle in Settings and the three filters on the leaderboard screen.

## Capabilities

### New Capabilities
- `segments/leaderboard-brackets`: The consent, the brackets record and how the client keeps it current, the bracket boundaries, and what the AppView may store from it.

### Modified Capabilities
- `segments/segment-leaderboards`: Boards filter by age group, weight class and gender; the leaderboard screen offers the filters.

## Impact

- **Lexicons:** a new `actor.leaderboardBrackets` record (self space only), listed in `space.athlete`; filter parameters on `feed.getSegmentLeaderboard`.
- **AppView:** indexing the brackets record from `self` only, a `leaderboard_brackets` table and migration, and the filters in the leaderboard query. Never served.
- **Client:** a brackets repository that computes and refreshes the record, the Settings toggle, the leaderboard filters. Sessions signed in before the space type lists the new record must sign in again to use it.
- **Docs:** BACKLOG.md's "AppView as a consented member for aggregation" section moves into this change's design; `.agents/rules/appview.md` and AGENTS.md list the new private data the AppView holds.
- **Dependencies:** `segment-leaderboards`.
