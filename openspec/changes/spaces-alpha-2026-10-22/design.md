# Design

## Context

- **The source.** Bluesky's guide `alpha-migrations/prompt-10-22-26.md` (atproto `permissioned-data` branch), sections 1–4, 6 and 7. The Oct 15 notes are in discourse thread 1129, post 6. Our layer already matches the Oct 1 guide (`prompt-10-1-26.md`): split read and write policies, `putMember`, `spaceType`, `repoRev`, HTTP message signatures.
- **Today's flow.** `SpaceCredentials` exchanges a delegation token at the authority's PDS through `DelegationAuthenticator`: `Bearer` plus a signature with `keyid`, made with one `bindingKey()` per session (or per AppView process). `SpaceMember.call` sends `Atproto-Space <cred>` plus the audience and a signature made with that same key, and retries once on `JwtExpired`/`CredentialRevoked`. `SpaceSyncer.pull` applies each `listRepoOps` page as it arrives, then sets `rev` from the ops and `page.commit.rev`. `headRev` pages with `excludeValues` until the commit. Backfill uses `listRecords`, which carries no MAC in the protocol.
- **Prerequisite.** `spaces-mac-crypto` provides `encapsulate`, `SpaceMac.forCommit`/`forRecord`, the header codecs, `p256PublicKey`, `Cid.bytes()`, `#atproto_kem` lookup and `XrpcClient.queryWithHeaders`.

## Goals / Non-Goals

**Goals:**
- Everything the app does against spaces works on the Oct 22 image: own writes, member reads on every client, AppView sync, `checkUserAccess`.
- Every Spaces wire detail stays in `Space.kt` and `core/atproto/space/`. AppView changes are limited to `SpaceSyncer`, its route and its test fake.

**Non-Goals:**
- Compatibility with the Oct 1 image, or with credentials, checkpoints or DID docs created under it.
- Token-less reads of public spaces, STAR `getRepo`, value-to-CID checks and set-hash comparison (see `spaces-mac-crypto` non-goals).
- `notifySpaceDeleted` and the `describeServer` spaces flag. These are optional follow-ups proposed separately, if wanted.

## Decisions

### D1. A credential owns its key
`Credential(token, expiresAt, key)`. `exchange()` generates a key (`newKey()`), sends its `did:key` as `credentialKid` in the body together with the delegation token, and stores the key with the credential. `bindingKey()` goes away, and `SpaceMember` signs with `credential.key`. A refresh after expiry or revocation makes a new key, as the guide asks. The cache, the single-flight exchange and the 5 s margin are unchanged.
- **Alternative:** keep one session key. Rejected: the Oct 1 guide already asked for a fresh key, and the per-space cache makes per-credential keys free.

### D2. The exchange is an ordinary unauthenticated procedure
`XrpcClient(http, authorityPds, NoAuth)` with `GetSpaceCredential.Input(space, credentialKid, delegationToken)`. `DelegationAuthenticator` is deleted. `InvalidDelegationToken` now comes back as 400, and like any other refusal it is thrown and never cached. `SpaceNotFound` and `SpaceDeleted` keep their current mapping.

### D3. Presentation constants change in one place
`HttpMessageSignature`:
- the label becomes `atproto-space-credential`;
- the params are `("authorization" "atproto-space-audience");tag="atproto-space-credential"`;
- base64 becomes `Base64.Default` with padding;
- `sign(key, authorization, audience)` loses the nullable audience and the `keyid` branch.

`SpaceMember` writes `Atproto-Space-Credential <cred>`. New vectors come from `createSpaceSigHeaders` in the Oct 22 package.

### D4. MACs only where the protocol requires them, inside `SpaceMember`
`SpaceMember` gains `getRecordVerified(space, repo, collection, rkey)` and `listRepoOpsVerified(...)`. Each:
1. resolves the repo owner's `#atproto_kem` (cached with the DID document in `IdentityResolver`);
2. builds a verifier, sends `Atproto-Space-Want-Mac`, and checks `Atproto-Space-Mac` on the response;
3. on `Mismatch`, `Missing` or `Malformed`, re-resolves the DID document (bypassing the cache), retries once with a fresh verifier, and then throws `SpaceMacException`.

Other calls (`listRecords`, `getBlob`, `registerNotify`) stay as they are, because the host attaches no MAC to them. `SpaceClient.getRecord` uses the verified call for member reads only. A `SpaceMacException` isn't an `XrpcException`, so `read()` lets it propagate as a failure, and the `SpaceRead` mapping is unchanged. Owner reads over OAuth send no Want-Mac.
- **Alternative:** a `RequestAuthenticator` that adds Want-Mac to every request. Rejected: the verifier needs the response and the parsed body, which authenticators never see, and a MAC is per-method.

### D5. The syncer buffers a pull and commits it only after the MAC
`pull` collects every page's ops into memory until a page carries `commit`. On that page the commit MAC is checked, and `commit.space`/`commit.repo` must equal the requested space and authority. Only then are the ops applied in order and `commit.rev` returned as the new revision. If the MAC fails twice, the sync throws, `failed()` backs off, and nothing from the pull is indexed. Pulls are small (15-minute reconcile, a pull per notification), so buffering is cheap. A pull that ends without a commit (an empty log after `since`) keeps `since`.

`headRev` asks for the MAC the same way, and its pages need the header even though only the last page carries a commit. Every page gets a fresh key share, retries included (guide §4b).
- **Alternative:** apply each page as it arrives. Rejected: an unverified tail would be indexed before the check that would reject it.

### D6. Wire types change in place
- `ListRepoOps.Commit(ver, repo, space, rev, data)`, with `data` as `{"$bytes": …}` decoded to bytes.
- `NotifyWrite.Input.data` replaces `hash` (still unread).
- `GetSpaceCredential.Input(space, credentialKid, delegationToken, clientAttestation?)`.
- `SpaceErrors` gains `BAD_SPACE_MAC`, `INVALID_TOKEN`, `BAD_SPACE_SIGNATURE` and `BAD_SPACE_AUDIENCE`. None is a denial: they mean our side is wrong, so they throw.

### D7. Test fake speaks the new protocol
`FakeSpacesPds` publishes a P-256 `#atproto_kem` key per athlete in its DID documents. It answers `getSpaceCredential` from the body (checking `credentialKid` is a P-256 `did:key`), and answers `listRepoOps` with the new commit shape and an `Atproto-Space-Mac` computed author-side (JDK `KeyAgreement` ECDH plus the `spaces-mac-crypto` HKDF and MAC helpers). Switches let tests corrupt a MAC, omit it, or rotate an athlete's KEM key. Client tests (`SpaceFixtures`, `SpaceClientTest`) get the same for `getRecord`.

### D8. Local stack: bump, wipe, re-smoke
- Resolve the current `pds-spaces-alpha` digest (2026-10-22 build), pin it, and re-check the CSRF `sed` (the build fails if it drifted).
- `scripts/dev.sh down` with volumes, then `scripts/dev.sh --seed alice`, so every account is created with `#atproto_kem` and the AppView starts with no `space_sync` rows.
- `spaces-smoke.sh` (owner-only) needs no MAC, only a re-run.
- `syncer-smoke.py` moves to the body-based exchange and the new scheme, and does the KEM step with `openssl pkeyutl -derive` and `openssl kdf HKDF`, so it stays dependency-free.
- The Oct 15 rule is checked by an end-to-end pass that writes to every space the app uses.

## Risks / Trade-offs

- [Browsers can't read `Atproto-Space-Mac` unless the PDS exposes it] → checked on the web client in the integration task. If it's missing, member reads fail on web only. We then report it upstream, and web shows the failure as an ordinary error.
- [The Oct 29 alpha changes some of this again] → wire details stay in `Space.kt` and `space/`, and the smoke scripts catch drift. Oct 29 gets its own small change.
- [Buffering a pull holds every op of a long gap in memory] → a gap over an hour already falls back to `backfill`, which pages `listRecords` without buffering, so a buffered pull covers at most an hour of writes to one space.
- [`listRecords` backfill isn't MAC-covered] → as the protocol defines it. The revision recorded after a backfill comes from the MAC-verified `headRev`.

## Migration Plan

Merge `spaces-mac-crypto` first. Then this change: bump, wipe, reseed, and sign in again on every client, since sessions don't survive the wipe. Rollback means reverting the commit and the digest, and wiping again.
