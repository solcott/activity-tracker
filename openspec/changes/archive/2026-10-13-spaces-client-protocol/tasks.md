# Tasks

## 1. Space-type declaration and local lexicon authority

- [x] 1.1 Find the space-type lexicon examples and the `com.atproto.space.*` / `com.atproto.simplespace.*` lexicon JSON in the pinned PDS image (design D1, D8) and copy them to the session scratchpad for reference. Verify the scratchpad holds a `"type": "space"` example and every method listed in D8.
- [x] 1.2 Add `lexicons/app/activitytracker/space/athlete.json` (`"type": "space"`, listing the `actor.privateDetails`, `feed.activityStreams` and `feed.activityZones` collections) and `Nsid.SPACE_ATHLETE`, kept out of `indexedCollections`, following the `lexicon-change` skill. Update `.agents/rules/lexicons.md` with where space declarations live and that they aren't indexed. Verify `./gradlew :core-lexicon:jvmTest` passes and the `lexicon-auditor` agent reports no mismatch.
- [x] 1.3 Write `docker/scripts/publish-lexicons.sh` (design D2): sign in as `lexicons.test`, `putRecord` each `lexicons/**.json` whose main def is `space` into `com.atproto.lexicon.schema/<id>`. Verify it runs twice in a row without error and `com.atproto.repo.getRecord` returns the declaration.
- [x] 1.4 Pass `PDS_LEXICON_AUTHORITY_DID` from `docker/.env` in `docker/compose.yaml` (empty default in `.env.example`), and make `scripts/dev.sh` create `lexicons.test` if missing, write its DID to `.env`, recreate `pds` only when the DID changed, and run `publish-lexicons.sh` before reporting ready. Verify on a wiped stack that the first run recreates the PDS and a second run doesn't, and that `scripts/dev.sh status` is unaffected.
- [x] 1.5 Update `.agents/rules/docker.md`, the `local-stack` skill (lexicon authority, the 5-minute negative cache, a troubleshooting row for `invalid_scope: Unable to retrieve space declarations`) and BACKLOG's "Spaces need a real lexicon namespace" item. Verify `scripts/check-agent-sync.sh` passes.

## 2. Wire types (`core/lexicon`)

- [x] 2.1 Add `atproto/Space.kt`: `SpaceUri`, the descriptors and policy members from design D8, with field names taken from the image's lexicon JSON (1.1). Settle the open question on credential expiry here. Verify with a `core-lexicon` test that `SpaceUri` parses and formats `at://{did}/space/{type}/{skey}` and rejects other URIs, and that each input round-trips through `AtJson` with `$type` on union members.

## 3. Signing (`core/atproto/crypto`, `space`)

- [x] 3.1 Add `Base58.kt` and `DidKey.kt` (design D3): P-256 point compression, `0x1200` multicodec, base58btc `z` prefix. Verify with a test against P-256 `did:key` vectors (generated with `@atproto/crypto` in the pinned image, odd and even Y) that encoding matches.
- [x] 3.2 Generate signature vectors once in the scratchpad with the spike's port of `@atproto/space`'s `http-signature.ts` from the pinned image: a fixed key, a credential request (`authorization`, with `keyid`) and a credentialed read (`authorization` + `atproto-space-audience`). Verify the scratchpad has the signature base, `Signature-Input` and a valid `Signature` for each.
- [x] 3.3 Implement `space/HttpMessageSignature.kt` (design D4). Verify a `commonTest` test matches both vectors' signature base and `Signature-Input` byte for byte and verifies its own `Signature` with the public key, then run `./gradlew :core-atproto:jvmTest` and `:core-atproto:iosSimulatorArm64Test`.

## 4. Credentials and client (`core/atproto/space`)

- [x] 4.1 Add `XrpcClient.queryBytes` for blob downloads. Verify with a `MockEngine` test in `XrpcClientTest` that bytes and the content type come back and a non-2xx throws `XrpcException`.
- [x] 4.2 Implement `SpaceCredentials` (design D5). Verify with `MockEngine` tests: one exchange for repeated reads; one exchange for concurrent first reads; refresh within 5 s of `exp` (test `Clock`); `invalidate` forces a new exchange; `UserNotAuthorized` isn't cached; the exchange goes to the authority's PDS from its DID document, with `Bearer` and a signature whose `keyid` is the session's `did:key`.
- [x] 4.3 Implement `SpaceClient` reads (design D6): `SpaceRead` `Found`/`NotFound`/`NoAccess`, owner reads through the session PDS with no credential, member reads with `Atproto-Space` + audience + signature, one retry after `JwtExpired`/`CredentialRevoked`. Verify with `MockEngine` tests covering each spec scenario under "Credential reuse", "Denied access is a result", "Owner reads" and "Member reads", including a network failure that throws.
- [x] 4.4 Implement `SpaceClient` writes: `createSpace` (caller-supplied read and write policies, `#open` app access, `AlreadyExists` result), `getSpace`, `putRecord`, `deleteRecord`, all over DPoP. Verify with `MockEngine` tests of the request bodies and the `SpaceAlreadyExists` mapping.
- [x] 4.5 Add `spaces: SpaceClient` to `SessionClients`, with the binding key and credential cache scoped to the session. Update `.agents/rules/data-and-auth.md` with the Spaces client (where wire types live, owner vs member reads, `SpaceRead`). Verify `./gradlew :core-data:jvmTest` passes and the Metro graph compiles (`./gradlew :composeApp:compileKotlinJvm`).

## 5. OAuth scope

- [x] 5.1 Change `OAuthClientConfig.DEFAULT_SCOPE` (design D7), built from `Nsid.SPACE_ATHLETE`. Update the scope in `OAuthFlowTest`, and extend the AppView client-metadata test to assert the new scope. Verify `./gradlew :core-atproto:jvmTest :server-appview:test` passes.
- [x] 5.2 On the local stack, sign out and in on desktop (hot-reload MCP), web, Android and iOS. Verify each consent screen lists Spaces access, sign-in completes, and the feed still loads.

## 6. Integration check

- [x] 6.1 With a throwaway JVM test or scratch program (not committed) using `SpaceClient` against the local stack: alice (OAuth session from 5.2) creates a member-list space of type `app.activitytracker.space.athlete`, adds bob with `putMember`, puts an `actor.privateDetails` record; bob reads it as a member; carol gets `NoAccess`; alice reads it as owner with no exchange. Verify all four outcomes, and that `com.atproto.repo.listRecords` on alice's public repo doesn't show the record.
- [x] 6.2 Run `./gradlew check` and `docker/scripts/spaces-smoke.sh alice`. Verify both pass.

## Workflow follow-up

- Run `openspec validate spaces-client-protocol --strict` before archiving.
- Archive with `/opsx:archive` once merged, then propose `appview-space-access` (change 3), which no longer adds the space-type lexicon (it moved here).
