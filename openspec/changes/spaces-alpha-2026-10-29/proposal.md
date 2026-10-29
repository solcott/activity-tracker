# Proposal

## Why

Bluesky's 2026-10-29 Spaces alpha removes `com.atproto.simplespace.*`, which every athlete space is created and checked through. Its replacement is one `policy` object per space, the managing-app callout moves to `com.atproto.managedspace.checkUserAccess`, and a refused credential request now answers `SpaceNotFound` instead of `UserNotAuthorized`. Against the new image, space creation and every managing-app check fail. The AppView syncer would also treat a mere refusal, such as a moment when the PDS can't reach the AppView, as a deleted space and purge it. The same release adds `clearRecords`, which empties a space repo without deleting the space. Syncers learn of a clear only by comparing the commit's `data` with a running set hash. We skipped that check for Oct 22, so a cleared space's private activities would stay indexed and served.

## What Changes

- **BREAKING. Space policies replace simplespace.** The athlete's client creates, reads and updates its spaces with `com.atproto.space.{createSpace,getSpace,updateSpace}`, sending one `policy`. Tier, `self` and `interactions` spaces use `com.atproto.managedspace.policy` naming the AppView, which is what the PDS migrates existing spaces to. `updateSpace` replaces the whole policy, so the client compares the whole policy before updating.
- **BREAKING. Managing-app callout renamed.** The AppView serves `com.atproto.managedspace.checkUserAccess` and accepts only that `lxm`. Write checks now reach it too (without `clientId`), and it keeps denying them.
- **Refusals look like missing spaces.** `SpaceNotFound` from a credential request now also means "not authorized".
  - Clients keep mapping it to not found, and keep handling the old denial errors other authorities may still send.
  - The syncer no longer drops a space on the first `SpaceNotFound`. It retries with backoff and drops the space only after it has answered `SpaceNotFound` for 24 hours. `SpaceDeleted` still drops at once.
- **Set-hash verification in the syncer.**
  - The AppView keeps each synced space's LtHash state, matching upstream `@atproto/space`.
  - It folds every pulled op into that state and checks the digest against the MAC-verified commit's `data` before applying a pull.
  - A mismatch, which is how a `clearRecords` shows, triggers a full resync that removes what the space no longer holds.
  - Backfills fold every listed record and must match the head commit before their revision is recorded.
- **Wire types** (`Space.kt`): a `SpacePolicy` open union covering `personalspace`, `memberspace`, `managedspace` and unknown members. `SimpleSpace`, `SpaceAppAccess` and `UnsupportedAppAccess` are removed. `PolicyMismatch` is added.
- **Local stack**: `pds-spaces-alpha` re-pinned to the 2026-10-29 build. The smoke and seed scripts use the new methods and policies.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `spaces/client-protocol`:
  - the owner creates and updates spaces with a single policy, compared and replaced whole;
  - a refusal answered as `SpaceNotFound` is reported as not found.
- `spaces/managing-app`: the callout is `com.atproto.managedspace.checkUserAccess`, with that `lxm`, and write checks arrive and are denied.
- `spaces/appview-syncer`:
  - `SpaceNotFound` drops a space only after it persists;
  - pulls and backfills are indexed only when the set hash matches the verified commit's `data`;
  - a cleared space is resynced and its records removed.
- `dev/local-stack`: the local PDS serves the space-policy methods instead of simplespace.

## Impact

- **`core/lexicon`:** `atproto/Space.kt` (policy union, lifecycle methods, `ManagedSpace.CheckUserAccess`, errors), `SpaceSerializationTest`.
- **`core/atproto/space`:** `SpaceClient` (create, get and update with a policy), tests and fixtures.
- **`core/data`:** `AthleteSpaces.create`, `FakeAthletePds`, profile and follow-request tests, and the presenter test fixtures that stub simplespace.
- **AppView:**
  - `api/Routes.kt` and `api/SpaceAccess.kt` (callout NSID);
  - `sync/SpaceSyncer.kt` (not-found persistence, set-hash checks);
  - new `sync/LtHash.kt` (BLAKE3 from BouncyCastle, already a dependency);
  - `db/Tables.kt` plus a Flyway migration adding `space_sync.set_hash` and `not_found_since`;
  - `FakeSpacesPds` and the space tests.
- **Docker:** `docker/pds.Dockerfile` (digest), `spaces-smoke.sh`, `moderation-smoke.sh`, `seed-effort.sh`, `syncer-smoke.py`.
- **Docs:** `AGENTS.md`, `.agents/rules/appview.md`, `.agents/rules/docker.md`, and the `local-stack` skill.
- **Data:**
  - The PDS's `006-space-policy` actor-store migration converts existing spaces to `managedspace`. Their member lists were empty, so access doesn't change.
  - Existing `space_sync` rows have no set hash, so each space is backfilled once after upgrading.
  - There's no deployed environment; the local stack can be wiped and reseeded.
