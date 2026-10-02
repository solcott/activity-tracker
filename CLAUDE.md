# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Kotlin Multiplatform, Strava-style athlete activity tracker backed by ATProtocol. Clients target Android, iOS, and Web (wasmJs) and share a Compose Multiplatform UI. The backend is our own AppView (Ktor + Postgres). User data lives in each user's PDS. See README.md for local setup.

## Commands

```bash
./gradlew check                                   # all tests on all targets + Android lint + ktfmt + detekt + sorted deps
./gradlew format                                  # format Kotlin/scripts (ktfmt kotlinlang style) and sort dependency blocks
./gradlew detektBaseline                          # regenerate each module's detekt-baseline.xml (config: config/detekt/detekt.yml)
./gradlew :core-atproto:jvmTest --tests "*.OAuthFlowTest"   # single test: fastest on the jvm target
./gradlew :core-lexicon:iosSimulatorArm64Test     # iOS tests run on the simulator
./gradlew :server-appview:test                    # AppView tests (H2 in PostgreSQL mode, no Docker needed)
./gradlew -p build-logic :convention:test         # convention plugin TestKit tests

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

**Lexicons.** `lexicons/**.json` is the schema source of truth. `core/lexicon` hand-mirrors it as `@Serializable` models plus typed `XrpcQuery`/`XrpcProcedure` descriptors, which both the client (`XrpcClient`) and the AppView routes use. A lexicon change updates the JSON, the Kotlin model, and the AppView indexer/views together. Lexicons have no floats, so units are integers. Records carry `$type`. Serialize lexicon data only with `AtJson`. All repo records are public, so never put private data in a record.

**Writes go through the outbox.** `ActivityRepository` enqueues each write into a Room outbox with a client-generated TID rkey. `OutboxProcessor` drains it with idempotent `putRecord`s. The UI updates optimistically, and the AppView catches up via Jetstream.

**DI (Metro) and scopes.** `AppScope` holds app-wide singletons (HTTP, OAuth, Room, `ActivityRecorder`, `AuthCoordinator`). `SessionScope` is one signed-in account: a `SessionGraph` graph extension created with the `OAuthSession` and dropped on sign-out. `App.kt` renders `LoginScreen` on `AppCircuit` when signed out and a `key(did)` `SessionGraph`'s `SessionCircuit` when signed in. Platform pieces (DB builder, `LocationTracker`, `AuthBrowser`) are contributed from each module's platform source sets.

**Hard rule:** no Ozone or other atproto SDK in client code. Ozone has no wasmJs target. The XRPC layer is hand-written on Ktor in `core/atproto`. A hook enforces this.

## Where the details live

Area rules in `.claude/rules/` load when you work on matching paths:

| Rule | Covers |
|---|---|
| `lexicons.md` | Lexicon ↔ Kotlin mirror conventions, `Nsid`, open enums, optional fields |
| `appview.md` | Indexer idempotency, untrusted input, Flyway migrations, H2-portable SQL, routes |
| `compose-screens.md` | Circuit screen anatomy, scopes, Metro graphs, presenter tests |
| `data-and-auth.md` | Room 3, outbox, XRPC client, OAuth flow and native client config |
| `build-logic.md` | Convention plugins, quality tooling, toolchain, TestKit |
| `docker.md` | Local PDS → relay → Jetstream stack, pinned and patched images |

Skills: `new-screen` (scaffold a Circuit screen), `lexicon-change` (add or change a record or query across every layer), `local-stack` (bring up, seed, smoke-test and debug the Docker network).

Agents: `gradle-runner` (run Gradle tasks and return a verdict, not a log) and `lexicon-auditor` (read-only cross-layer consistency check after a lexicon change).

Hooks (`.claude/settings.json`, scripts in `.claude/hooks/`) block hand edits to generated files, ask before editing a committed migration, block Ozone in client code, and remind you which counterparts to update after a lexicon-layer edit.
