# Design

## Context

See proposal.md for why. Today:

- Leaderboards (`segments/segment-leaderboards`) are served by `SegmentViews.leaderboard`, whose `rankable()` already joins `segment_prefs` to leave out athletes who hide from leaderboards. Filters are query params on `feed.getSegmentLeaderboard` (`period`, `scope`, `eBike`).
- The AppView indexes from `self` only `feed.segmentTrack` and `actor.segmentPrefs` (`Indexer.SEGMENT_COLLECTIONS`, `index/SegmentIndex.kt`), drops them on `dropSpace(self)` and account deletion, and never serves them.
- `SegmentPrefsRepository` (`core/data/.../segments`) is the pattern for a consent record in `self`: read once per session, saved by direct call, gated by `canWriteToSpaces(nsid)` because the PDS fills the `space:` grant from `space.athlete`'s declared collections at sign-in.
- Birth date, weight and gender are `actor.defs#details`, in the public profile or in `actor.privateDetails` per tier. `ProfileRepository.ownProfile()` already merges them for the owner (`ownDetails`).
- BACKLOG.md's "AppView as a consented member for aggregation" asked for consent per feature and indexing only from the tiers needed. This design answers it more strictly: the AppView reads no tier details at all.

## Goals / Non-Goals

**Goals:**
- The AppView learns only brackets, and only for consenting athletes.
- Filters compose with the existing period, scope and e-bike choices and the existing visibility rules.

**Non-Goals:**
- Age at the time of the effort. Boards use the athlete's current bracket, as Strava does.
- Per-bracket consent (sharing weight but not age). One choice covers all three; the record's optional fields leave room for it later.
- Custom gender descriptions as boards, or counts of athletes per bracket.

## Decisions

### A separate `actor.leaderboardBrackets` record, not a field on `actor.segmentPrefs`
Rkey `literal:self`, in the `self` space only. Fields: `ageGroup`, `weightClass`, `gender` (each an optional open-enum string with `knownValues` from the spec), `updatedAt`.
- Consent off is a delete, so nothing bracket-shaped lingers in a record the AppView keeps.
- `segmentPrefs` saves read-modify-write two settings already; a third, refreshed automatically each session, would race with Settings saves on another device.
- *Alternative:* a `brackets` object inside `segmentPrefs`. Rejected for the two reasons above.

The record is added to `space.athlete`'s collections and description. Old sessions lack it in their grant; `canWriteToSpaces(Nsid.ACTOR_LEADERBOARD_BRACKETS)` hides the choice for them (spec: "Old session").

### The client computes the brackets
`LeaderboardBrackets` (pure, `core/data/.../segments`) maps `ProfileDetails` and today's date (athlete's time zone) to the three values: age by whole years from `birthDate`, weight from grams by `floor(g / 1000)`, gender only for `female`/`male`/`nonBinary`.
`LeaderboardBracketsRepository` (`SessionScope`) holds `current: StateFlow<Boolean?>` (on/off as read), `save(on)` (direct call: on computes from `ProfileRepository.ownProfile()` and puts; off deletes), and `refresh()`, which, while on, recomputes and puts only if the stored values differ. It depends on `ProfileRepository`, never the reverse, so the profile save signals it through a flow rather than a call.
- *Alternative:* AppView reads `privateDetails` from tiers (it's a member of followers/mutuals and syncs self). Rejected: it would hold birth dates and weights, even transiently, and need the details from every tier.

### When the client refreshes
- `refreshOnSave()` runs for the session from `App` (beside `repairRoutesOnSave`), so it outlives any screen: it refreshes once at its start from the stored details, then after each `ProfileRepository.save`, whose `savedDetails` flow it collects. Only a changed bracket is written, so saving other fields writes nothing.
- A refresh failure is logged and left for the next session or save; it never fails the profile save, which has finished by then.
- A device that is never opened keeps a stale age group; accepted (see risks).

### AppView storage and filtering
- Flyway `V34__leaderboard_brackets.sql`: `leaderboard_brackets(did PK, age_group NULL, weight_class NULL, gender NULL, updated_at)`. H2-portable.
- Indexed only from `self`: add the NSID to `SEGMENT_COLLECTIONS` handling (`upsertSegments` / `deleteSegments` branch, or a sibling `upsertLeaderboardBrackets`). Unknown values are stored as NULL. Delete record, `dropSpace(self)` and account deletion delete the row.
- Not tied to matching: the row stays while matching is off, since matching off drops every effort anyway, and turning matching on again shouldn't need a fresh consent.
- `feed.getSegmentLeaderboard` gains optional `ageGroup`, `weightClass`, `gender` params with the known values; any other value is `InvalidRequest`. When any is set, `rankable()` adds an `exists` on `leaderboard_brackets` matching each set column. Ranks and the viewer entry then follow from the existing query, so the viewer entry is absent when the viewer isn't in the bracket.
- Params mirrored in `core/lexicon` (`GetSegmentLeaderboard.Params`, `LeaderboardAgeGroup`, `LeaderboardWeightClass`, `LeaderboardGender` constants objects, like `LeaderboardPeriod`).

### Client UI
- Settings: a toggle beside "Show me on leaderboards", following `SegmentMatchingSetting` (direct write, error keeps the old choice), shown only while matching is on and `canUse`. Its text says the brackets become visible to anyone who can see their efforts.
- Leaderboard screen: `SegmentLeaderboardState` gains `ageGroup`, `weightClass`, `gender` (null = All) and events to change each; each change restarts paging as period does. Weight labels convert the kg classes to the viewer's units (`UnitsRepository`), rounded to whole pounds.

## Risks / Trade-offs

- [A filtered board reveals a bracket to anyone who can see the effort] → That's what the athlete consented to; the Settings text says so, and existing effort visibility still applies.
- [Stale age group when the athlete doesn't open the app around a birthday] → Accepted; Strava has the same lag. Refreshes run every session.
- [Combining filters narrows a board to one person, revealing all three brackets] → Each bracket is already revealed singly; combining adds nothing beyond what consent covers.
- [Old sessions can't write the record] → The choice is hidden for them; they sign in again to get it, as with every new space collection.

## Migration Plan

Additive: new lexicon, new table, new optional params. Deploy the AppView first (it ignores the new record until then, and older clients never send the params). Rollback drops the params; the table can stay.
