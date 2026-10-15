# Design

## Context

See proposal.md (Why). The AppView builds a profile's viewer state in one place, `viewerState` (`server/appview/.../api/ActorDetails.kt`), used by `getProfile`'s view (`Views.kt`). `effectiveFollow()` is a correlated condition on a `follows` row, and `follows(author, subject)` already wraps it for one pair. On the client, `ProfileRepository.withHiddenDetails` picks the tiers to read from the view's `viewer`, after `PendingFollows.applyTo` has laid the athlete's unindexed follow or unfollow over it.

## Goals / Non-Goals

**Goals:**
- One extra field on the existing viewer state; no new query or route.
- The client never asks for a `mutuals` credential it is sure to be refused.

**Non-Goals:**
- Caching refused exchanges (`SpaceCredentials` stays uncached for refusals).
- Hiding Followers or Mutuals UI based on `followedBy`: it only gates reads.

## Decisions

- **`followedBy` is the athlete's follow URI, set only when effective.** The AppView selects the athlete's `follows` row of the viewer ANDed with `effectiveFollow()`, so a follow pending the viewer's approval is absent, the same rule the PDS applies when it asks `checkUserAccess` about the `mutuals` tier (`isMutual`). A URI rather than a boolean mirrors `following` and bsky's `viewer.followedBy`. Alternative: report it even when pending, with a `followedByRequested` flag. Rejected: nothing on the client needs a pending follow-back, and the mutuals gate would have to re-derive effectiveness.
- **Client tier choice.** Own profile: every tier. Effective `following` (set and not `followRequested`) and `followedBy`: `followers` and `mutuals`. Effective `following` alone: `followers`. Otherwise none. Alternative: keep asking for `mutuals` when `followedBy` is absent but cache the refusal for a while. Rejected in the proposal.
- **`PendingFollows` needs no rule of its own.** It replaces only `following`/`followRequested` and copies the rest of the viewer state, so `followedBy` survives a pending follow: following back an open-policy athlete who already follows you reads `mutuals` at once, and a pending request or an unfollow reads nothing.
- **No migration.** The `follows` and `follow_approvals` tables already hold this.

## Risks / Trade-offs

- [AppView lags behind a new follow-back] → Mutuals details appear once the follow is indexed, as follows already do (accepted in the proposal).
- [AppView's follow graph disagrees with the PDS's `checkUserAccess` answer] → Both come from the same AppView (`checkUserAccess` is `SpaceAccess.kt` on the same tables), so they agree up to indexing lag.
