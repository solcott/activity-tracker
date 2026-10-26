# Tasks

## 1. Service auth requires `lxm`

- [x] 1.1 In `ServiceAuthVerifier.issuer()`, reject a missing `lxm` the same as a wrong one (`lxm != nsid`). Update the `ServiceAuthVerifierTest` cases that mint tokens with `lxm = null` so they pass the route's NSID, and add cases for a missing `lxm` and for another method's `lxm`. Verify with `./gradlew :server-appview:test --tests "*.ServiceAuthVerifierTest"`.
- [x] 1.2 Add `AppViewTest`-style end-to-end cases: a correctly signed token with no `lxm` gets `401` on an `app.activitytracker.*` route, on `checkUserAccess` (in `SpaceAccessTest`) and on a `notifyWrite` callout (in `SpaceSyncTest`). Check that no other AppView test mints a token without `lxm`. Verify with `./gradlew :server-appview:test`.

## 2. DID syntax and the failure cache

- [x] 2.1 In `core/atproto`, add a DID syntax check (did:plc: `[a-z2-7]+`; did:web: host labels with an optional `%3A<port>` and no path; other methods refused) and call it in `IdentityResolver.resolveDid` before building a URL. Add `HandleResolutionTest` (or a new `DidSyntaxTest`) cases for a did:plc with `/`, `?` or `#`, a did:web with a path, a did:key, and the valid local-stack forms (`did:web:localhost%3A8081`, `did:plc:alice`), asserting no request reaches the `MockEngine` for the invalid ones. Verify with `./gradlew :core-atproto:jvmTest --tests "*.HandleResolutionTest"` (and the new class).
- [x] 2.2 Make `ServiceAuthVerifier.issuer()` reject an `iss` that fails the syntax check as `Bad iss` before any key lookup, and add a test whose `keys` lambda fails if it is called. Verify with `./gradlew :server-appview:test --tests "*.ServiceAuthVerifierTest"`.
- [x] 2.3 Add the bounded failed-lookup cache (about 10,000 entries, 5-minute expiry, cleared on success, used by the forced re-resolve too) to `ServiceAuthVerifier`, using the injected `Clock`. Test that repeated tokens naming an unresolvable DID call `keys` once within the window and again after it, and that a later success is accepted. Verify with `./gradlew :server-appview:test --tests "*.ServiceAuthVerifierTest"`.

## 3. Guarded outbound client

- [x] 3.1 Add `ktor-client-okhttp` to `gradle/libs.versions.toml` and as an `implementation` dependency of `server/appview` only. Run `./gradlew format` to keep the dependency blocks sorted, and verify `./gradlew :server-appview:compileKotlin` succeeds.
- [x] 3.2 Add `APPVIEW_ALLOW_PRIVATE_ADDRESSES` (default `false`) to `AppViewConfig.fromEnv`, with `AppViewConfigTest` cases for unset, `true` and `false`. Verify with `./gradlew :server-appview:test --tests "*.AppViewConfigTest"`.
- [x] 3.3 Add `server/appview/.../net/GuardedHttp.kt`: an OkHttp-engine `HttpClient` factory with the private-address `Dns` filter (loopback, site-local, link-local, unique-local, CGNAT, any-local, multicast, and IPv4-mapped forms) and the IP-literal interceptor, both left out when `allowPrivate` is set. Test it against a local Ktor test server on `127.0.0.1` and on `localhost`: refused with the flag off, before any request reaches the server (including after a redirect from an allowed host to a loopback one, if a public-looking host can be faked through the `Dns`), and served with the flag on. Unit-test the address classifier on IPv4 and IPv6 samples. Verify with `./gradlew :server-appview:test --tests "*.GuardedHttpTest"`.
- [x] 3.4 Give `IdentityResolver` an optional `untrusted: HttpClient = http`, used for `did:web` documents and the handle `.well-known` lookup only. Add a `core/atproto` test with two `MockEngine`s showing which client each lookup uses. Verify with `./gradlew :core-atproto:jvmTest`.
- [x] 3.5 Wire `Application.kt`: keep the CIO client as the trusted one; build the guarded client from the config flag; build `identity` (trusted + guarded) and `configuredIdentity` (trusted only, for `startLabelSubscribers` and `OzoneClient`); pass the guarded client to `PdsBlobStreamSource`, `SpaceCredentials`, `SpaceMember` and `BskyBlockBackfill`; log a warning at startup when private addresses are allowed. Verify with `./gradlew :server-appview:test` and by starting the AppView against the local stack (`scripts/dev.sh`) and checking `.dev/appview.log` for the warning and a working `getProfile` for `alice.test`.
- [x] 3.6 Make `:server-appview:run` set `APPVIEW_ALLOW_PRIVATE_ADDRESSES=true` unless the environment sets it (next to `APPVIEW_EXTRA_AUDIENCES` in `server/appview/build.gradle.kts`), and document the flag in the local-stack skill (`.agents/skills/local-stack/SKILL.md`) and README. Verify `scripts/dev.sh status` still reports a healthy AppView and `scripts/check-agent-sync.sh` passes.
- [x] 3.7 Add an end-to-end `AppViewTest` case with the flag off: a token whose `iss` is `did:web:127.0.0.1%3A<port>`, pointing at a test server that records requests, gets `401`, and the server receives nothing. Verify with `./gradlew :server-appview:test`.

## 4. Verified handles in the AppView

- [x] 4.1 Add Flyway `V39__actor_handle_check.sql` with a nullable `handle_check_due_at` (bigint) on `actors`, H2- and Postgres-portable, and the column in `Tables.kt`. Verify with `./gradlew :server-appview:test --tests "*.MigrationVersionsTest"`.
- [x] 4.2 Replace `ResolvingHandleLookup` with `HandleVerifier` returning `Verified`/`Invalid`/`Unreachable` (design decision 5), keeping a `fun interface` seam so existing tests' fakes still compile. Unit-test each outcome with a `MockEngine` resolver: matching handle, a handle resolving to another DID, a handle not found, no claimed handle, DID document unreachable, and every handle lookup unreachable. Verify with `./gradlew :server-appview:test --tests "*.HandleVerifierTest"`.
- [x] 4.3 Use it in the `Indexer`: at first sight, insert the result and its due time through `ensureActor` (`handle.invalid` and +1 h when unreachable); on a `#identity` event, ignore the event's handle and re-check, updating the row only on `Verified`/`Invalid` and setting the due time either way. Add `AppViewTest` cases: an impersonating DID is shown and stored as `handle.invalid`; an `#identity` event naming someone else's handle doesn't change the stored handle; an unreachable re-check keeps the last verified handle; a new account whose check is unreachable is `handle.invalid`. Verify with `./gradlew :server-appview:test`.
- [x] 4.4 Add `HandleSweep` (shaped like `BskyBlockBackfill`: every 10 minutes, up to 200 due actors, oldest first), start it in `Application.kt`, and test with an injected `Clock` that a due account is re-checked and updated, a not-yet-due one isn't, and an unreachable result pushes the due time out an hour. Verify with `./gradlew :server-appview:test --tests "*.HandleSweepTest"`.
- [x] 4.5 Make `resolveActor` find no one for `handle.invalid`, and leave `handle.invalid` rows out of `ActorSearch`'s handle matches (exact and fuzzy) while keeping display-name and DID matches. Add `getProfile` and `searchActors` tests for the impersonation case, a query for `handle.invalid`, and a lookup by DID of an account whose handle is invalid. Verify with `./gradlew :server-appview:test`.
- [x] 4.6 Update the CLAUDE.md architecture paragraph where it describes how the AppView learns handles and verifies service auth, if it says anything now untrue, and verify with the `rules-check-drift` skill or a manual read.

## 5. Client DID sign-in

- [x] 5.1 In `IdentityResolver.resolve`, verify a DID input's claimed handle with `resolveHandle` and fall back to `handle.invalid` on a mismatch, not-found or transport failure, without failing sign-in. Add `core/atproto` tests for each case and confirm `OAuthFlowTest` still passes. Verify with `./gradlew :core-atproto:jvmTest`.

## 6. Integration

- [x] 6.1 Run `./gradlew check` and confirm it passes (tests on all targets, detekt, ktfmt, lint, sorted dependencies).
- [x] 6.2 On the local stack (`scripts/dev.sh --seed alice`), confirm sign-in with `alice.test` on the web app, `getProfile` for `alice.test`, a photo or stream fetch, a Followers-tier read through the managing app, and `docker/scripts/spaces-smoke.sh alice` and `docker/scripts/moderation-smoke.sh` all still pass.
- [x] 6.3 Update `docs/reviews/2026-10-23-codebase-review.md` to remove P2, P3 and P6 and note the fixing commit, leaving the file uncommitted.

## Workflow follow-up

- Commit to `main` in small commits by concern (service auth, outbound guard, handle verification, client sign-in) when asked.
- Archive the change with `/opsx:archive` once the tasks are done.
