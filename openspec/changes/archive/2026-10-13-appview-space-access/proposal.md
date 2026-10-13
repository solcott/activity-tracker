# Proposal

## Why

Changes 4 and 5 put athletes' private profile details and activity streams in `app.activitytracker.space.athlete` spaces, one per visibility tier (`public`, `mutuals`, `self`), with the AppView as each space's managing app. When a viewer asks for a credential to one of those spaces, the athlete's PDS calls the AppView's `com.atproto.simplespace.checkUserAccess`. Today that route doesn't exist, so the PDS gets a 404 and denies everyone but the athlete (spike finding S2). The AppView already knows the follow graph that decides who counts as a mutual, so it can answer the callout without anything new being stored.

This is change 3 of 6. It adds the AppView's side only; no client creates a managing-app space yet.

## What Changes

- **`checkUserAccess` route.** `GET /xrpc/com.atproto.simplespace.checkUserAccess?space=&user=&access=[&clientId=]` answers `{"authorized": true|false}`, decided by the space's tier (its skey):
  - `public`: any user may read.
  - `mutuals`: a user may read if they and the space's authority follow each other in this app's indexed follow graph (`follows`), the same rule `visibleDetails` applies today.
  - `self`, or any other skey: no one but the authority, whom the PDS admits without asking.
  - `write`, or any unknown `access`: denied. Only the athlete writes to their spaces.
  - A space of another type is denied.
- **Callout authentication.** The PDS signs the call with a service-auth JWT whose `iss` is the space's authority, `aud` is the managing-app id (`<serviceDid>#activitytracker_space`, not the AppView's own DID) and `lxm` is `com.atproto.simplespace.checkUserAccess`. The route accepts exactly that audience, requires the token, and answers `authorized: false` when the issuer isn't the space's authority.
- **Denial is an answer.** Every policy decision is `200 {"authorized": false}`, never an error, so nothing about the decision depends on how the PDS treats non-200s. Only a missing or invalid token or bad parameters get an error.
- **Wire type.** `SimpleSpace.CheckUserAccess` joins the other Spaces descriptors in `core/lexicon/.../atproto/Space.kt`, with the tier skeys as constants beside `Nsid.SPACE_ATHLETE`.

## Capabilities

### New Capabilities
<!-- None: access decisions are part of the managing app's job. -->

### Modified Capabilities
- `spaces/managing-app`: The AppView now answers `checkUserAccess` callouts and decides access to athletes' spaces per tier, instead of only advertising itself in its DID document.

## Impact

- **`core/lexicon`:** `atproto/Space.kt` gains `SimpleSpace.CheckUserAccess` (params `space`, `user`, `access`, `clientId`; output `authorized`). Tier skey constants (`public`, `mutuals`, `self`) for `app.activitytracker.space.athlete`.
- **AppView:**
  - New `api/SpaceAccess.kt` (the decision, over `Follows`).
  - `api/Routes.kt` registers the route with a second `ServiceAuthVerifier` that accepts only the managing-app audience.
  - `auth/ServiceAuthVerifier.kt` takes its accepted audiences from the caller instead of always adding `#activity_appview`.
  - `Application.kt` builds the second verifier.
  - Tests in `src/test`: the tier decisions end to end through `testApplication` with tokens signed by `TestAccountKey`, and the verifier's audience handling.
- **No migration, no new table, no new dependency.** The decision reads the existing `follows` table.
- **Docs:** `.agents/rules/appview.md` (the route and its audience), BACKLOG's two Spaces items point at this change for the AppView side.
- **Clients:** none. `SpaceClient` already reports a denial as `NoAccess`.
