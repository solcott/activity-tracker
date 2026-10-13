# Design

## Context

- **The PDS image.** The local PDS is built from `docker/pds.Dockerfile`, which takes `ghcr.io/bluesky-social/pds:0.4` and applies one checked `sed`: it makes the OAuth CSRF cookie non-Secure. The patch finds `csrf.js` by globbing `/app/node_modules/.pnpm/@atproto+oauth-provider@*/...`. That layout comes from the `bluesky-social/pds` distribution image. The Spaces alpha image (`ghcr.io/bluesky-social/atproto:pds-spaces-alpha`) is built from the atproto monorepo instead, so the path may differ.
- **Networking.** The relay and Jetstream share the PDS's network namespace (`network_mode: "service:pds"`), because DID docs say `localhost:2583`. That doesn't change here.
- **Where the AppView runs.** It runs on the host (`./gradlew :server-appview:run`, started by `scripts/dev.sh`). By default `scripts/dev.sh` starts a cloudflared quick tunnel. `server/appview/build.gradle.kts` then sets `APPVIEW_PUBLIC_URL` to the tunnel URL, so `AppViewConfig.serviceDid` becomes `did:web:<tunnel-host>`, and `APPVIEW_EXTRA_AUDIENCES` keeps `did:web:localhost%3A8081` working for the web app.
- **The DID document.** `wellKnownRoutes` (`api/Routes.kt`) serves the AppView's `did:web` document, which has one service today: `#activity_appview`.
- **Bulletin as the reference.** Bluesky's reference app (bluesky-social/bulletin) publishes its managing app as `did:web:<host>#bulletin`, with a service of type `AtprotoSpaceService`. Its PDS calls `GET /xrpc/com.atproto.simplespace.checkUserAccess?space=&user=&access=` with a service-auth JWT whose `iss` is the space authority and whose `lxm` is `com.atproto.simplespace.checkUserAccess`.

## Goals / Non-Goals

**Goals:**
- A pinned, reproducible, Spaces-capable PDS that keeps every client's sign-in working.
- The AppView discoverable and reachable as a managing app from the local PDS.
- Spike findings concrete enough that changes 2 to 5 (`spaces-client-protocol`, `appview-space-access`, `private-details-in-spaces`, `activity-streams-in-spaces`) can be specified without guessing.

**Non-Goals:**
- Implementing `checkUserAccess`, or any Kotlin Spaces client code. Those are changes 3 and 2.
- Running the Spaces protocol from the `permissioned-data` branch (`@atproto/dev-env start:multi-pds`). We stay on the published image.
- A multi-PDS local network. One PDS is enough for owner, member and non-member accounts.
- Keeping existing local data.

## Decisions

### D1. Use the published `pds-spaces-alpha` image, pinned by digest
`FROM ghcr.io/bluesky-social/atproto:pds-spaces-alpha@sha256:<digest>`, with the tag kept in a comment so it can be found when bumping.
- **Why:** Bluesky says the image needs no new configuration beyond the reference PDS distribution. Pinning matters because the alpha ships breaking changes on Thursdays.
- **Alternatives:**
  - Building the PDS from the `permissioned-data` branch is reproducible from a commit, but it needs a Node and pnpm monorepo build in Docker, and that branch "may diverge from published packages".
  - Community images (happyproto/pds-test) republish nightly, so they are no better pinned and add a third party.

### D2. `platform: linux/amd64` on the `pds` service
The image is amd64-only. Docker Desktop runs it under Rosetta on Apple Silicon. It applies to that one service; the relay, Jetstream and PLC stay native.
- **Alternative:** building an arm64 image ourselves. That's the same monorepo build cost as D1's rejected alternative.

### D3. Re-target the CSRF patch by searching, not by a hard-coded path
Find the file with `find /app -path '*/oauth-provider/dist/router/assets/csrf.js' -not -path '*/node_modules/*'`. In the monorepo image that is `/app/packages/oauth/oauth-provider/dist/...`, and the PDS imports it through the package's `dist` exports. Keep `sed` followed by `grep -q`, and fail if the search returns zero files or more than one. That way an unexpected layout fails the build rather than guessing. The image runs as `node`, which can't write under `/app`, so the patch step runs as `USER root` and the Dockerfile switches back to `USER node`.
- **Why:** the monorepo image's `node_modules` layout differs from the distribution image's. The `find` still pins the exact package-relative file, so the guard keeps its meaning.
- **Alternative:** dropping the patch. Rejected, because iOS sign-in would break (see `.agents/rules/docker.md`).

### D4. The managing app is the AppView's own service DID plus `#activitytracker_space`
Add a second service to the DID document: `{"id": "#activitytracker_space", "type": "AtprotoSpaceService", "serviceEndpoint": publicUrl}`. Expose it on `AppViewConfig` as a derived `managingAppService = "$serviceDid#activitytracker_space"`, so changes 3 and 4 have one source.
- **Why:** it reuses the existing `did:web` and service-auth verifier, so no second identity or key is needed. Bulletin uses the same type.
- **Alternatives:**
  - A separate `did:web` for the managing app. That means two documents and two audiences for no benefit.
  - Reusing `#activity_appview` as the managing app. That mixes two service types under one id.

### D5. Spaces require the tunnel
The PDS resolves `did:web:localhost%3A8081` to `http://localhost:8081` *inside its container*, and that is the PDS itself. With the tunnel, the AppView's DID is a public HTTPS host the container can reach. This sidesteps both did:web's HTTPS requirement and the PDS's outbound SSRF guard against private addresses. `scripts/dev.sh --no-tunnel` keeps working, prints a note that Spaces callouts won't reach the AppView, and the docs say so.
- **Alternatives:**
  - `host.docker.internal`. A did:web for that host needs plain-HTTP did:web resolution, and the PDS's SSRF guard probably blocks private IPs. That would mean patching the PDS's network guard, which is more drift surface than the relay patches.
  - Running the AppView in Docker (`--profile appview`). It still needs a did:web the PDS can resolve, so it's the same problem.

### D6. A bash smoke script with a password session; the spike is separate and throwaway
- **The smoke script.** `docker/scripts/spaces-smoke.sh <name>` follows `create-account.sh`'s pattern (curl, `post()` helper, `TEST_ACCOUNT_PASSWORD`): `createSession`, then `simplespace.createSpace` (`spaceType` `app.activitytracker.smoke`, `skey` `smoke`, `memberListPolicy` for both policies, `open` app access), then `getSpace`, `space.putRecord`, `space.getRecord` and `space.deleteRecord`. If `createSpace` reports the space already exists, it continues with the existing space, which makes the script rerunnable.
- **The spike.** It needs RFC 9421 signatures and P-256 `did:key`s, which curl can't produce. So it runs as a throwaway Node script in a scratch directory, using the `@atproto/*` alpha packages at the same snapshot bulletin pins (`0.0.0-spaces-alpha-*`). Its code is not committed. The SDK ban covers client code, and the spike is neither client code nor committed.
- **The fallback.** If the PDS refuses legacy password sessions on Spaces methods, the smoke script signs in with an app password (`com.atproto.server.createAppPassword`). If it refuses those as well, the smoke becomes a JVM test in change 2, and this change drops the script requirement.

## Spike plan and findings

Record the result of each question below (in the "Finding" lines) before closing this change.

| # | Question | How | Feeds |
|---|---|---|---|
| S1 | Do delegation-token → `getSpaceCredential` → `space.getRecord` work for a member (`memberListPolicy`, bob added with `putMember`)? | Node spike, alice and bob on the local PDS | change 2 |
| S2 | Does a `managingAppPolicy` naming `did:web:<tunnel>#activitytracker_space` make the PDS call the AppView? | Watch `.dev/appview.log` for `GET /xrpc/com.atproto.simplespace.checkUserAccess`. A 404 is the expected answer until change 3. | changes 3, D5 |
| S3 | Which `space:` scope string does the PDS's OAuth server accept next to `atproto transition:generic`, without a published permission set? | Authorize a loopback client with `space:app.activitytracker.space.athlete?action=read&action=create&action=update&action=delete&manage=create&manage=update` | change 2 |
| S4 | How does a blob attach to a space record (`repo.uploadBlob`, then reference it), and can a non-member fetch it with `com.atproto.sync.getBlob`? | Node spike | change 5 |
| S5 | Does a `publicPolicy` read still need a credential, and can a non-member get one? | Node spike | changes 4, 5 |
| S6 | Does the owner read their own space repo with only `read_self`, without a credential? | Node spike | change 4 |
| S7 | Credential TTL as issued, and the error codes on expiry and denial | Node spike | change 2 |

All findings are against the image pinned in D1, built 2026-10-01. The `@atproto` alpha packages couldn't be installed with npm: they declare `workspace:*` dependencies, and pnpm isn't on this machine. So the spike reimplements `@atproto/space`'s `http-signature.ts`, read from `/app/packages/space` in the pinned image, on Node's WebCrypto. A byte-compatible signature from hand-written code is itself evidence that change 2's Kotlin port is feasible.

- **Finding S1: member round trip works.** With bob added by `putMember {read: true}`, the flow is `getDelegationToken` on bob's PDS → `getSpaceCredential` on alice's PDS → `space.getRecord` and `space.listRecords`, all returning 200. The wire format, which change 2 must match exactly:
  - **Delegation token.** A JWT with `typ` `atproto-space-delegation+jwt`, signed with the viewer's account key (`alg` ES256K, `kid` `#atproto`). Its `iss` is the viewer, `sub` is the space URI, and `aud` is `<authority>#atproto_space_host`. It lasts 60 s and is single-use: a second exchange fails with `401 JwtReplayed`.
  - **Asking for a credential.** `Authorization: Bearer <delegation>`, plus `Signature-Input: atproto-space=("authorization");keyid="<did:key>"` and `Signature: atproto-space=:<base64 r||s>:`. The signature base is the lines `"authorization": <value>` and `"@signature-params": <input>`, joined with `\n`.
  - **Using a credential.** `Authorization: Atproto-Space <credential>`, plus `Atproto-Space-Audience: <repo owner DID>`, signed over `("authorization" "atproto-space-audience")` with no `keyid`. The key is bound by the credential's `cnf.kid`, a P-256 `did:key` (multicodec `0x1200`, compressed point).
  - **Credential.** A JWT with `typ` `atproto-space-credential+jwt`, signed by the authority's account key (ES256K, `kid` `#atproto`), with `sub` the space and no `aud`.
- **Finding S2: the managing-app callout works through the tunnel.** With `managingAppPolicy` set to `did:web:<tunnel>#activitytracker_space`, the PDS fetched `/.well-known/did.json` (200) and then `GET /xrpc/com.atproto.simplespace.checkUserAccess` (404, since the route arrives in change 3) within about 700 ms. It treated the 404 as `400 UserNotAuthorized`, so a broken managing app fails closed. No SSRF patch is needed: dev mode already sets `disableSsrfProtection`.
- **Finding S3: `transition:generic` doesn't cover Spaces, and a `space:` scope requires a resolvable space declaration.**
  - With only `atproto transition:generic`, `getDelegationToken` and `createSpace` return `403 ScopeMissingError`. The message names the exact scope needed, for example `space:<type>?authority=<did>&skey=<skey>&action=read`, and `createSpace` needs `action=read_self&manage=create`.
  - PAR accepts any `space:` string, but `/oauth/authorize` resolves the space type's lexicon and fails with `invalid_scope: Unable to retrieve space declarations` unless that NSID resolves to a `"type": "space"` declaration.
  - Locally, setting `PDS_LEXICON_AUTHORITY_DID` to an account that publishes `com.atproto.lexicon.schema/<nsid>` records makes it resolve (verified with a throwaway compose override, since reverted). The granted scope then has the declaration's `collection=` list added, consent shows "Spaces: Access permissioned spaces", and `getDelegationToken` and `createSpace` succeed. `createSpace` of a type outside the scope still gets a 403.
  - Failed lexicon lookups are cached for `LEXICON_REFRESH_FREQUENCY` (5 minutes), so publish declarations before the first sign-in that needs them.
  - Password sessions skip scope checks entirely (S1, S5 and S6 used them), so only OAuth tokens surface this.
- **Finding S4: blobs.**
  - `com.atproto.repo.uploadBlob` followed by a space `putRecord` that references the blob works.
  - The blob is served to a member by `com.atproto.space.getBlob` (with a credential) and listed by `space.listBlobs`.
  - Public `com.atproto.sync.getBlob` for the same CID returns `400 BlobNotFound`, so a blob referenced only from a space record doesn't leak through the public repo.
- **Finding S5: a public read policy still needs a credential.** An anonymous `space.getRecord` gets `401 AuthMissing`, but any signed-in user (carol, a non-member) gets a credential and reads. "Public" therefore means "any signed-in atproto user", not "anyone on the web".
- **Finding S6: owners don't need a credential for their own repo.** The owner reads their own space repo with their PDS session (OAuth needs `action=read_self`). An owner can also mint a credential for their own space.
- **Finding S7: credential lifetime and errors.**
  - Credentials last 600 s (`exp - iat`). A non-member's exchange fails with `400 UserNotAuthorized`.
  - Wrong `Atproto-Space-Audience`: `401 BadSpaceAudience`. A signature by a key other than `cnf.kid`: `401 BadSpaceSignature`. A tampered credential: `401 BadJwtSignature`. A reused delegation token: `401 JwtReplayed`.
  - Expiry wasn't waited out. The source (`credential.ts`) rejects past `exp` with clock skew of 5 s, and bulletin refreshes 5 s early on `JwtExpired` and `CredentialRevoked`.

### Consequences for later changes (task 5.5)

- **The local stack needs a lexicon authority (new, not in this change's tasks).** Before change 2 can request a `space:` scope over OAuth, the local PDS must resolve `app.activitytracker.space.athlete`. Proposed: a `lexicons.test` account created by `scripts/dev.sh`, `docker/scripts/publish-lexicons.sh` publishing the repo's `"type": "space"` declarations as `com.atproto.lexicon.schema` records, and `PDS_LEXICON_AUTHORITY_DID` set from `docker/.env`. **Decided: it opens change 2 (`spaces-client-protocol`)**, the first change to request the scope.
- **Production needs a real lexicon namespace.** Outside the local stack, the `app.activitytracker.*` NSID authority must resolve through DNS (`_lexicon` TXT), and `Nsid.NAMESPACE` is a placeholder today. That gates any shared deployment, and belongs in BACKLOG's "Before deploying anywhere shared".
- **Change 2:**
  - The scope string is `space:app.activitytracker.space.athlete?authority=*&action=read&action=read_self&action=create&action=update&action=delete&manage=create&manage=update`, added to `atproto transition:generic`. `authority=*` is needed to read other athletes' spaces.
  - Signatures are P-256 raw r||s with `did:key` binding, as above.
  - Cache credentials for about 590 s.
  - Map `UserNotAuthorized` to "no access", not to an error.
- **Change 3:** the PDS treats a non-200 from `checkUserAccess` as a denial. Answer `{authorized}` within the PDS's timeout. A denial is not an error, so return 200 with `false`.
- **Change 4:** owners read their own tier spaces with their session token (`read_self`), with no credential round trip.
- **Change 5:**
  - Stream data becomes a blob, uploaded with `repo.uploadBlob` and referenced from the space record, and read with `space.getBlob`.
  - The `public` tier still needs a signed-in viewer. That matches today's app, which is always signed in, but the `public` tier shouldn't be treated as web-readable.

## Risks / Trade-offs

- **[The alpha image changes behaviour under the same tag]** → Pin by digest (D1). Bumping is a deliberate edit that re-runs the smoke script.
- **[amd64 emulation is slow or flaky (healthcheck timing, crypto)]** → Raise the PDS healthcheck `retries` if startup exceeds 60 s, and record the observed startup time in the docker rule.
- **[Quick-tunnel hostnames change on restart, which changes the managing-app DID]** → Acceptable for alpha dev: spaces are created per run and the data is throwaway. Note it in the docker rule. A named tunnel is the fix if it starts to hurt.
- **[The PDS's SSRF guard blocks even the public tunnel]** → S2 detects it. The mitigation would be a checked `sed` like the relay's, recorded here before implementing.
- **[The new image can't read the old PDS SQLite data]** → Wipe the volumes as part of the switch (Migration Plan). The data is local and disposable.
- **[Spike packages drift from the image's protocol version]** → Use the alpha snapshot whose date is closest to the pinned digest's build date.

## Migration Plan

1. Stop the stack and wipe its volumes: `scripts/dev.sh down`, then `docker compose -f docker/compose.yaml down -v`. This drops the PDS, PLC, relay, Jetstream and AppView data together, so no DID or index is left dangling.
2. Rebuild and start: `scripts/dev.sh --seed alice`, then create `bob`.
3. Run `docker/scripts/spaces-smoke.sh alice`.
4. **Rollback:** revert the Dockerfile and compose changes and repeat step 1. Nothing outside local dev depends on this.
