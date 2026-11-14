# Proposal

## Why

Bluesky's 2026-11-12 Spaces alpha renames the credential exchange (`getSpaceCredential` becomes `authorizeSpaceSync`) and the managing-app callout (`checkUserAccess` becomes `checkAccess`, answering `read` and `write` at once), and replaces syncer notifications outright: `registerNotify`, `notifyWrite` and `notifySpaceDeleted` are gone, and a syncer now registers by passing an endpoint and its own token in each `authorizeSpaceSync`, then receives one `notifySpaceUpdate` with no service auth. Read MACs move to RFC 9421 HTTP message signatures with `hmac-sha256`, keyed by HPKE secret export instead of ECDH plus HKDF. Against the new image every member read, every managing-app check and every syncer registration fails, so no non-public activity is readable or synced.

## What Changes

- **BREAKING. Credential exchange renamed.** Clients and the AppView call `com.atproto.space.authorizeSpaceSync` with `credentialKey` (was `credentialKid`); its output adds `spaceRev`. Refusals answer `AccountNotAuthorized` (was `UserNotAuthorized`); `NotSpaceOwner` folds into `NotAuthorized`, and `BadSpaceMac` becomes `InvalidSpaceMac`. `RegistrationNotConfirmed` and `UpstreamFailure` are new. `createSpace` and `getSpace` answer `space` instead of `uri`.
- **BREAKING. Managing-app callout renamed.** The AppView serves `com.atproto.managedspace.checkAccess`, with that `lxm`, taking `space`, an optional `repo` (was `user`) and `clientId`, with no `access`, and answers `{read, write}`. A check with no `repo` (a request without a delegation token) reads nothing. `write` is always false.
- **BREAKING. Syncer registration through authorization.**
  - Each `authorizeSpaceSync` the AppView makes carries `notify = {endpoint, token}`: its public URL and a 32-byte random token per space, stored in `space_sync.notify_token` and kept across reauthorizations.
  - The AppView serves `com.atproto.space.confirmRegistration` (echoing the challenge) and `com.atproto.space.notifySpaceUpdate` with no service auth, checking the token instead; any other is `400 InvalidToken`.
  - A registration lives only as long as its credential (10 minutes), so the syncer reconciles every 5 minutes and reauthorizes within 6 minutes of expiry.
  - `#repoWrite` and `#repoRemoved` for the authority's repo make a space due. `#spaceDeleted` and `#registrationExpired` make it due and force reauthorization. Only an authorization answered `SpaceDeleted` drops a space.
  - The `#activitytracker_space_syncer` DID-document service and its service-auth audience are removed.
- **BREAKING. Read MACs are HTTP message signatures.** Readers send `Accept-Signature` naming an HPKE key share and the `atproto-space-commit` or `atproto-space-record` profile. Hosts answer `Atproto-Space-Commit` or `Atproto-Space-Record` with `Signature-Input` and `Signature`. The verifier checks the signature over the header re-serialized as an RFC 8941 dictionary, and that the header matches the body and the request.
- **`listRepoOps`** carries `commit` on every page and answers `RepoNotFound` for a never-written repo. `listRecords` loses `reverse`.
- **Space type declaration**: `name` becomes `title` in `lexicons/app/activitytracker/space/athlete.json`.
- **Local stack**: `pds-spaces-alpha` re-pinned to the 2026-11-12 build. The smoke scripts use the new methods, registration and MACs.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `spaces/client-protocol`:
  - credentials come from `authorizeSpaceSync` with `credentialKey` and no `notify`;
  - `AccountNotAuthorized` is a denial;
  - record MACs are RFC 9421 `hmac-sha256` signatures keyed by HPKE secret export;
  - a deleted space is dropped by the AppView once its authorization confirms it.
- `spaces/managing-app`: the callout is `com.atproto.managedspace.checkAccess`, with that `lxm`, answering `read` and `write`, and a check with no account reads nothing.
- `spaces/appview-syncer`:
  - registration goes through `authorizeSpaceSync`'s `notify`, with a stored token, and is renewed by reauthorizing;
  - `notifySpaceUpdate` and `confirmRegistration` are authenticated by that token, not service auth;
  - a deletion notice only prompts a sync, and only `SpaceDeleted` drops a space;
  - reconciles every 5 minutes;
  - commit MACs are HTTP message signatures, and every `listRepoOps` page carries the commit;
  - the syncer DID-document service is removed.
- `auth/service-auth`: the syncer's host callbacks are no longer service-auth endpoints.
- `dev/local-stack`: the local PDS serves the 2026-11-12 alpha.
- `moderation/account-status`, `moderation/labels`: closed tier spaces answer `checkAccess` with `read: false`.

## Impact

- **`core/lexicon`:** `atproto/Space.kt` (`AuthorizeSpaceSync` with `Notify`, `ConfirmRegistration`, `NotifySpaceUpdate` and the `SpaceUpdate` open union, `ManagedSpace.CheckAccess`, `listRepoOps` commit on every page, renamed and new errors), `SpaceSerializationTest`.
- **`core/atproto/space`:** `SpaceCredentials` (`authorizeSpaceSync`, an optional `notify` callback, `Credential.spaceRev`), `SpaceMember` (`authorize`, `Accept-Signature`), `SpaceMac` (HTTP message signatures), `SpaceKem` (HPKE secret export), `HttpMessageSignature`, new `StructuredFields` (replacing `StructuredDictionary`), `SpaceMacHost` (test fakes), `SpaceClient` (`space` output), and their tests and regenerated `SpaceMacVectors`.
- **`core/data` and presenters:** `FakeAthletePds` and the test fixtures that stub the renamed methods.
- **AppView:**
  - `api/Routes.kt` (`checkAccess`; `confirmRegistration` and `notifySpaceUpdate` through `unauthenticatedProcedure`; the syncer DID-document entry removed) and `api/SpaceAccess.kt`;
  - `sync/SpaceSyncer.kt` (reauthorization cadence, `notified(space, reauthorize)`, `deleted()` removed) and new `sync/SyncRegistrations.kt`;
  - `AppViewConfig` (`syncerService`, `SYNCER_FRAGMENT` removed), `Application.kt`;
  - `db/Tables.kt` plus Flyway `V61__space_sync_notify_token.sql`;
  - `FakeSpacesPds` and the space tests.
- **Lexicons:** `lexicons/app/activitytracker/space/athlete.json` (`title`).
- **Docker:** `docker/pds.Dockerfile` (digest), `spaces-smoke.sh`, `syncer-smoke.py`, `syncer-smoke.sh`, and `scripts/dev.sh` wording.
- **Docs:** `AGENTS.md`, `.agents/rules/appview.md`, `.agents/rules/data-and-auth.md`, `.agents/rules/docker.md`, and the `local-stack` skill.
- **Data:**
  - `space_sync.notify_token` starts null and is filled on each space's next authorization. Existing `registered_until` values lapse within minutes and are renewed by reauthorizing.
  - Registrations made with `registerNotify` on the old image don't carry over; there's no deployed environment, and the local stack is wiped and reseeded.
