# Design

## Context

- **Saving today.** `ProfileRepository.save` (`core/data/.../profile`) splits an edit by `DetailGroup` (location, birthdate, gender, body (weight and height share one visibility), training). Public groups go into the `actor.profile` record with `putRecord`, and the rest go to `actor.putPrivateDetails`, which replaces every private detail at once. A group going public is first kept in the private store as Only me, and cleared only after the record holds it. Profile saves are direct calls, not outbox writes, and the edit screen shows a failure.
- **Reading today.** `ownProfile()` merges the PDS record with `getPrivateDetails` (`ownDetails`). Other athletes' details come only from `getProfile`, where `visibleDetails` (`api/PrivateDetails.kt`) merges `actor_private_details` for the athlete themselves and for mutuals. `ActivityStreamsViews.visibleStreams` keeps zone snapshots when `visibleDetails(...)?.training != null`.
- **Spaces pieces from changes 1 to 3:**
  - `SessionClients.spaces` (`SpaceClient`) does owner reads with `read_self`, member reads with credentials, and `createSpace` (which reports `AlreadyExists`), `getSpace`, `putRecord` and `deleteRecord`.
  - `AthleteSpace.MUTUALS`/`SELF` hold the tier skeys.
  - `athlete.json` already declares the collection `app.activitytracker.actor.privateDetails`.
  - The AppView's `checkUserAccess` admits mutuals to `mutuals`, everyone to `public`, and nobody to `self`.
  - The OAuth scope already includes `manage=update`.
- **The pinned PDS image** serves `com.atproto.simplespace.updateSpace` (`space`, plus optional `readPolicy`, `writePolicy` and `appAccess`, each replacing the old one wholesale). It isn't mirrored in `Space.kt` yet.
- **The AppView's DID.** Native clients reach the AppView through the tunnel, while the web client reaches it at `localhost:8081` and uses `did:web:localhost%3A8081` as its service-auth audience (`NetworkConfigs`). The AppView's `/.well-known/did.json` always carries its real `serviceDid` (the tunnel host) as `id`, with the `#activitytracker_space` service.

## Goals / Non-Goals

**Goals:**
- No non-public profile detail is stored or served by the AppView.
- The edit-profile flow keeps its current shape and its "never lose a detail" guarantee.
- No space request for a viewer who can't be a mutual (doesn't follow the athlete).

**Non-Goals:**
- The `public` tier space. Public details stay in `actor.profile`, so nothing is written to it yet (change 5 may use it for streams).
- An outbox for space writes. Profile saves stay direct calls, as `putPrivateDetails` was (D6).
- Copying existing `actor_private_details` rows (local-only data, see the proposal).
- The AppView reading spaces as a member, for aggregation or anything else (D8).
- Zone snapshots in spaces (change 5).

## Decisions

### D1. One `privateDetails` record per tier, rkey `self`, holding only that tier's details
New lexicon `app.activitytracker.actor.privateDetails`: a record with `"key": "literal:self"` and a required `details` (`actor.defs#details`). The Kotlin mirror is `PrivateDetailsRecord` (with `$type`). `Nsid.ACTOR_PRIVATE_DETAILS` stays out of `indexedCollections`, because space records never reach Jetstream. The tier a detail sits in *is* its visibility.
- **Why:** one read per tier rebuilds the profile, and one put per tier saves it. A record per group would mean five rkeys and partial-write states between them.
- **Alternatives:**
  - A single record in `self` with a visibility map. Rejected: mutuals couldn't read it.
  - Keeping `#detailsVisibility` in the record. Rejected: it's redundant with the tier, and it could contradict it.

### D2. `DetailsVisibility` leaves the lexicon
With `put/getPrivateDetails` gone, nothing on the wire carries `#detailsVisibility`. Remove it from `defs.json`, and move the Kotlin class unchanged to `core/data/.../profile` (same name and fields, a new package), where `DetailGroup`, `OwnProfile` and `ProfileEdit` use it.
- **Alternative:** keeping it in `core/lexicon` without a lexicon def. Rejected: `lexicons.md` keeps that module a mirror of the JSON.

### D3. `AthleteSpaces` ensures a tier space once per session
A class in `core/data/.../profile` with `suspend fun ensure(tier): SpaceUri`, built from `SessionClients.spaces` and `SessionClients.managingApp()` and owned by the session-scoped `ProfileRepository` (its only user), so it lives exactly as long as the session. On the first call for a tier in a session, it:
1. Calls `createSpace(SPACE_ATHLETE, tier, readPolicy = ManagingApp(managingApp), writePolicy = MemberList(), appAccess = Open())`.
2. On `AlreadyExists`, calls `getSpace`. If the read policy isn't `ManagingApp(managingApp)`, it calls `updateSpace(readPolicy = ManagingApp(managingApp))`.

The result is memoized per tier, with one in-flight ensure per tier shared by concurrent callers.
- **Write policy `MemberList()` with no members:** only the authority writes its own repo, and no write callout reaches the AppView. That matches `checkUserAccess`, which denies `write` anyway, without a round trip.
- **Only on save:** reads never create spaces. Reading a tier that doesn't exist gives `NotFound`, which means "no details there".
- **Alternative:** creating all tiers at sign-in. Rejected: it adds requests to every sign-in, and it creates spaces for athletes who never use them.

### D4. The managing-app id comes from the AppView's DID document
`core/atproto` gains `ManagingApp.resolve(http, appViewUrl): String`. It GETs `{appViewUrl}/.well-known/did.json` and returns `id + "#activitytracker_space"`, after checking that a service with that id and type `AtprotoSpaceService` is listed (otherwise it throws). `SessionClients.managingApp()` calls it once per session (lazily, on the first `ensure`).
- **Why:** `config.appViewDid` is the *audience the client uses*, which on the web is the localhost DID, and the PDS can't resolve that (spaces-local-stack D5). The DID document always names the real service DID.
- **Alternatives:**
  - A new `describeServer`-style XRPC. Rejected: the DID document already says this.
  - Adding the id to client config (`local.properties`). Rejected: one more knob to keep in sync with the tunnel, which changes every run.

### D5. Save order: add, then the record, then trim
`save(edit)` computes each group's target: `PUBLIC`, `MUTUALS` or `SELF`. It also computes each tier's *current* contents, read with `read_self` only when the edit doesn't own every group, as `kept` does today.
1. **Add.** For each tier, put its new contents **plus any detail leaving that tier** (`ensure` first, only if the tier will hold something). After this step every detail is in its new home, and also in its old one if it moved.
2. **Write the profile record** with the public groups.
3. **Trim.** Re-put each tier whose step-1 contents differ from its final contents, or `deleteRecord` it when its final contents are empty.

While a save is in progress, a moved detail is visible to the union of its old and new audiences. That union is never wider than the more public of "was" and "will be", which is the guarantee today's code gives for details going public. It generalizes the current "going public stays as Only me" rule to every move.
- **Alternative:** ordering the writes by tier (`self` first, and so on). Rejected: a save that moves one detail from Only me to Mutuals and another from Mutuals to Only me has no safe order.

### D6. Profile saves stay direct, not outbox
Edit profile already needs a connection: it uploads the avatar and writes the record with `putRecord` directly, and it reports failure on screen. Tier writes join it.
- **Why not the outbox (named in change 2's non-goals):** the outbox exists so activity recording works offline, with optimistic feed updates. A profile save that silently retried later would leave the edit screen claiming success while a visibility change hasn't happened yet, which is the wrong failure for privacy settings. When something needs offline space writes (change 5's streams), it adds an outbox target there.

### D7. Viewing a profile: read `mutuals` only when following
`ProfileRepository.profile(actor)` still calls `getProfile`. Then:
- **Own profile:** read both tiers with `read_self` and merge, public first. That replaces the old AppView merge for "Only me" details on your own profile.
- **Someone you follow** (`view.viewer?.following != null`): `spaces.getRecord(SpaceUri.of(view.did, SPACE_ATHLETE, MUTUALS), view.did, ACTOR_PRIVATE_DETAILS, "self")`. `Found` is merged under the public details. `NotFound` and `NoAccess` show public only. A thrown failure is logged (Kermit, without record contents) and also shows public only, so a down PDS doesn't break the profile.
- **Anyone else:** no space call.
- **Why gate on following:** a mutual must follow the athlete, so this skips a doomed delegation and exchange for everyone else. The athlete following back is checked by the PDS, through `checkUserAccess`.
- **`pendingOwnProfile`** keeps laying the last save over the view, as today, so an edit shows at once.

### D8. AppView reads for aggregation: deferred, kept possible
Segments and leaderboards will need the AppView to read some data. When they're scoped, a later change can:
- give the AppView a signing key in its DID document so it can mint delegation tokens;
- have `checkUserAccess` admit the AppView's DID for `read` on the tiers that feature needs, gated on an explicit athlete opt-in.

This change keeps that open: the AppView stays each tier's managing app, so admitting itself is a rule change in `SpaceAccess.kt`, not a data migration. Recorded in BACKLOG.
- **Why not now:** no feature needs it yet. Whether the PDS accepts a `did:web` delegation issuer is untested, and "Only me" plus aggregation needs a consent design.

### D9. Sign-in-again is a presenter state, not a retry
`ProfileRepository` lets the `ScopeMissingError` `XrpcException` propagate. `EditProfilePresenter` maps it to a new `UiError.Kind.SignInAgain`, and the UI shows that with a "Sign in again" action that signs out (`AuthRepository.signOut`), which returns to `LoginScreen`.
- **On load,** `ownProfile()` catches `ScopeMissingError` on the tier reads only, returns the public details, and sets `OwnProfile.needsSignIn`. The presenter then shows the same error, so the athlete can still edit public details on that session.

### D10. AppView: public details only, zones on public training
- **Removed:** `PrivateDetails.put/get`, the `getPrivateDetails`/`putPrivateDetails` routes, `ActorPrivateDetails` (`Tables.kt`), and its delete in `Indexer.deleteActor`.
- **`visibleDetails(actor, viewer)`** becomes `publicDetails(actor)`. It keeps the location/training decoding, and it moves next to `Views.kt` with `isMutual`/`follows` (still used by `SpaceAccess` and `ActivityStreamsViews.mayView`), since `PrivateDetails.kt` goes away.
- **Zones:** `seesTraining = isSelf || publicDetails(actor)?.training != null`.
- **Migration:** `V13__drop_private_details.sql`, with `DROP TABLE actor_private_details;` plus a comment pointing at the spaces. It's H2-portable.

## Risks / Trade-offs

- **[A profile view by a follower adds a delegation-token round trip and a credential exchange]** → Credentials are cached for about 590 s per space, so repeat views are one read. Denials aren't cached (change 2), so a one-way follower pays an exchange on every view. That's accepted for now; a short-lived negative cache in `ProfileRepository` is the fix if it shows up.
- **[The managing-app id changes when the quick tunnel restarts]** → D3 rewrites the read policy on the next save. Until then, mutual reads are denied (fail closed). Nothing rewrites it for an athlete who never saves again, which is acceptable on throwaway local data.
- **[A failed save leaves a moved detail in two tiers]** → By D5 that's the wider-of-old-and-new audience, as today. The next save (or re-save) trims it.
- **[Mutuals lose zone snapshots on streams until change 5]** → Accepted in the proposal. Fails closed.
- **[`getProfile` callers other than the app (none today) would lose mutuals details]** → `getProfile`'s lexicon description is updated to say "public details only".
- **[`updateSpace` replaces the read policy wholesale]** → We only ever set `ManagingApp(managingApp)`, so nothing else is lost.

## Migration Plan

1. Land the AppView and client together (one repo, local dev only). Run the AppView migration (`V13`) on start.
2. Locally: `scripts/dev.sh` (tunnel on). Old sessions get the sign-in-again prompt. Re-enter non-public details once, because the dropped rows aren't copied.
3. **End-to-end check:**
   - alice saves her weight as Mutuals and her birthdate as Only me.
   - bob (a mutual) sees the weight and not the birthdate.
   - carol (who follows alice one-way) sees neither.
   - alice sees both, on edit profile and on her own profile.
   - **Result (2026-10-13, web app with OAuth sessions, tunnel on):** all four held. alice's public record and public repo held neither detail, and an anonymous space read was refused. `.dev/appview.log` showed one `checkUserAccess` for bob and one for carol, both `200`; the PDS then gave carol `UserNotAuthorized`, which the app shows as no details. After a tunnel restart (7.2), bob got `UserNotAuthorized` until alice re-saved, and the re-save's `updateSpace` let him back in. A session without `space:` (7.3) loaded public details, showed the sign-in-again message, and its button returned to login.
   - **Two fixes came out of it:**
     - A leftover `actor.privateDetails` record from change 2's check (no `details`) made edit profile fail to load. `AthleteSpaces.details` now reads a record that doesn't decode as no details.
     - A save that didn't change any detail wrote no space, so it never repaired a stale managing app. `save` now ensures every tier that holds details.
4. **Rollback:** revert the change, then `docker compose ... down -v` to reset the AppView DB, since V13 can't be undone in place. The data in the spaces stays on the PDS and is ignored.
