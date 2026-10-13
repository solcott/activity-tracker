# Design

## Context

- **The callout, as the pinned PDS makes it** (`/app/packages/pds/src/simplespace/manager.ts` and `api/com/atproto/space/util.ts` in the image pinned by `spaces-local-stack`):
  - The PDS calls the managing app only when the space's policy for that access is `managing-app`, and never for the authority itself: `authorizeUser` returns `true` for `userDid === spaceDid` before looking at the policy.
  - It resolves the `managingApp` string (`did:web:<host>#activitytracker_space`) to the DID document's `AtprotoSpaceService` endpoint and sends `GET /xrpc/com.atproto.simplespace.checkUserAccess` with params `space`, `user`, `access` (`read` | `write`) and `clientId` (only with a client attestation).
  - Its service-auth JWT has `iss` = the space's authority, **`aud` = the `managingApp` string, fragment included**, and `lxm` = `com.atproto.simplespace.checkUserAccess`.
  - It reads `res.body.authorized === true`. Any exception, non-200 or unreachable app means "deny". It calls with `access: 'write'` from `notifyWrite`, when another PDS reports a member's write.
- **`ServiceAuthVerifier`** accepts `aud` in `{serviceDid, serviceDid#activity_appview}` plus the same for each extra audience. A token addressed to `…#activitytracker_space` is rejected as "Wrong audience" today.
- **Routes.** `xrpcQuery(method, verifier)` passes an optional viewer; handlers that need one call `requireViewer`. `AuthException` maps to `401 AuthenticationRequired`, `XrpcError` to its own status.
- **Mutuals today.** `visibleDetails` (`api/PrivateDetails.kt`) treats a viewer as a mutual when `follows(viewer, did) && follows(did, viewer)`, over `follows`, which only holds `app.activitytracker.graph.follow` records. `follows_author_page_idx (author_did, …)` serves both lookups.
- **Tiers.** `athlete.json` declares `"key": "any"`; change 2 resolved that each tier is a space with skey `public`, `mutuals` or `self`, matching `Visibility`'s values. Changes 4 and 5 create them.

## Goals / Non-Goals

**Goals:**
- A `checkUserAccess` answer the pinned PDS accepts, with the same mutuals rule the AppView already enforces in views.
- Managing-app tokens usable only on `checkUserAccess`, and AppView tokens not usable there.

**Non-Goals:**
- Creating tier spaces, or choosing each tier's write policy and app access (change 4).
- Telling the PDS that a credential should be revoked after an unfollow. The credential lifetime (600 s) bounds the lag.
- Checking `clientId` against an allow list. Spaces use `open` app access.
- Blocks, muting or any rule beyond follows.

## Decisions

### D1. A second verifier for the managing-app audience
`ServiceAuthVerifier` takes the set of accepted audiences from its caller instead of deriving `#activity_appview` itself; a small helper (`appViewAudiences`) keeps today's set (`serviceDid`, `serviceDid#activity_appview`, and both forms of each extra audience). `appView` (`Application.kt`) derives a second instance with the same keys, `verifier.forAudiences(setOf(config.managingAppService))`, so tests that build the app get it too. `checkUserAccess` is registered with the second, every other route keeps the first.
- **Why:** the audience check is what stops a token minted for one purpose being replayed for another. Keeping two sets apart means a `checkUserAccess` token can't act as a viewer on `getProfile`, and a viewer's token for the AppView can't answer as an authority.
- **Extra audiences don't apply.** The PDS uses whatever `managingApp` the space names, which change 4 sets from `managingAppService` (the tunnel DID). There's no localhost variant to accept.
- **Alternatives:**
  - Add `#activitytracker_space` to the one verifier. Rejected: every route would accept callout tokens. `lxm` would usually stop that, but `lxm` is optional in the verifier.
  - Pass the expected audience to `verify` per call. Equivalent, but `xrpcQuery` already takes the verifier as its auth policy; a second instance needs no change to the route helpers.

### D2. The decision is a pure function of the space reference and the follow graph
New `api/SpaceAccess.kt`: `class SpaceAccess(db)` with `suspend fun check(issuer: Did, params): Output`, in this order:
1. `params.space.authority != issuer` → `false` (D3).
2. `params.space.type != Nsid.SPACE_ATHLETE` → `false`.
3. `params.access != "read"` → `false`.
4. By `params.space.skey`: `public` → `true`; `mutuals` → `follows(user, authority) && follows(authority, user)` in one transaction; anything else → `false`.

It reuses `follows` from `PrivateDetails.kt`, moved next to it if it reads better, so "mutual" has one definition. `user == authority` isn't special-cased: the PDS never asks, and if it did, `self` would wrongly deny but harmlessly (the PDS has already admitted the authority).
- **Why fail closed on unknowns:** an unknown skey or type is either a client bug or a newer tier this AppView doesn't know; denying keeps unknown data private, as `ActivityStreamsViews` treats unknown visibility as `self`.
- **Alternative:** a table of tier rules in the database. Nothing varies per athlete, so code is simpler.

### D3. A mismatched issuer is a denial, not an error
A valid token from a DID other than the space's authority gets `200 {"authorized": false}`, logged at `warn`.
- **Why:** the only legitimate caller is the authority's PDS. Any other issuer could only be probing whether two accounts are mutuals, which is public in the follow graph anyway, but answering it would confirm the AppView treats them as such. A denial gives nothing away, and the PDS treats errors and `false` the same.
- **Alternative:** `403`. Clearer in logs, but the spec keeps errors for authentication and parameters; the warn log covers debugging.

### D4. Mirror the descriptor in `Space.kt`; tier keys next to `Nsid.SPACE_ATHLETE`
`SimpleSpace.CheckUserAccess` (`Params(space: SpaceUri, user: Did, access: String, clientId: String? = null)`, `Output(authorized: Boolean)`) goes in `core/lexicon/.../atproto/Space.kt`, field names copied from the pinned image's `checkUserAccess.json`. `access` stays a `String` with `READ`/`WRITE` constants (open `knownValues`, per `lexicons.md`). The tier skeys go in an `AthleteSpace` object (`PUBLIC`, `MUTUALS`, `SELF`) in `core/lexicon`, defined from `Visibility`'s constants so a tier and a visibility can't drift. Changes 4 and 5 build `SpaceUri.of(did, Nsid.SPACE_ATHLETE, AthleteSpace.MUTUALS)` from them.
- **Why in `core/lexicon`:** the AppView and clients share it, and `XrpcParams.decode` needs the descriptor. Decoding `space` as `SpaceUri` and `user` as `Did` makes a malformed value a `400 InvalidRequest` through the existing route helper.

### D5. Prove it with the real PDS, not only unit tests
`AppViewTest`-style tests cover the decision table and authentication through `testApplication`, signing callouts with `TestAccountKey.serviceJwt(iss, aud = managingAppService, lxm)`. The end-to-end scenarios run once on the local stack (tunnel on) with a throwaway JVM program using `SpaceClient`, as change 2's integration check did: alice creates `public`, `mutuals` and `self` spaces with `managingAppPolicy` for reads, bob (mutual) and carol (follows alice only) read from each.
- **Why throwaway:** the stack needs Docker, the tunnel and OAuth sessions, which `./gradlew check` doesn't have.

## Risks / Trade-offs

- **[Follow indexing lags Jetstream]** → A new mutual is admitted, and an unfollow takes effect, only once the AppView indexes the follow record (usually seconds). On top of that a credential already issued lives up to 600 s. Both are accepted, as in change 2.
- **[Quick-tunnel DID changes on restart]** → Spaces created under the old tunnel name a `managingApp` that no longer resolves, so the PDS denies every non-owner. Fails closed; local data is throwaway. A named tunnel fixes it if it starts to hurt.
- **[The PDS has no timeout on the callout]** → The answer is two indexed lookups, so it's fast; a slow AppView only delays credential exchanges.
- **[The alpha changes `checkUserAccess`]** → The image stays pinned; a bump re-checks `manager.ts` and the lexicon, and the change stays in `Space.kt` and `SpaceAccess.kt`.
- **[`public` means any signed-in atproto account]** → Finding S5. Matches today's app, where the feed needs a session; the public tier holds nothing a logged-out web visitor needs.

## Migration Plan

1. Deploy the AppView with the new route. No migration; no client change.
2. Nothing uses managing-app spaces until change 4, so there's nothing to backfill.
3. **Rollback:** revert the change. The PDS's callout gets a 404 again and denies everyone but the authority.
