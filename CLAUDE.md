# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Kotlin Multiplatform, Strava-style athlete activity tracker backed by ATProtocol. Clients target Android, iOS, Web (wasmJs) and Desktop (JVM) and share a Compose Multiplatform UI. GPS recording is phone-only: desktop and web offer manual entry only. The backend is our own AppView (Ktor + Postgres). User data lives in each user's PDS. See README.md for local setup.

## Commands

```bash
./gradlew check                                   # all tests on all targets + Android lint + ktfmt + detekt + sorted deps
./gradlew format                                  # format Kotlin/scripts (ktfmt kotlinlang style) and sort dependency blocks
./gradlew composeStabilityCheck                   # fail on unstable Compose params (and State fields in :app-screens); reports in build/compose/reports
./gradlew detektBaseline                          # regenerate each module's detekt-baseline.xml (config: config/detekt/detekt.yml)
./gradlew :core-atproto:jvmTest --tests "*.OAuthFlowTest"   # single test: fastest on the jvm target
./gradlew :core-lexicon:iosSimulatorArm64Test     # iOS tests run on the simulator
./gradlew :server-appview:test                    # AppView tests (H2 in PostgreSQL mode, no Docker needed)
./gradlew -p build-logic :convention:test         # convention plugin TestKit tests
./gradlew :detekt-rules:test                      # the repo's own detekt rules (UseTextFieldState, UseSingleFlight)

./gradlew :webApp:wasmJsBrowserDevelopmentRun     # web app; PORT env overrides 8080
./gradlew :desktopApp:hotRun --auto               # desktop app with Compose Hot Reload (reloads on save); :desktopApp:run without
./gradlew :androidApp:installDebug
./gradlew :server-appview:run                     # AppView on :8081; defaults target the docker stack
scripts/dev.sh [--seed alice]                     # whole local env: Docker, tunnel, local.properties, AppView, adb reverse (idempotent)
scripts/dev.sh status | down
docker compose -f docker/compose.yaml up -d       # local PLC/PDS/relay/Jetstream/Postgres
docker/scripts/create-account.sh alice            # alice.test on the local PDS
```

iOS: open `iosApp/iosApp.xcodeproj`, or run `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGN_IDENTITY=- build`. The ad-hoc signature ("Sign to Run Locally") needs no team, and the app needs it: an unsigned build (`CODE_SIGNING_ALLOWED=NO`) can't use the Keychain, so sign-in fails with `-34018`. Its build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`.

Wasm tests are disabled. Client modules also have a `jvm()` target so tests (Room in-memory, Ktor `MockEngine`, `circuit-test`) run on the JVM.

## Architecture

**Data flow.** Clients write records straight to the user's PDS with `putRecord`, using DPoP-bound OAuth tokens. The AppView never writes to repos. It tails Jetstream (`network.bsky.jetstream.subscribeEvents`, the v2 wire), filtered to `Nsid.indexedCollections`, indexes records into Postgres, and serves `app.activitytracker.*` queries. The one thing it stores rather than indexes is the profile details an athlete marks Mutuals or Only me: clients write those with the `actor.putPrivateDetails` procedure, and `getProfile` shows each one only to the viewers its visibility allows (`PrivateDetails.kt`). Clients call the AppView directly and authenticate with short-lived service-auth JWTs, which the PDS mints via `com.atproto.server.getServiceAuth` (see `ServiceAuthAuthenticator`). The AppView verifies these JWTs against the issuer's DID-doc key (`ServiceAuthVerifier`, which supports ES256K and ES256 via BouncyCastle).

**Lexicons.** `lexicons/**.json` is the schema source of truth. `core/lexicon` hand-mirrors it as `@Serializable` models plus typed `XrpcQuery`/`XrpcProcedure` descriptors, which both the client (`XrpcClient`) and the AppView routes use. A lexicon change updates the JSON, the Kotlin model, and the AppView indexer/views together. Lexicons have no floats, so units are integers. Records carry `$type`. Serialize lexicon data only with `AtJson`. All repo records are public, so never put private data in a record: a non-public profile detail goes to the AppView private store instead (see `BACKLOG.md` for the planned move to atproto Spaces).

**Writes go through the outbox.** `ActivityRepository` enqueues each write into a Room outbox with a client-generated TID rkey. `OutboxProcessor` drains it with idempotent `putRecord`s. The UI updates optimistically, and the AppView catches up via Jetstream.

**Screens span three modules.** `app/screens` (`:app-screens`) holds each feature's `Screen`, `State` and `Event`. `app/presenters` (`:app-presenters`) holds the presenters and has the Compose runtime, no UI (foundation comes through only for `TextFieldState`: text fields' state lives in `State`, owned by the presenter). `composeApp` holds the UIs. Presenters and UIs both depend on the contracts and never on each other. `composeApp` and `webApp` list `:app-presenters` only so their Metro graphs aggregate its factories.

**DI (Metro) and scopes.** `AppScope` holds app-wide singletons (HTTP, OAuth, Room, `ActivityRecorder`, `AuthCoordinator`). `SessionScope` is one signed-in account: a `SessionGraph` graph extension created with the `OAuthSession` and dropped on sign-out. `App.kt` renders `LoginScreen` on `AppCircuit` when signed out and a `key(did)` `SessionGraph`'s `SessionCircuit` when signed in. Platform pieces (DB builder, `LocationTracker`, `AuthBrowser`) are contributed from each module's platform source sets. Desktop and web bind `UnsupportedLocationTracker` (`nonMobileMain`), whose `isSupported = false` hides Record in the feed.

**Theme and adaptive UI.** Each platform follows its own conventions in one shared UI. `ui/theme` has the brand (teal, amber `tertiary` for kudos) as full Material 3 schemes. `AppTheme` adapts it by `LocalUiPlatform`: Material 3 on Android; iOS grouped surfaces, Apple text styles and dimming instead of ripples on iOS; Inter (bundled in `nonMobileMain/composeResources`) and a denser scale on desktop and web. Screens build from `ui/adaptive` (`AppTopBar`, `AppButton`/`AppSecondaryButton`/`AppTextButton`/`AppIconButton`, `AppTextField`, `AppSegmentedControl`, `AppMenu`, `AppBottomSheet`, `AppDatePickerDialog`, `PlatformIcon`, `ReadableList`), never the Material widgets those wrap; detekt's `UseAdaptiveComponents` enforces this. The wrappers use Calf (`calf-ui`) where UIKit has a native piece: menus, sheets, the date picker and the tab bar. The iOS navigation bar is drawn in Compose, because Calf's native one is pinned to the window and can't follow screen transitions or swipe-back. Signed in, `nav/AppShell` wraps the navigable screens. It shows a native tab bar on iOS, a bottom bar or rail on Android, and a sidebar on wide desktop and web windows. Each `TopLevelDestination` keeps its own back stack (`resetRoot` with save/restore). `App` tells each screen through `LocalIsNavRoot` whether it is a tab root, which drops its back button and gives it a large title on iOS.

**Activity files.** `core/activityfile` (`:core-activityfile`) reads FIT, GPX, TCX and Shearwater dive (XML) files, optionally gzipped, into an `ImportedActivity`, which `ActivityDraft.fromImport` publishes like a recording (GPX track blob). `ActivityFileReader` is the entry point: it picks the format from the content, not the file name. GPX, TCX and Shearwater XML parse in common code with xmlutil's pull parser (`walkXml` matches local names, so namespace prefixes don't matter). Shearwater times have no zone: they're read in the device's zone (`TimeZones.device()`), `ImportedActivity.localStart` keeps the clock, and the import screen warns and lets the athlete pick the dive's zone (`inZone`; web bundles `@js-joda/timezone` for the zone rules). A dive's depths, gases, CNS, tanks and site come through as `ImportedDive`, from Shearwater files and FIT dive logs alike, which only the import screen shows so far. Each FIT decoder passes its SDK's dive messages through as `FitDiveMessages`, and `FitDives` (common) picks the session's summary and the gases breathed. FIT uses Garmin's own SDK on each platform behind the `FitDecoder` interface: the Java SDK on Android, desktop and the AppView (`commonJvmMain`), the npm JS SDK on web (`@JsModule` externals), and the Swift SDK on iOS. Kotlin/Native can't call Swift, so `iosApp/iosApp/SwiftFitDecoder.swift` implements the interface and `ContentView` passes it into `IosAppGraph.Factory`. `gunzip` is expect/actual over each platform's built-in inflater. Every format decodes to a `DecodedActivity`, and `DecodedActivityMapper` (common) interprets it.

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
| `hot-reload.md` | Desktop app, Compose Hot Reload, its MCP server (screenshots, semantic tree, clicks) for verifying UI |

Skills: `new-screen` (scaffold a Circuit screen), `lexicon-change` (add or change a record or query across every layer), `local-stack` (bring up, seed, smoke-test and debug the Docker network).

Agents: `gradle-runner` (run Gradle tasks and return a verdict, not a log) and `lexicon-auditor` (read-only cross-layer consistency check after a lexicon change).

Hooks (`.claude/settings.json`, scripts in `.claude/hooks/`) block hand edits to generated files, ask before editing a committed migration, block Ozone in client code, warn when Gradle runs alongside a `hotRun --auto` build, block `private val _x` backing properties in favour of Kotlin explicit backing fields (`val x: StateFlow<T>` + `field = MutableStateFlow(...)`), and remind you which counterparts to update after a lexicon-layer edit.
