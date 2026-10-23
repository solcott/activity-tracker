# Tasks

## 1. Image and reference material

- [x] 1.1 Resolve the `ghcr.io/bluesky-social/atproto:pds-spaces-alpha` digest built on or after 2026-10-22 (`docker buildx imagetools inspect`), and pin it in `docker/pds.Dockerfile` with the date in the comment (design D8). Verify `docker compose -f docker/compose.yaml build pds` succeeds with the CSRF patch's checks intact.
- [x] 1.2 Copy the `com.atproto.space.*` lexicons the app uses (`getSpaceCredential`, `getRecord`, `listRepoOps`, `notifyWrite`, `defs`) from the new image into the session scratchpad. Generate new `createSpaceSigHeaders` vectors with the Oct 22 `@atproto/space` package. Verify the scratchpad holds each lexicon and a vector set.

## 2. Wire types (`core/lexicon`)

- [x] 2.1 Update `atproto/Space.kt` per design D6 (commit shape, `data`, the credential input, new errors) and the file's pinned-image note. Verify `SpaceSerializationTest` round-trips the new commit, including `data` bytes, and the credential input, and `./gradlew :core-lexicon:jvmTest` passes.

## 3. Credentials and presentation (`core/atproto/space`)

- [x] 3.1 Change `HttpMessageSignature` per design D3. Verify `HttpMessageSignatureTest` matches 1.2's vectors byte for byte, padded base64 included.
- [x] 3.2 Rework `SpaceCredentials` per design D1/D2: a key per credential, a body-based exchange, no `DelegationAuthenticator`. Verify `SpaceCredentialsTest`:
  - the request has no `Authorization` and its body names a P-256 `did:key`;
  - two spaces get two keys, and a refresh after expiry gets a new key;
  - `InvalidDelegationToken` (400) throws and isn't cached;
  - the existing single-flight and margin tests still pass.
- [x] 3.3 Change `DelegationTokens` to `#atproto_pds` (rename the constant). Verify `DelegationTokensTest` asserts `aud` and `kid` `#atproto`.
- [x] 3.4 Change `SpaceMember` to the new scheme and per-credential key, and add the verified `getRecord` and `listRepoOps` calls (design D4), wiring `SpaceClient.getRecord`'s member path to the verified call. Verify `SpaceClientTest` covers every scenario of "Member record reads are MAC-verified" against `SpaceFixtures` (valid, a rotated key then valid, a mismatch twice, missing, owner read without Want-Mac), and that the `JwtExpired` retry still holds.
- [x] 3.5 Update `.agents/rules/data-and-auth.md` (atproto Spaces: the key per credential, the new scheme, MAC-verified member reads, `SpaceMacException` as a failure). Verify `./gradlew :core-atproto:jvmTest :core-atproto:iosSimulatorArm64Test :core-data:jvmTest` passes.

## 4. AppView syncer

- [x] 4.1 Update `FakeSpacesPds` per design D7: KEM keys in DID docs, the body-based credential, the new commit shape with MACs, and switches to corrupt, omit or rotate. Verify the existing `SpaceSyncTest` cases pass against the new fake before the syncer changes. They should fail only on MAC absence, and the failure output shows that.
- [x] 4.2 Make `SpaceSyncer.pull` and `headRev` buffered and MAC-verified (design D5), and rename `hash` to `data` in the `notifyWrite` route. Verify `SpaceSyncTest` covers each scenario of "Pulled ops indexed only under a verified commit" (verified, wrong space or repo, a mismatch twice with backoff and nothing indexed, a rotated key) and "Delegation token audience", and that `./gradlew :server-appview:test` passes.
- [x] 4.3 Update `.agents/rules/appview.md` (syncer paragraph: buffered pulls, MAC, `#atproto_pds`) and the AGENTS.md data-flow paragraph if its wording changed. Verify `scripts/check-agent-sync.sh` passes.

## 5. Local stack and smoke scripts

- [x] 5.1 Update `docker/scripts/syncer-smoke.py` (body-based exchange, the new scheme and tag, padded base64, the openssl KEM step and MAC check on `listRepoOps`; design D8) and `syncer-smoke.sh` if its arguments change. Verify on a wiped stack (`scripts/dev.sh down` with volumes, `scripts/dev.sh --seed alice`) that `docker/scripts/spaces-smoke.sh alice` and `docker/scripts/syncer-smoke.sh alice --blob` pass.
- [x] 5.2 Update `.agents/rules/docker.md` and the `local-stack` skill: the new pin date, the wipe on bump, and a troubleshooting row for `BadSpaceMac` and for `InvalidToken` on the old scheme. Verify `scripts/check-agent-sync.sh` passes.

## 6. Integration check

- [x] 6.1 On the wiped local stack with alice and bob (bob follows alice, alice follows back), on desktop (hot-reload MCP), web, Android and iOS:
  - alice saves a Followers profile detail, posts a Followers activity, and likes and comments on a Followers activity of bob's (creating her `interactions` space);
  - bob sees alice's Followers detail on her profile, and her activity in his timeline.

  Verify each step succeeds (no `SpaceNotFound` from the Oct 15 rule), the AppView logs verified pulls, and on web the MAC header is readable (no CORS failure).
- [x] 6.2 Run `./gradlew check`. Verify it passes.

## Workflow follow-up

- Run `openspec validate spaces-alpha-2026-10-22 --strict` before archiving; archive once merged.
- Propose the Oct 29 alpha change when that release lands.
