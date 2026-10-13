# Design

## Context

- **Wire format is known.** The archived `spaces-local-stack` design (findings S1, S3, S6, S7) records the delegation-token, credential-request and credentialed-read formats, the 600 s credential lifetime and the error codes. This design relies on those findings and doesn't restate them. They hold for the PDS image pinned there (built 2026-10-01).
- **Existing client pieces.** `XrpcClient` (`core/atproto/xrpc`) talks to one service with a pluggable `RequestAuthenticator`; `OAuthSessionManager.pdsAuthenticator` does DPoP for the signed-in PDS; `ServiceAuthAuthenticator` caches tokens per NSID behind a `Mutex`. `DpopKey` is a P-256 ECDSA key on cryptography-kotlin (every target) that signs raw r‖s and exposes its uncompressed public point. `signJwt` and base64url live in `crypto/`. `IdentityResolver` (AppScope, `AtprotoBindings`) resolves a DID to its PDS.
- **Session wiring.** `SessionClients` (`core/data/di`, `SessionScope`) owns the `pds` and `appView` clients. `SessionGraph` is dropped on sign-out, so anything it owns goes with the session.
- **Scope today.** `OAuthClientConfig.DEFAULT_SCOPE = "atproto transition:generic"` is used by `loopback()` (web, desktop), `NetworkConfigs.native()` and the AppView's `/oauth/client-metadata.json` (`Routes.kt`).
- **Lexicon authority.** Finding S3: `/oauth/authorize` fails with `invalid_scope` unless the space type's NSID resolves to a `"type": "space"` declaration. Locally that needs `PDS_LEXICON_AUTHORITY_DID` naming an account that publishes `com.atproto.lexicon.schema/<nsid>` records. Failed lookups are cached for 5 minutes. The PDS reads the env var at start, but the account it names can only be created once the PDS is running.

## Goals / Non-Goals

**Goals:**
- A complete, tested Spaces client in `core/atproto` that changes 4 and 5 call without touching the wire format.
- Every Spaces wire detail confined to `atproto/Space.kt` (types) and `space/` in `core/atproto` (signing, credentials, client).
- Sign-in with the `space:` scope working on every client against the local stack.

**Non-Goals:**
- Any feature reading or writing a space (changes 4 and 5), and an outbox target for space writes (change 4).
- The AppView's `checkUserAccess` (change 3). Until it lands, member reads of a managing-app space are denied, which this change surfaces as no access.
- Persisting credentials or the binding key across app restarts.
- Recovering a session that lacks the scope automatically (see D7).
- A production lexicon namespace (BACKLOG, "Spaces need a real lexicon namespace").

## Decisions

### D1. The space-type declaration moves into this change
`lexicons/app/activitytracker/space/athlete.json` (`"type": "space"`) and `Nsid.SPACE_ATHLETE` land here, not in change 3, because the scope can't be granted without them. The declaration (`name`, `description`, `collections`, per `lexSpace` in the pinned image's `@atproto/lexicon`) already lists the collections changes 4 and 5 will write: `actor.privateDetails`, `feed.activityStreams` and `feed.activityZones`. Their record lexicons arrive with those changes. An empty list isn't an option: the PDS copies the declared collections into the granted scope at sign-in, and a `space:` scope with no collections allows no writes at all (`space-permission.ts`: "No collection means no write targets"). Declaring them now also avoids another re-consent when those changes land. `feed.activityStreams` is still a public, indexed record collection until change 5 retires it; listing the same NSID in the space is harmless meanwhile, because space records never reach the firehose. The space type isn't indexed: it stays out of `Nsid.indexedCollections`.
- **Alternative:** a throwaway declaration only in the local stack. Rejected: the declaration is part of the app's schema, and `lexicons/` is the source of truth.

### D2. Lexicon authority: a `lexicons.test` account, DID written to `docker/.env`, PDS recreated once
`scripts/dev.sh`, after the PDS is healthy:
1. creates `lexicons.test` with `create-account.sh`-style curl if `createSession` for it fails (no profile record);
2. writes its DID to `PDS_LEXICON_AUTHORITY_DID` in `docker/.env` if that differs, then `docker compose up -d pds` to recreate the container with it (relay and Jetstream share its network namespace, so they're recreated with it);
3. runs `docker/scripts/publish-lexicons.sh`, which `putRecord`s each `lexicons/**.json` whose `defs.main.type` is `space` into `com.atproto.lexicon.schema/<id>`, with `$type` `com.atproto.lexicon.schema` and `lexicon: 1`.

The account persists with the PDS volume, so later runs find the DID unchanged and skip the recreate. Publishing runs every time and is idempotent. `compose.yaml` passes `PDS_LEXICON_AUTHORITY_DID` through from `.env`, empty by default, which leaves the PDS's normal DNS resolution in place.
- **Why:** no new image or service, and the account survives restarts with the rest of the PDS data.
- **Alternatives:**
  - A fixed DID baked into `.env.example`. Impossible: `did:plc` comes from the PLC at creation.
  - Serving declarations from a fake DNS. Far more moving parts than one env var.

### D3. The binding key reuses `DpopKey`, with a new `did:key` encoding
A session holds one in-memory P-256 key for Spaces signatures, generated with `DpopKey.generate()` (a generic ES256 key despite its name) and separate from the session's DPoP key. New `crypto/DidKey.kt` encodes its public key as `did:key:z…`: compress the uncompressed point (`0x02`/`0x03` by Y's parity, then X), prefix the multicodec varint for `p256-pub` (`0x1200` → `0x80 0x24`), base58btc-encode with the `z` multibase prefix. A small `Base58.kt` sits next to `Encoding.kt`.
- **Why separate from the DPoP key:** the DPoP key is persisted and bound to OAuth tokens; mixing purposes would tie a credential leak to token replay. In memory only, because credentials are cached in memory too, so a restart re-mints both.
- **Alternatives:** renaming `DpopKey` to `P256Key` (churn across OAuth code for no behaviour change); a base58 library (one 20-line function doesn't justify a dependency on every target).

### D4. `HttpMessageSignature` signs header components only
`space/HttpMessageSignature.kt` builds the RFC 9421 signature base from covered header names in order (lower-cased, `"name": value` lines), then `"@signature-params": <params>`, joined by `\n`, and returns the `Signature-Input` and `Signature` header values with label `atproto-space`. Params are the component list plus optional `keyid`, with no `created` or `alg` unless the reference code emits them. It is checked against vectors generated once by the spike's port of `@atproto/space`'s `http-signature.ts` on the pinned image and stored in the test. Because ECDSA signatures are randomized, the test compares the signature *base* and `Signature-Input` byte-for-byte and verifies the signature with the public key.
- **Alternative:** a general RFC 9421 implementation (derived components, structured-field parsing). Not needed: Spaces covers two plain headers.

### D5. `SpaceCredentials`: a per-space cache with one in-flight exchange
Keyed by space URI, it holds `Credential(token, exp)`. `get(space)` returns a cached entry if `exp - 5 s > now`, otherwise starts or joins a `Deferred` exchange for that space (a `Mutex`-guarded map of in-flight exchanges, like `ServiceAuthAuthenticator`). The exchange:
1. `getDelegationToken(space)` on the viewer's PDS (`SessionClients.pds`, DPoP);
2. resolve the authority DID (from the space URI) to its PDS with `IdentityResolver`, cached per DID for the session;
3. `getSpaceCredential` there, with `Authorization: Bearer <delegation>` and a signature carrying `keyid = did:key`.

`exp` comes from the credential JWT's payload (decoded, not verified: the PDS verifies it). `invalidate(space)` drops an entry. Denials (`UserNotAuthorized`) are never cached, so a new follow takes effect on the next read. The cache lives in `SessionScope` and goes with the session on sign-out.
- **Not `UseSingleFlight`:** that detekt rule and `rememberSingleFlight()` are for presenters; this is a plain coroutine cache.
- **Alternative:** a `RequestAuthenticator` that retries on `JwtExpired` in `shouldRetry`. Rejected: `shouldRetry` would have to read the error body, which `toXrpcException` then reads again. `SpaceClient` instead catches `XrpcException` with `JwtExpired` or `CredentialRevoked`, invalidates and retries once.

### D6. `SpaceClient` is the only public entry point
`space/SpaceClient.kt`, built per session from the viewer's DID, the `pds` client, `SpaceCredentials`, `IdentityResolver` and the `HttpClient`:
- **Reads** (`getRecord`, `listRecords`, `getBlob`) return a sealed `SpaceRead<T>`: `Found(value)`, `NotFound`, `NoAccess`. Network and other XRPC failures throw. If the space's authority is the signed-in DID, the read goes through `pds` with DPoP (owner read, `read_self`). Otherwise it goes to the owner's PDS through an `XrpcClient` whose authenticator adds `Authorization: Atproto-Space`, `Atproto-Space-Audience` and the signature.
- **Writes** (`createSpace`, `getSpace`, `putRecord`, `deleteRecord`) go through `pds`. `createSpace` maps `SpaceAlreadyExists` to a `Created`/`AlreadyExists` result. It takes read and write policies (the policy shapes come from `Space.kt`); features pass `#managingAppPolicy` with the AppView's `managingAppService`, and the integration check (task 6.1) uses `#memberListPolicy`, because `checkUserAccess` only arrives in change 3. Member management (`putMember`) isn't part of `SpaceClient`.
- `getBlob` needs raw bytes, so `XrpcClient` gains `queryBytes(nsid, params)`.
- `SessionClients` gains `val spaces: SpaceClient`; nothing calls it yet.

### D7. One scope string, everywhere; old sessions must sign in again
`DEFAULT_SCOPE` becomes `"atproto transition:generic space:app.activitytracker.space.athlete?authority=*&action=read&action=read_self&action=create&action=update&action=delete&manage=create&manage=update"`, built from `Nsid.SPACE_ATHLETE`. `NetworkConfigs` and `Routes.kt` already reference the constant, so they follow. A session from before the change gets `403 ScopeMissingError` on Spaces calls; `SpaceClient` lets that surface as an `XrpcException` with that error. Asking the athlete to sign in again belongs with the first feature that uses spaces (change 4).
- **Why not detect and re-auth now:** nothing calls `SpaceClient` yet, and local data is wiped between these changes anyway.

### D8. Wire types mirrored from the pinned image
`core/lexicon/.../atproto/Space.kt` holds descriptors for `com.atproto.space.{getDelegationToken, getSpaceCredential, getRecord, listRecords, getBlob, putRecord, deleteRecord}` and `com.atproto.simplespace.{createSpace, getSpace}`, the policy union members it needs (`#managingAppPolicy`, `#memberListPolicy`, `#open`), and `SpaceUri` (`at://{did}/space/{type}/{skey}` with `authority`, `type`, `skey`). Field names are copied from the lexicon JSON in the pinned image (`docker run --rm --entrypoint sh <image> -c 'find / -path "*lexicons/com/atproto/s*space*"'`), not from memory. Open unions follow the `lexicons.md` conventions (unknown members decode to `Unknown`).

## Risks / Trade-offs

- **[The alpha changes the wire format on a Thursday]** → The image stays pinned; a bump re-runs `spaces-smoke.sh` and the signature vectors, and changes stay inside `Space.kt` and `space/`.
- **[Signature mismatch only shows up against a real PDS]** → Byte-level vectors from the reference code (D4), plus an end-to-end check on the local stack (alice reads bob's member-list space).
- **[Recreating the PDS container in `dev.sh` restarts relay and Jetstream]** → Only on the first run of a fresh stack, before the AppView starts; later runs see the same DID and skip it.
- **[`authority=*` asks for read access to every athlete's spaces of this type]** → It's needed to read other athletes' spaces, and each read is still gated by that space's policy. The consent screen says so.
- **[Credential revocation lags]** → An unfollow takes effect within one credential lifetime (≤ 10 minutes), as the plan accepted.
- **[The negative lexicon cache]** → A sign-in attempted before `publish-lexicons.sh` runs caches the failure for 5 minutes. `dev.sh` publishes before printing "ready"; the docker rule notes the cache.

## Migration Plan

1. Pull the change and run `scripts/dev.sh` (creates `lexicons.test`, recreates the PDS once, publishes the declaration).
2. Sign out and in again on each client to get the new scope.
3. **Rollback:** revert the change; clear `PDS_LEXICON_AUTHORITY_DID` in `docker/.env` and `docker compose up -d pds`. Sessions with the wider scope keep working.

## Resolved During Implementation

- **Credential expiry.** `getSpaceCredential`'s output is only `{credential}`, so `SpaceCredentials` reads `exp` from the credential JWT's payload.
- **Declaration shape.** The PDS validates declarations with `@atproto/lex-document`'s `lexiconSpaceSchema`, which requires `key` (`any`, `nsid`, `tid` or `literal:<x>`), even though `@atproto/lexicon`'s type marks it optional. `athlete.json` uses `"key": "any"`, since a tier's skey (`public`, `mutuals`, `self`) can't be one literal. Change 3 still validates the skey in `checkUserAccess`.
- **Denials.** Besides `UserNotAuthorized`, the lexicon lets an authority answer `NotAuthorized` (hiding whether the user or the app failed) or `AppNotAuthorized`. `SpaceRead.NoAccess` covers all three (`SpaceErrors.denials`).
