# Proposal

## Why

The local PDS is pinned to the Spaces alpha built on 2026-10-01. Bluesky's 2026-10-22 alpha is the largest breaking release so far, and probably the last one that size before beta at the end of November. It changes how credentials are requested and presented, where tokens are addressed, and how space reads are authenticated: HMACs keyed through a new `#atproto_kem` DID key replace commit signatures. Against the new image, every member read and every AppView sync fails. Smaller alphas (Oct 29 first) build on this one, so we move now.

This is change 2 of 2 for the Oct 22 alpha. It depends on `spaces-mac-crypto`, which adds the key agreement and MAC checks it wires in. No backward compatibility: the local environment is wiped, and nothing keeps the old formats working.

## What Changes

- **BREAKING. Local PDS bumped** to the `pds-spaces-alpha` digest built on 2026-10-22. The local stack is wiped and reseeded, so every account gets a `#atproto_kem` key from creation. The CSRF patch is re-checked against the new image.
- **BREAKING. Credential requests carry everything in the body.** `getSpaceCredential` becomes a JSON POST of `{space, credentialKid, delegationToken}`, with no `Authorization` header and no signature. Each credential is bound to a fresh P-256 key named as `credentialKid`, instead of one key for the whole session.
- **BREAKING. Credential presentation.**
  - The scheme is `Authorization: Atproto-Space-Credential <cred>`.
  - The signature label is `atproto-space-credential`, with `;tag="atproto-space-credential"`.
  - The signature is in padded base64.
  - The signer signs with the credential's own key.
- **BREAKING. Delegation tokens** that the AppView signs are addressed to `<authority>#atproto_pds` (was `#atproto_space_host`).
- **MAC-verified reads.**
  - Member `getRecord` (clients) asks for a record MAC with a fresh key share, and returns a record only when the MAC checks out.
  - The syncer's `listRepoOps` (AppView) asks for a repo MAC on every page, and moves its indexed revision only past a verified commit.
  - A mismatch refreshes the author's DID document and retries once with a new key.
- **Wire types** (`Space.kt`):
  - `ListRepoOps.Commit` becomes `{ver, repo, space, rev, data}`.
  - `NotifyWrite.Input.hash` becomes `data`.
  - `GetSpaceCredential.Input` gains `credentialKid` and `delegationToken`.
  - `SpaceErrors` gains `BadSpaceMac`, `InvalidToken`, `BadSpaceSignature` and `BadSpaceAudience`.
- **Smoke scripts** (`spaces-smoke.sh`, `syncer-smoke.py`) speak the new protocol, including a MAC-verified credentialed read.
- **Oct 15 lifecycle rule** (a space must exist before its authority writes to it): checked on the new image for every space the app writes. That includes `interactions` for likes, comments, hides and blocks, besides the tiers.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `spaces/client-protocol`:
  - credential requests are unsigned and carry their key in the body, one fresh key per credential;
  - credentialed reads use the new scheme and signature label;
  - member record reads must pass a MAC check.
- `spaces/appview-syncer`:
  - the AppView's delegation tokens are addressed to the authority's PDS service;
  - pulled ops are indexed only under a MAC-verified commit.

## Impact

- **`core/atproto/space`:** `SpaceCredentials` (body request, a key per credential, no `DelegationAuthenticator`), `SpaceMember` (new scheme, per-credential key, Want-Mac and verify for `getRecord`, retry on mismatch), `HttpMessageSignature` (label, tag, padding; the `keyid` variant removed), `DelegationTokens` (`#atproto_pds`), `SpaceClient` (MAC failures surface as errors).
- **`core/lexicon`:** `atproto/Space.kt` (commit shape, renames, errors).
- **AppView:** `sync/SpaceSyncer.kt` (buffered, MAC-verified pulls), the `notifyWrite` route (field rename), `FakeSpacesPds` and the space tests.
- **Docker:** `docker/pds.Dockerfile` (digest), `docker/scripts/spaces-smoke.sh`, `docker/scripts/syncer-smoke.py`/`.sh`.
- **Docs:** `.agents/rules/data-and-auth.md`, `.agents/rules/appview.md`, `.agents/rules/docker.md`, the AGENTS.md data-flow paragraph, and the `local-stack` skill.
- **Data:** local Postgres, PDS and PLC volumes are wiped. There's no deployed environment, so nothing to migrate.
