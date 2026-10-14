# Proposal

## Why

Profile details an athlete marks Mutuals or Only me (location, birthdate, gender, weight and height, training) are the one kind of data the AppView stores rather than indexes. Clients send them to `actor.putPrivateDetails`, and they sit in `actor_private_details`. Changes 1 to 3 built what a native home needs: a PDS that supports Spaces, a Kotlin Spaces client, and the AppView answering `checkUserAccess` as each athlete space's managing app. This is change 4 of the Spaces series. It moves those details into the athlete's own tier spaces, so they live on the athlete's PDS and the AppView no longer holds a copy.

## What Changes

- **Details live in tier spaces.** Each non-public detail is stored in the athlete's `app.activitytracker.space.athlete` space for its tier (`mutuals` or `self`), in one record per tier: a new `app.activitytracker.actor.privateDetails` record with rkey `self`. Which space holds a detail is its visibility, so no visibility field is stored. Public details stay in `actor.profile`.
- **Clients create spaces lazily.** The first save that puts a detail in a tier creates that tier's space, with the AppView as managing app for reads. Clients learn the managing-app id from the AppView's DID document, not their own config, because the web client addresses the AppView by a localhost DID the PDS can't reach. A space whose policy names a stale managing app (the quick tunnel's host changed) is updated to the current one.
- **Saving writes to spaces.** `ProfileRepository.save` writes each tier's record (or deletes it when the tier is empty) and the profile record. As today, a detail's new home is written before its old one is cleared.
- **Reading merges spaces into the profile.** The athlete reads their own tiers with their session (`read_self`). On another athlete's profile, a viewer who follows them reads that athlete's `mutuals` tier as a member, and the PDS asks the AppView whether they're mutuals. Details found are merged under the public ones. A refusal or a missing record means public details only, and a failed read never fails the profile.
- **Signing in again.** A session from before the `space:` scope gets `ScopeMissingError`. Edit profile then tells the athlete to sign in again and offers that action, instead of failing with a raw error.
- **BREAKING (API): `actor.putPrivateDetails` and `actor.getPrivateDetails` are removed**, along with `actor.defs#detailsVisibility`. `getProfile` returns public details only.
- **BREAKING (local data):** a new Flyway migration drops `actor_private_details`. Existing rows aren't copied: they exist only on the local dev stack, which change 1 already wiped.
- **Stream zones fall back to public training.** `feed.getActivityStreams` keeps zone snapshots for the athlete and for viewers when the athlete's training is public. Until change 5 moves zones into the space, mutuals no longer see them.
- **A path for later AppView reads.** BACKLOG gains "AppView as a consented member for aggregation", for segments and leaderboards. Nothing here builds that, and nothing here blocks it.

## Capabilities

### New Capabilities
- `profile/private-details`: Where an athlete's non-public profile details are stored, how the athlete saves and reads them, and who sees them on a profile.

### Modified Capabilities
- `spaces/client-protocol`: "Owner writes" adds updating a space's policies, so a stale managing app can be replaced.

## Impact

- **Lexicons:**
  - New: `actor/privateDetails.json` (a record).
  - Removed: `putPrivateDetails.json`, `getPrivateDetails.json` and `defs#detailsVisibility`.
  - Updated descriptions: `getProfile`/`defs#profileView` now say details are public only.
- **`core/lexicon`:**
  - New: a `PrivateDetailsRecord` model and `Nsid.ACTOR_PRIVATE_DETAILS`, which isn't indexed.
  - Removed: `PutPrivateDetails`/`GetPrivateDetails`.
  - Moved: `DetailsVisibility` moves to `core/data`.
  - New: a `SimpleSpace.UpdateSpace` descriptor.
- **`core/atproto`:** `SpaceClient.updateSpace`, and a lookup of the managing-app id from the AppView's DID document.
- **`core/data`:** `ProfileRepository` (save, own profile, other athletes' profiles), plus a new per-session `AthleteSpaces` helper that ensures a tier space exists with a current policy.
- **Presenters and UI:** `EditProfilePresenter` and its UI get a sign-in-again prompt (new `UiError.Kind`). `ProfilePresenter` needs no change: the merged view comes from the repository.
- **AppView:**
  - Removed: `PrivateDetails.put/get`, the two routes, `ActorPrivateDetails` and the indexer's delete of it. `visibleDetails` becomes public only.
  - Changed: `ActivityStreamsViews`' training rule.
  - New: migration `V13__drop_private_details.sql`.
- **Docs:** AGENTS.md (architecture paragraph), `.agents/rules/appview.md`, `lexicons.md`, `data-and-auth.md`, BACKLOG.
- **Tests:** `ProfileRepositoryTest`, `EditProfilePresenterTest`, `ProfileDetailsTest`, `ActivityStreamsTest`, `SpaceClientTest`, plus an end-to-end check on the local stack.
