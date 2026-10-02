# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Kotlin Multiplatform, Strava-style athlete activity tracker backed by ATProtocol. Clients target Android, iOS, and Web (wasmJs) and share a Compose Multiplatform UI. The backend is our own AppView (Ktor + Postgres). User data lives in each user's PDS. See README.md for local setup.

## Commands

```bash
./gradlew check                                   # all tests on all targets + Android lint + ktfmt + detekt + sorted deps
./gradlew ktfmtFormat sortDependencies            # format Kotlin/scripts (ktfmt kotlinlang style) and sort dependency blocks
./gradlew detektBaseline                          # regenerate each module's detekt-baseline.xml (config: config/detekt/detekt.yml)
./gradlew :core-atproto:jvmTest --tests "*.OAuthFlowTest"   # single test: fastest on the jvm target
./gradlew :core-lexicon:iosSimulatorArm64Test     # iOS tests run on the simulator
./gradlew :server-appview:test                    # AppView tests (H2 in PostgreSQL mode, no Docker needed)

./gradlew :webApp:wasmJsBrowserDevelopmentRun     # web app; PORT env overrides 8080
./gradlew :androidApp:installDebug
./gradlew :server-appview:run                     # AppView on :8081; defaults target the docker stack
docker compose -f docker/compose.yaml up -d       # local PLC/PDS/relay/Jetstream/Postgres
docker/scripts/create-account.sh alice            # alice.test on the local PDS
```

iOS: open `iosApp/iosApp.xcodeproj`, or run `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`. Its build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`.

Wasm tests are disabled. Client modules also have a `jvm()` target so tests (Room in-memory, Ktor `MockEngine`, `circuit-test`) run on the JVM.

## Architecture

**Data flow.** Clients write records straight to the user's PDS with `putRecord`, using DPoP-bound OAuth tokens. The AppView never writes to repos. It tails Jetstream (`network.bsky.jetstream.subscribeEvents`, the v2 wire), filtered to `Nsid.indexedCollections`, indexes records into Postgres, and serves `app.activitytracker.*` queries. Clients call the AppView directly and authenticate with short-lived service-auth JWTs, which the PDS mints via `com.atproto.server.getServiceAuth` (see `ServiceAuthAuthenticator`). The AppView verifies these JWTs against the issuer's DID-doc key (`ServiceAuthVerifier`, which supports ES256K and ES256 via BouncyCastle).

**Lexicons.** `lexicons/**.json` is the schema source of truth. `core/lexicon` hand-mirrors it as `@Serializable` models plus typed `XrpcQuery`/`XrpcProcedure` descriptors, which the client (`XrpcClient`) and the AppView routes (`xrpcQuery` in `Routes.kt`) both use. When you change a lexicon, update the JSON, the Kotlin model, and the AppView indexer/views together. The NSID namespace `app.activitytracker` is a placeholder. In Kotlin it is defined only in `Nsid.NAMESPACE`, but the lexicon files and `create-account.sh` also contain it.

Lexicon rules that shape the code:
- Lexicons have no floats. Distances are integer meters and speeds are integer mm/s.
- Records must carry `$type`. Models default it, and `AtJson` sets `encodeDefaults = true`. Serialize lexicon data only with `AtJson`.
- All repo records are public. Never put private data in a record.

**Writes go through the outbox.** `ActivityRepository` enqueues each write into the Room `outbox` table with a client-generated TID rkey, so retries are idempotent `putRecord`s. `OutboxProcessor.drain()` applies writes in order and stops at the first failure. GPX track blobs are uploaded first, and their `BlobRef` is spliced into the record. The UI updates optimistically through `FeedRepository.updateCached`. The AppView catches up asynchronously via Jetstream.

**DI (Metro) and scopes.**
- `AppScope` holds app-wide singletons: the HTTP client, OAuth, Room, `ActivityRecorder`, and `AuthCoordinator`.
- `SessionScope` belongs to one signed-in account. The `SessionGraph` graph extension is created with the `OAuthSession` and dropped on sign-out.
- `App.kt` switches between the two. Signed out, it renders `LoginScreen` on `AppCircuit`. Signed in, it renders a `key(did)` `SessionGraph` whose `SessionCircuit` hosts the navigable screens.
- The real `@DependencyGraph`s extend the common `AppGraph`: `AndroidAppGraph` and `IosAppGraph` in `composeApp` platform source sets, and `WebAppGraph` in the `webApp` module (the web app shell, like `androidApp`/`iosApp`). A module that declares a graph must have every contributing module on its compile classpath, which is why `webApp` depends on `core:data` directly. Platform pieces (DB builder, `LocationTracker`, `AuthBrowser`) are contributed from each module's platform source sets.

**Adding a screen.** Declare a `@CircuitSerializable(<scope>::class) data object/class XScreen : Screen`, plus a State, an Event sealed interface, a Presenter, and a UI. Use the same scope as the screen's `@CircuitInject`. Navigation state is saved with kotlinx.serialization: Metro generates a `CircuitSerializerRegistration` for each screen, and `CircuitBindings.kt` passes that scope's set to `SerializableCircuitSaver`. An unregistered screen fails at runtime when it is saved.
- The presenter is a `@CircuitInject(XScreen::class, <scope>::class)` `@Composable` function returning the State. Circuit supplies `screen`/`navigator` params, and Metro injects the rest. Don't use a presenter class with `@AssistedInject`; that's only needed for classes, and Metro rejects a class taking `Screen`/`Navigator` without it. A class with plain `@Inject` (like `LoginPresenter`) is fine when it needs neither.
- The UI is a `@CircuitInject` composable function.
- Metro generates the Circuit factories (`metro.enableCircuitCodegen=true` in gradle.properties). There is no Circuit KSP.

**OAuth.** `core/atproto/oauth` is a full ATProto OAuth public client: handle → DID → PDS → authorization server, then PAR + PKCE + DPoP with nonce retry, then refresh.
- Pending authorizations are persisted before the browser opens, because the web redirect unloads the page. Platforms feed the redirect back to `AuthCoordinator.handleRedirect`: Android through the `MainActivity` intent filter, iOS through the `ASWebAuthenticationSession` callback, and web through URL params in `webApp`'s `main.kt`.
- Web uses the loopback client ID. Loopback redirects must use `127.0.0.1`, so the web app redirects from `localhost`.
- Native uses AppView-hosted `/oauth/client-metadata.json`, which needs an HTTPS tunnel in dev. Its URL is the single `activitytracker.appviewPublicUrl` property (`local.properties`); the `activitytracker.native.oauth` convention plugin derives the redirect scheme from it for `composeApp` (generated `GeneratedNativeNetworkConfig`), the Android manifest, and the AppView `run` task. Physical iPhones can't reach the localhost PDS. See README.

## Gotchas

- **Room 3** uses the `androidx.room3` packages and KSP only. DAOs must be `suspend` or return `Flow`. The `room3 {}` schema dir is `core/data/schemas`. On the web, Room runs through `web/sqlite-worker` (SQLite WASM on OPFS), which needs the COOP/COEP headers set in `webApp/webpack.config.d/devserver.js`.
- **No Ozone or other atproto SDK in client code.** Ozone has no wasmJs target. The XRPC layer is hand-written on Ktor in `core/atproto`.
- **iOS test binaries** need Swift libraries because cryptography-kotlin uses CryptoKit. The `dev.whyoleg.cryptography` plugin, applied in the `activitytracker.kmp.library` convention, handles this.
- **Convention plugins** live in `build-logic/`. Client modules apply `activitytracker.kmp.library` or `activitytracker.kmp.compose` (`androidApp` applies `activitytracker.android.application`), and the Android namespace is derived from the module path. Every module gets ktfmt, detekt and sort-dependencies from `activitytracker.quality` (the KMP conventions apply it; other modules apply it directly); there is no `subprojects {}` block. Plugin behaviour is covered by TestKit tests in `build-logic/convention/src/test`. `androidApp` is a separate application module (AGP 9 KMP split), so it can't use Metro intrinsics. Graph creation is exposed from `composeApp` instead (`createAndroidAppGraph`).
- **Local stack.** Data flows PDS → relay (indigo) → Jetstream → AppView. Current Jetstream bootstraps from a relay's `listHosts`/`listRepos`, so it can't read a PDS directly. The PDS serves HTTP only when `PDS_HOSTNAME=localhost`, so the relay and Jetstream share the PDS container's network namespace. Both are built from pinned commits with one-line patches that relax their SSRF guards for `localhost` (`docker/relay.Dockerfile`, `docker/jetstream.Dockerfile`). Bumping a pin means re-checking its `sed` target. Public requestCrawl rejects `localhost`, so the `relay-crawl` one-shot registers the PDS via the relay admin API. The AppView SQL (`server/appview/src/main/resources/db/migration`) is kept portable so it runs on both Postgres and H2.
