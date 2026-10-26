# Design

## Context

See proposal.md for why. What the code does today:

- **One HTTP client for everything.** `Application.kt` builds a single `HttpClient(CIO)` with WebSockets. One `IdentityResolver` uses it for the PLC directory, the configured handle resolver, Cloudflare DNS-over-HTTPS, `did:web` documents and handle `.well-known` checks. The same client fetches blobs from PDSes (`PdsBlobStreamSource`), exchanges and uses space credentials (`SpaceCredentials`, `SpaceMember`), lists Bluesky blocks (`BskyBlockBackfill`), streams Jetstream and labels, and calls Ozone. Labeler and Ozone endpoints are looked up through the same resolver, from the operator's configured DIDs.
- **Service auth.** `ServiceAuthVerifier.issuer()` already checks `aud`, `exp` and `lxm` before `signingKey()` resolves the issuer, but a missing `lxm` passes. `keyCache` remembers only keys it found, so every token naming an unresolvable DID makes a new request. `Did` checks only the `did:` prefix, so a `did:plc:` value can carry `/`, `?` or `#` into the PLC URL. `didWebUrl` accepts any host.
- **Handles.** `ResolvingHandleLookup.handleFor` returns the DID document's `alsoKnownAs` handle unverified and caches it for the life of the process. `Indexer.index()` calls it for every record and passes the handle to `ensureActor`, an `insertIgnore`, so only a new account's row takes it. A Jetstream `#identity` event writes the event's handle straight into `actors.handle`. `resolveActor` and `ActorSearch` match on `actors.handle`.
- **Client.** `IdentityResolver.resolve` verifies a typed handle against the DID document, but for a DID input it shows the document's claimed handle unverified. `OAuthClient` stores that as the session's handle.
- **Local stack.** Every PDS endpoint, the PLC directory and the AppView's own `did:web` are `localhost`, and AppView tests use made-up DIDs such as `did:plc:alice`.

## Goals / Non-Goals

**Goals:**
- One enforcement point per concern: the HTTP client for addresses, the verifier for claims, and one handle-check routine used by the indexer, the `#identity` path and the sweep.
- No behavior change on the local stack beyond setting one flag, and no rewrite of test fixtures.

**Non-Goals:**
- Guarding the phone, desktop and web clients' requests. They run on the user's device and can't reach the AppView's network.
- Bounding the existing success `keyCache` or rate-limiting service auth in general.
- Outbound protection for the segment importer, which talks only to configured endpoints.
- Fixing C10 (several `did=` TXT records). The handle check reuses `resolveHandle`, so fixing C10 later will tighten it for free.

## Decisions

### 1. Two HTTP clients: the trusted CIO client and a guarded OkHttp client

The current `HttpClient(CIO)` stays as the **trusted** client for configured endpoints: PLC, the handle resolver, Cloudflare DoH, Jetstream (WebSockets), label streams and Ozone. A new **guarded** client, `HttpClient(OkHttp)`, is used for every host learned from a DID document or handle. It's built in one factory, `GuardedHttp.create(allowPrivate: Boolean)`, in `server/appview/.../net/`.

The guard has two parts, because OkHttp doesn't call `Dns` for IP-literal hosts:
- **A `Dns` implementation** that calls `Dns.SYSTEM` and drops every address that is loopback, site-local (10/8, 172.16/12, 192.168/16), link-local (169.254/16, fe80::/10), unique-local (fc00::/7), CGNAT (100.64/10), any-local, multicast or an IPv4-mapped IPv6 form of one of these. If nothing is left, it throws `UnknownHostException`, so the call fails before any socket opens. OkHttp connects only to the addresses `Dns` returns, so the address checked is the one connected to, which closes DNS rebinding.
- **An application interceptor** that refuses a request whose URL host is an IP literal in one of those ranges, before OkHttp opens a connection.

Ktor handles redirects itself (its `HttpRedirect` plugin, with OkHttp's own redirect following turned off by the engine), so every redirect hop is a new OkHttp call that passes through both parts again.

With `allowPrivate` set, the factory returns the same OkHttp client without the filter and interceptor, so local dev exercises the same engine as production.

*Alternatives:* a custom `SocketFactory` that checks the address at `connect` would cover both cases in one place, but it's more code and was not the agreed approach. Checking the host name before the request and then letting CIO resolve it again leaves a rebinding window. Moving everything to OkHttp would also move Jetstream's WebSocket, which works today on CIO.

### 2. Two `IdentityResolver`s

`IdentityResolver` gets an optional second client, `untrusted: HttpClient = http`. It fetches `did:web` documents and handle `.well-known` checks with `untrusted`, and uses `http` for the PLC directory, the configured handle resolver and DoH. Client apps pass one client, so nothing changes for them.

The AppView builds two resolvers:
- `identity = IdentityResolver(trusted, plcUrl, handleResolverUrl, untrusted = guarded)`, used by the service-auth verifier, handle checks, PDS lookups and space credentials.
- `configuredIdentity = IdentityResolver(trusted, plcUrl, handleResolverUrl)`, used only to resolve the operator's configured labeler and Ozone DIDs (`startLabelSubscribers`, `OzoneClient`).

Callers that fetch from a PDS endpoint take the guarded client: `PdsBlobStreamSource`, `SpaceCredentials` and `SpaceMember` (built for the syncer and `MemberSpaceBlobs`), and `BskyBlockBackfill`. `ResolvingPdsLookup` only resolves documents, through `identity`. `BlobRedirects` sends the viewer a redirect and makes no fetch itself.

### 3. DID syntax checked at the resolver

`IdentityResolver.resolveDid` checks syntax before it builds a URL and throws `IdentityException` otherwise:
- `did:plc:` followed by one or more of `[a-z2-7]`. That alphabet can't change the URL's path or query. Requiring exactly 24 characters adds no safety and would break the hundreds of `did:plc:alice`-style test DIDs.
- `did:web:` followed by a host name of letters, digits, `-` and `.` in valid labels, optionally `%3A<port>`, with no further `:` segments.
- Any other method is refused, as it already is.

The verifier also rejects an `iss` that fails this check as `Bad iss`, so a malformed issuer never reaches a resolver. The check lives in `core/atproto` next to `didWebUrl`, so the client benefits too. The `Did` type stays as it is: tightening its constructor would ripple through every lexicon decode.

*Alternative:* validate in `Did`'s `init`. That rejects malformed DIDs at parse time, but records with odd DIDs would then fail to decode as a whole instead of being refused only where they are resolved.

### 4. Service auth: required `lxm` and a failure cache

`issuer()` changes `lxm != null && lxm != nsid` to `lxm != nsid`, with the message "Token not valid for $nsid". Every route already passes its method NSID to `verify` (`Routes.kt`), so the managing-app and syncer routes follow with no route change.

`signingKey()` records a failed resolution in a small LRU (`FailedLookups`, capped at about 10,000 entries, each kept for 5 minutes, behind a lock). A DID found in it is refused straight away with "No signing key". A success clears its entry. The forced re-resolve after a signature mismatch also skips DIDs in the cache. The cap keeps an attacker who sends random DIDs from growing memory without bound.

### 5. Handle checks: one routine, a due time and three outcomes

A new `HandleVerifier` (replacing `ResolvingHandleLookup`) returns a sealed result for a DID:
- `Verified(handle)`: the DID document claims a handle and `resolveHandle(claimed)` returns the same DID.
- `Invalid`: no claimed handle, the claimed handle resolves to another DID, or `resolveHandle` throws `HandleNotFoundException`.
- `Unreachable`: the DID document couldn't be fetched, or `resolveHandle` rethrew a transport failure (no lookup answered).

A new nullable column, `actors.handle_check_due_at` (epoch ms, Flyway `V39`), says when an account is next checked:
- After `Verified` or `Invalid`: now + 24 h, and the handle is written (`handle.invalid` for `Invalid`).
- After `Unreachable`: now + 1 h, and the stored handle is left alone.

How each trigger uses it:
- **First sight.** `Indexer.index()` asks the verifier only when the in-memory cache has no answer for the author. The cache holds the last `Verified`/`Invalid` handle per DID. For a new account, `ensureActor` inserts the result (`handle.invalid` when `Unreachable`) along with its due time. For an existing row the insert is ignored, so a failed check never overwrites a verified handle. The cache stops repeat checks within the process.
- **`#identity` events.** The handle in the event is ignored, and the account is re-checked through the verifier. The cache entry is updated or dropped to match, and the row is updated as above. An event for an account with no row changes nothing.
- **Sweep.** `HandleSweep`, a loop shaped like `BskyBlockBackfill`, runs every 10 minutes. It takes up to 200 actors whose `handle_check_due_at` is null or past, oldest first, and checks them one at a time. The migration leaves existing rows null, so they are all checked soon after deploy.

*Alternative:* a boolean `handle_verified` plus a timestamp. A single due time folds in the retry back-off, and `handle.invalid` already marks "not verified", so no separate flag is needed.

### 6. Lookups by handle

Every stored handle is now either verified or `handle.invalid`. `resolveActor` returns nothing when the requested handle is `handle.invalid`. `ActorSearch` leaves `handle.invalid` rows out of its handle matches, both the exact match and the trigram or prefix match, while still finding those accounts by display name or DID.

### 7. Client DID sign-in

In `IdentityResolver.resolve`, the DID-input branch calls `resolveHandle(claimed)`. It keeps the claimed handle only when the result equals the DID. Any mismatch, `HandleNotFoundException` or transport failure gives `INVALID_HANDLE`. A handle the user typed keeps its existing check.

### 8. Configuration

`AppViewConfig` gains `allowPrivateAddresses` from `APPVIEW_ALLOW_PRIVATE_ADDRESSES` (`true`/`false`, default `false`). `main` logs a warning when it is on. The `:server-appview:run` task sets it to `true` unless the environment already sets it, the same way it sets `APPVIEW_EXTRA_AUDIENCES`, and `scripts/dev.sh` starts the AppView through that task, so it gets the flag too. The local-stack skill and README mention the flag.

## Risks / Trade-offs

- **Third-party callers without `lxm` get 401.** → The pinned PDS sets it on every callout, and so do our clients, the importer and the scripts. The 401 message names the method, so a caller can tell what's missing.
- **Stored handles stay unverified between deploy and the first sweep.** → The migration makes every existing row due at once. The sweep covers 200 rows every 10 minutes, and the only data that exists today is the local stack's.
- **A handle check costs up to four requests** (the DID document, the handle resolver, DoH, `.well-known`) where it used to cost one. → It runs once per account per process at first sight, on `#identity` events, and once a day per account in the sweep. The well-known check goes through the guarded client.
- **A handle that only `.well-known` can verify, hosted on a private address, is `handle.invalid` in production.** → That's the intended outcome: no public resolver could verify it either.
- **The OkHttp engine follows Ktor redirects differently from CIO** (timeouts, connection pool). → Only the guarded paths move. Blob and space-sync tests already run against `MockEngine` or `FakeSpacesPds`. The end-to-end tests below run a real OkHttp client against a loopback server with the flag off.
- **DNS-over-HTTPS is still a public resolver for `_atproto` TXT lookups.** It's a fixed, configured endpoint, so it stays on the trusted client.
- **The failure cache can refuse a DID that recovers within 5 minutes.** → The cost is a short wait after an outage. A success clears its entry at once.

## Migration Plan

1. Deploy with `APPVIEW_ALLOW_PRIVATE_ADDRESSES` unset in production. Local dev picks it up from the Gradle task.
2. Flyway `V39` adds the nullable `handle_check_due_at` column. No backfill is needed, since null means "due now".
3. Rollback: revert the deploy. The extra column is ignored by the old code, and the old code accepts tokens without `lxm` and stops verifying handles, so it keeps working. Handles the sweep wrote as `handle.invalid` stay that way until the old code's next `#identity` event, which is acceptable.
