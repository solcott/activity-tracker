# Proposal

## Why

Private profile details and activity streams are moving into atproto Spaces (changes 4 and 5), and both need a client that can write to the athlete's own spaces and read other athletes' spaces as a member. No such client exists: the hard rule bans atproto SDKs in client code, so the Spaces XRPC calls, the RFC 9421 request signatures and the credential exchange are hand-written on Ktor, like the rest of `core/atproto`. The `spaces-local-stack` spike (findings S1, S3, S7) pinned down the wire format this needs, so it can be built now. It also found that a PDS grants a `space:` OAuth scope only if it can resolve the space type's lexicon declaration, so the local stack needs a lexicon authority first.

This is change 2 of 6. It adds the protocol layer only; no feature reads or writes a space yet.

## What Changes

- **Space type declaration.** `lexicons/app/activitytracker/space/athlete.json` declares the space type `app.activitytracker.space.athlete` (`"type": "space"`), with a `Nsid` constant. Moved forward from change 3, because the OAuth scope can't be granted without it. It already lists the collections changes 4 and 5 will write (`actor.privateDetails`, `feed.activityStreams`, `feed.activityZones`), because the PDS grants writes only to declared collections.
- **Local lexicon authority.** `scripts/dev.sh` creates a `lexicons.test` account, `docker/scripts/publish-lexicons.sh` publishes the repo's space declarations to it as `com.atproto.lexicon.schema` records, and the PDS gets `PDS_LEXICON_AUTHORITY_DID` pointing at it. The PDS then resolves `app.activitytracker.space.athlete` and OAuth sign-in with the `space:` scope succeeds.
- **OAuth scope.** `OAuthClientConfig.DEFAULT_SCOPE` gains `space:app.activitytracker.space.athlete?authority=*&action=read&action=read_self&action=create&action=update&action=delete&manage=create&manage=update`. That covers the loopback clients (web and desktop), the native config (`NetworkConfigs`) and the AppView-hosted client metadata. **BREAKING (local dev only):** sessions signed in before this change lack the scope, so Spaces calls on them fail with `ScopeMissingError` until the athlete signs in again.
- **Spaces wire types.** `core/lexicon/.../atproto/Space.kt` mirrors the `com.atproto.space.*` and `com.atproto.simplespace.*` methods the app uses as `XrpcQuery`/`XrpcProcedure` descriptors, with a `SpaceUri` helper for `at://{did}/space/{type}/{skey}`. Every Spaces wire type lives in this one file, so weekly alpha changes touch it and `SpaceClient` only.
- **Request signatures.** `core/atproto` gains a P-256 `SpaceBindingKey` (exposed as a `did:key`) and an `HttpMessageSignature` signer for RFC 9421 (`atproto-space` label, `ecdsa-p256-sha256`, raw r‖s).
- **Credentials.** `SpaceCredentials` obtains a space credential for a viewer: a delegation token from the viewer's PDS, exchanged on the space authority's PDS with a signed `getSpaceCredential`. It caches the credential per space until 5 s before it expires, shares one in-flight exchange between concurrent callers, and drops the entry on `JwtExpired` or `CredentialRevoked`.
- **`SpaceClient`.** One entry point for spaces:
  - Member reads (`getRecord`, `listRecords`, `getBlob`) against the repo owner's PDS, resolved through `IdentityResolver` and signed with the credential.
  - Owner reads of the signed-in athlete's own spaces with the session token (`read_self`), with no credential.
  - Writes (`simplespace.createSpace`, `getSpace`, `space.putRecord`, `space.deleteRecord`) on the athlete's PDS with the existing DPoP authenticator.
  - Denial (`UserNotAuthorized`) is a typed "no access" result, not an error.

## Capabilities

### New Capabilities
- `spaces/client-protocol`: How clients authenticate to, read from and write to atproto spaces: the OAuth scope, request signatures, credential exchange and caching, member and owner reads, writes, and how denials surface.

### Modified Capabilities
- `dev/local-stack`: The local PDS resolves the app's space-type lexicon declarations, so OAuth sign-in with the `space:` scope works on every client.

## Impact

- **Lexicons:** new `lexicons/app/activitytracker/space/athlete.json`; `Nsid.SPACE_ATHLETE`. Not indexed by the AppView.
- **`core/lexicon`:** new `atproto/Space.kt`.
- **`core/atproto`:** `AtprotoConfig.kt` (scope); new `crypto/SpaceBindingKey.kt`, `space/HttpMessageSignature.kt`, `space/SpaceCredentials.kt`, `space/SpaceClient.kt`; tests in `commonTest`.
- **`core/data`:** `SessionClients` (or a session-scoped binding) provides `SpaceClient`. No repository uses it yet.
- **`composeApp`:** `NetworkConfigs.kt` follows `DEFAULT_SCOPE`. **AppView:** `Routes.kt` client metadata follows it; its test asserts the new scope.
- **Docker and scripts:** `docker/compose.yaml` (`PDS_LEXICON_AUTHORITY_DID`), `docker/.env.example`, new `docker/scripts/publish-lexicons.sh`, `scripts/dev.sh` (lexicon account, publish, PDS restart).
- **Docs:** `.agents/rules/data-and-auth.md`, `.agents/rules/docker.md`, `.agents/rules/lexicons.md`, the `local-stack` skill, BACKLOG ("Spaces need a real lexicon namespace" points here).
- **No new dependencies.** Signing reuses cryptography-kotlin, already used by `DpopKey` on every target.
