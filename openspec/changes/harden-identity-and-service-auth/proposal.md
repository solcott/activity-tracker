# Proposal

## Why

The 2026-10-23 codebase review found three related holes in how the AppView trusts identities. It shows any handle a DID document claims, so a did:plc that lists `alice.bsky.social` in `alsoKnownAs` appears as Alice and is found under her handle (P2). It accepts a service-auth token with no `lxm` on every method that shares its audience (P3). And it resolves the `iss` of any bearer token before checking the signature, fetching whatever host a `did:web` names, IP literals and internal addresses included, so one forged token makes it send a request inside its own network (P6). All three are fixed before more people sign in to the AppView.

## What Changes

- **Handles are verified both ways.** The AppView stores a DID document's claimed handle only after that handle resolves back to the same DID. It checks the first time it sees an account, on every Jetstream `#identity` event, and in a background sweep when an account's last check is more than 24 hours old. A handle that doesn't match, or that nothing resolves, is stored as `handle.invalid`. When the lookup can't be reached, the AppView keeps the last verified handle; an account it has never verified gets `handle.invalid` until a check succeeds. Looking up an actor by handle matches only verified handles.
- **The client verifies the handle when someone signs in with a DID.** `IdentityResolver.resolve` already checks a handle typed at sign-in; it now also resolves the DID document's claimed handle back when the input was a DID, and shows `handle.invalid` when it doesn't point to that DID.
- **BREAKING: `lxm` is required.** Every AppView service-auth verifier (its own routes, the managing-app endpoint and the syncer callouts) rejects a token whose `lxm` is missing or isn't the route's method NSID. Our clients, the importer, the scripts and the pinned PDS's `checkUserAccess`, `notifyWrite` and `notifySpaceDeleted` callouts all set it already, so nothing we run breaks; a third-party caller that omits it now gets `401`.
- **Outbound requests to addresses from DID documents or handles are guarded.** Every AppView request to a host it learned from a DID or handle (did:web documents, PDS endpoints for blobs, space credentials and sync, the Bluesky block backfill, handle `.well-known` checks) goes through an HTTP client that refuses private, loopback and link-local addresses at connect time, after DNS resolution, so DNS rebinding can't get around it. DIDs are checked for valid syntax before any request, and failed DID resolutions are cached briefly so a flood of tokens naming one bad DID makes one request, not one per token. Endpoints the operator configures (PLC, handle resolver, Jetstream, labelers, Ozone) use a separate trusted client. A config flag allows private addresses for the local Docker stack, where everything is `localhost`.

## Capabilities

### New Capabilities
- `auth/service-auth`: How the AppView verifies inter-service JWTs: claims checked (audience, expiry, required `lxm`), signature verified against the issuer's DID-document key, issuer resolution limited to valid DIDs and cached on failure.
- `identity/handle-verification`: Which handle the AppView and clients show for an account: bidirectional verification, when it is re-checked, what happens when lookups fail, and that handle lookups match only verified handles.
- `identity/outbound-requests`: Which hosts the AppView may contact for addresses it learns from DID documents and handles, the trusted client for configured endpoints, and the local-development exception.

### Modified Capabilities
- `spaces/managing-app`: "Callouts authenticated as the space's authority" changes from "`lxm`, if present, is `com.atproto.simplespace.checkUserAccess`" to requiring it.

## Impact

- **AppView** (`server/appview`): `ServiceAuthVerifier` (required `lxm`, failure cache), `HandleLookup`/`ResolvingHandleLookup` and the `Indexer`'s identity path (verification, stored check time), a background re-check loop, `ViewQueries.resolveActor`, `Application.kt` (two HTTP clients), `AppViewConfig` (the private-address flag), a new Flyway migration adding the handle check time to `actors`. Every DID-doc endpoint user (`BlobRedirects`, `ActivityStreamsViews`, `BskyBlockBackfill`, `SpaceSyncer`'s credentials and member client) moves to the guarded client.
- **core/atproto**: `IdentityResolver` verifies a DID sign-in's handle, validates DID syntax before building a URL, and can take a separate client for addresses from DID documents and handles. `Did` syntax validation lands in `core/lexicon` or alongside the resolver.
- **Dependencies**: `ktor-client-okhttp` in the AppView only. Client apps are unchanged.
- **Dev tooling**: `:server-appview:run` and `scripts/dev.sh` set the private-address flag; the local-stack skill notes it.
- **Specs**: `spaces/managing-app` changes one requirement; three new capabilities.
