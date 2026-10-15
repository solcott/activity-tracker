# Proposal

## Why

When you open the profile of someone you follow who doesn't follow you back, your client asks their PDS for both their `followers` and `mutuals` tiers. The `mutuals` read always gets refused, and it costs a delegation token plus a credential exchange on every view, because refusals are deliberately not cached (`SpaceCredentials`). The AppView already knows the follow graph, so the client can tell in advance that the read will be refused and skip it, instead of caching refusals.

## What Changes

- `getProfile`'s viewer state gains `followedBy`: the URI of the athlete's effective follow of the viewer (a pending follow, awaiting the viewer's approval, doesn't count). It's absent when there's none or the viewer is signed out.
- `ProfileRepository.withHiddenDetails` reads the `mutuals` tier only when the view has both an effective `following` and `followedBy`. With `following` alone it reads only `followers`. Your own profile still reads every tier.
- If the AppView lags behind a new follow-back, the mutuals details appear once it indexes that follow. That's accepted, and it matches how follows already appear.
- Not doing: the negative cache of refused exchanges suggested in BACKLOG.md. A refusal on any other read stays uncached, so a new follow still counts on the next read.

## Capabilities

### New Capabilities

### Modified Capabilities
- `graph/follow-requests`: `getProfile`'s viewer state also reports `followedBy`, counting effective follows only.
- `profile/private-details`: a viewer the athlete doesn't effectively follow back SHALL NOT request a credential for the athlete's `mutuals` space.

## Impact

- `lexicons/app/activitytracker/actor/defs.json` (`#viewerState`), `core/lexicon` `ProfileViewerState`.
- AppView: the profile view's viewer state query (effective follow from the athlete to the viewer, `effectiveFollow()`), and its tests.
- `core/data`: `ProfileRepository.withHiddenDetails`, and `PendingFollows` if it builds viewer state.
- No migration: the follow tables already hold this.
- Follow with the `lexicon-change` skill.
