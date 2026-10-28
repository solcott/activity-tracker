# AGENTS.md

This file provides guidance to AI coding assistants (Claude Code, Antigravity) when working with code in this repository. Configuration across `.agents/` and `.claude/` is kept synchronized via symbolic links.

## Project

A Kotlin Multiplatform, Strava-style athlete activity tracker backed by ATProtocol. Clients target Android, iOS, Web (wasmJs) and Desktop (JVM) and share a Compose Multiplatform UI. GPS recording is phone-only: desktop and web offer manual entry only. The backend is our own AppView (Ktor + Postgres). User data lives in each user's PDS. See [README.md](README.md) for local setup.

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
./gradlew :detekt-rules:test                      # the repo's own detekt rules (UseTextFieldState, UseSingleFlight, NoSystemClockInUi)
./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"   # every content-loading screen's samples against ui/content-loading
./gradlew :androidApp:testDebugScreenshotTestDefaultTestSuite     # screenshots vs references (part of check); report in androidApp/build/reports/tests/
./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite   # rewrite references in androidApp/src/screenshotTestDefaultDebug/reference after an intended UI change
scripts/check-agent-sync.sh                       # Claude Code <-> Antigravity config symlinks, hook and command parity

./gradlew :webApp:wasmJsBrowserDevelopmentRun     # web app; PORT env overrides 8080
./gradlew kotlinWasmUpgradeYarnLock               # after an npm dependency change: refresh kotlin-js-store/wasm/yarn.lock (Yarn, so the *PackageLock tasks fail)
./gradlew :desktopApp:hotRun --auto               # desktop app with Compose Hot Reload (reloads on save); :desktopApp:run without
./gradlew :androidApp:installDebug
./gradlew :server-appview:run                     # AppView on :8081; defaults target the docker stack
./gradlew :server-segment-importer:run --args="extract region.osm.pbf --agency usfs=build/usfs.geojsonl --region us-vermont --out build/us-vermont.ndjson"   # OSM and agency trails -> candidate segments
./gradlew :server-segment-importer:run --args="stage build/us-vermont.ndjson"      # upload candidates to the AppView as the importer account
./gradlew :server-segment-importer:run --args="publish --region us-vermont"        # publish ridden candidates as feed.segment records
./gradlew :server-segment-importer:run --args="refine build/us-vermont.ndjson"     # give published segments their candidates' names, and finer lines (same path only)
scripts/segment-import.sh us-vermont vermont-latest.osm.pbf [--agency usfs=<file>]... [--lang en]   # extract, stage, publish and refine in one run
scripts/dev.sh [--seed alice]                     # whole local env: Docker, tunnel, local.properties, AppView, adb reverse (idempotent)
scripts/dev.sh status | down
docker compose -f docker/compose.yaml up -d       # local PLC/PDS/relay/Jetstream/Postgres
docker/scripts/create-account.sh alice            # alice.test on the local PDS
```

iOS: open `iosApp/iosApp.xcodeproj`, or run `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGN_IDENTITY=- build`. The ad-hoc signature ("Sign to Run Locally") needs no team, and the app needs it: an unsigned build (`CODE_SIGNING_ALLOWED=NO`) can't use the Keychain, so sign-in fails with `-34018`. Its build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`.

Wasm tests are disabled. Client modules also have a `jvm()` target so tests (Room in-memory, Ktor `MockEngine`, `circuit-test`) run on the JVM.

## Architecture

**Data flow.** Clients write records straight to the user's PDS with `putRecord`, using DPoP-bound OAuth tokens. The AppView never writes to repos or spaces. It tails Jetstream (`network.bsky.jetstream.subscribeEvents`, the v2 wire), filtered to `Nsid.indexedCollections`, indexes records into Postgres, and serves `app.activitytracker.*` queries. Each activity has a visibility (Public, Followers, Mutuals, Only me), given by where it's stored: a public one in the repo, a non-public one in the athlete's `app.activitytracker.space.athlete` space for that tier. Each of its detail categories (heart rate, power, start time, map…) has its own visibility no wider than that (`DetailCategory`): fields of a narrower category leave the activity record for a `feed.activityDetails` record in that tier's space, and its streams are one `feed.activityStreams` record per tier (`ActivityLayout`). The AppView is those spaces' managing app (the athlete's PDS asks its `checkUserAccess`, `SpaceAccess.kt`, who may read each tier) and also a member and syncer of them (`SpaceSyncer`, with its own `APPVIEW_SIGNING_KEY`), so it holds non-public activities, their details and their streams and serves each only to the viewers its tier admits (`visibleTo`), merging an activity's tiers per viewer (`activityView.details`, `getActivityStreams` parts). It also syncs each athlete's `interactions` space, which only they and the AppView read: their likes and comments on non-public activities, served only while both the viewer and the giver may see the activity, and a comment only to viewers in the audience it was written for (`feed.comment#audience`), and their blocks and mutes (`graph.block`, `graph.mute`). A block, from that space or an `app.bsky.graph.block` in an athlete's repo, hides each athlete from the other everywhere (`blocked()` in `visibleTo`, `effectiveFollow()` and `SpaceAccess`); a mute leaves the muted athlete out of the muter's timeline and comments. For athletes who turned segment matching on (`actor.segmentPrefs` in `self`), it also indexes their `feed.segmentTrack`s from `self`: copies of each track with their privacy zones cut out, which it never serves and drops when matching goes off (`segments/segment-track`). It matches those tracks against public `feed.segment`s (indexed from repos only) in a background loop (`SegmentMatcher`, fed by the `segment_match_jobs` queue the indexer fills), and serves the resulting efforts only at the narrowest of their activity's tier and its Map and Pace/Speed categories (`segments/segment-efforts`), dropping them with the track, the activity, the segment or the setting. It derives each segment's status (`proposed` until enough distinct athletes have efforts on it, `active`, `duplicate` of an earlier one, or `archived`; `segments/segment-records`): only proposed and active segments are matched, and search lists proposed ones only to their creator and riders. Statuses count private efforts but reveal only the status. Active segments have leaderboards (`segments/segment-leaderboards`): each athlete's fastest effort the viewer may see, leaving out athletes who hide from leaderboards (`actor.segmentPrefs`), activities excluded on their segment track, and efforts faster than their sport group's speed cap. The segment importer, the one account `SEGMENT_IMPORTER_DID` names, stages candidate segments (public lines such as OSM trails) in the AppView by region and import run (`importer.putSegmentCandidates`, `finishSegmentCandidates`; `segments/segment-candidates`), which no feed query serves, matches for efforts or counts as duplicates; the matcher's `track` job also records which athletes' tracks pass each candidate (candidate riders, private like efforts and dropped with the tracks), and `importer.getRiddenSegmentCandidates` tells the importer only which candidates `SEGMENT_CANDIDATE_RIDERS` distinct athletes have ridden and it hasn't published yet, never how many, who or when. `importer.checkSegmentCandidates` checks a page of them, by id, for duplicates of live segments in one call. The importer publishes those as ordinary `feed.segment`s in its own repo, keyed by candidate id and exempt from the daily creation limit (`segments/segment-import`). Athletes who choose age, weight and gender boards keep an `actor.leaderboardBrackets` in `self`, holding only brackets their client computes; the AppView indexes it from there and uses it only to filter leaderboards (`segments/leaderboard-brackets`). It also indexes `gear.item` from repos and every tier, and each activity's `gear` references from its record and details by origin (`activity_gear`), and serves `activityView.gear` only with the items a viewer may see in a Gear tier they're admitted to, and each item's usage (`gear.getGear`, `getActorGear`, `getGearActivities`) totalled live over only the activities and references that viewer may see (`api/GearViews.kt`). It stores nothing else private beyond those likes, comments, blocks, mutes, segment tracks, efforts, candidate riders, the two leaderboard choices, the brackets and gear: profile details an athlete marks Followers, Mutuals or Only me (`actor.privateDetails`), their activity defaults and their privacy zones stay in their tier spaces, which clients write and read directly, and `getProfile` shows only public details. Only effective follows count: an athlete whose profile has `followPolicy: approve` counts a follow once they publish a `graph.followApproval` for the follower (`effectiveFollow()`), and declines stay private in their `self` space. Clients call the AppView directly and authenticate with short-lived service-auth JWTs, which the PDS mints via `com.atproto.server.getServiceAuth` (see `ServiceAuthAuthenticator`). The AppView verifies these JWTs against the issuer's DID-doc key (`ServiceAuthVerifier`, which supports ES256K and ES256 via BouncyCastle) and requires their `lxm` to be the called method. It shows a handle only once it resolves back to the account's DID (`HandleVerifier`), else `handle.invalid`.

**Lexicons.** `lexicons/**.json` is the schema source of truth. `core/lexicon` hand-mirrors it as `@Serializable` models plus typed `XrpcQuery`/`XrpcProcedure` descriptors, which both the client (`XrpcClient`) and the AppView routes use. A lexicon change updates the JSON, the Kotlin model, and the AppView indexer/views together. Lexicons have no floats, so units are integers. Records carry `$type`. Serialize lexicon data only with `AtJson`. All repo records are public, so never put private data in a record: a non-public profile detail goes to the athlete's tier space for its visibility instead (`ProfileRepository`, `AthleteSpaces`).

**Writes go through the outbox.** `ActivityRepository` enqueues each write into a Room outbox with a client-generated TID rkey. `OutboxProcessor` drains it with idempotent `putRecord`s. The UI updates optimistically, and the AppView catches up via Jetstream.

**Screens span three modules.** `app/screens` (`:app-screens`) holds each feature's `Screen`, `State` and `Event`. `app/presenters` (`:app-presenters`) holds the presenters and has the Compose runtime, no UI (foundation comes through only for `TextFieldState`: text fields' state lives in `State`, owned by the presenter). `composeApp` holds the UIs. Presenters and UIs both depend on the contracts and never on each other. `composeApp` and `webApp` list `:app-presenters` only so their Metro graphs aggregate its factories. Reusable pieces of a screen are SubCircuit sub-screens with the same three-module split (see `compose-screens.md`). Each screen's sample states live in `:app-fixtures` (`app/fixtures`, contracts and lexicon only), which UI tests and `:androidApp`'s screenshot tests render through `AppPreview` (`composeApp` `ui/preview/`) with no session or network.

**DI (Metro) and scopes.** `AppScope` holds app-wide singletons (HTTP, OAuth, Room, `ActivityRecorder`, `AuthCoordinator`). `SessionScope` is one signed-in account: a `SessionGraph` graph extension created with the `OAuthSession` and dropped on sign-out. `App.kt` renders `LoginScreen` on `AppCircuit` when signed out and a `key(did)` `SessionGraph`'s `SessionCircuit` when signed in. Platform pieces (DB builder, `LocationTracker`, `AuthBrowser`) are contributed from each module's platform source sets. Desktop and web bind `UnsupportedLocationTracker` (`nonMobileMain`), whose `isSupported = false` hides Record in the feed.

**Theme and adaptive UI.** One shared UI follows each platform's conventions. Screens build from the `ui/adaptive` wrappers (`AppScaffold`, `AppButton`, …), never the Material widgets they wrap; detekt's `UseAdaptiveComponents` enforces this.

**Hard rule:** no Ozone or other atproto SDK in client code. Ozone has no wasmJs target. The XRPC layer is hand-written on Ktor in `core/atproto`. A hook enforces this.

## Where the details live

Area rules in `.agents/rules/` (mirrored in `.claude/rules/`) load when you work on matching paths:

| Rule | Covers |
|---|---|
| `lexicons.md` | Lexicon ↔ Kotlin mirror conventions, `Nsid`, open enums, optional fields |
| `appview.md` | Indexer idempotency, untrusted input, Flyway migrations, H2-portable SQL, routes |
| `compose-screens.md` | Circuit screen anatomy, scopes, Metro graphs, presenter tests |
| `data-and-auth.md` | Room 3, outbox, XRPC client, OAuth flow and native client config |
| `build-logic.md` | Convention plugins, quality tooling, toolchain, TestKit |
| `docker.md` | Local PDS → relay → Jetstream stack, pinned and patched images |
| `activity-files.md` | FIT/GPX/TCX/Shearwater import, `ActivityFileReader`, per-platform FIT decoders, dive data |
| `hot-reload.md` | Desktop app, Compose Hot Reload, its MCP server (screenshots, semantic tree, clicks) for verifying UI |
| `adaptive-ui.md` | `AppTheme` per platform, the full `ui/adaptive` wrapper list, Calf, the iOS nav bar and Haze, `AppShell` and per-tab back stacks |

Use Kotlin explicit backing fields (`val x: StateFlow<T>` + `field = MutableStateFlow(...)`), not `private val _x`; a hook blocks the latter. Hooks in `.agents/hooks/` (configured in `.agents/hooks.json` and `.claude/settings.json`) also guard generated files, committed migrations and Ozone imports.

## Skills & Subagents

Custom skills live in `.agents/skills/`. `.claude/skills/` symlinks the shared ones and keeps its own OpenSpec skills (Claude uses `/opsx:apply`, Antigravity `/opsx-apply`). Run `scripts/check-agent-sync.sh` after changing either side.
- `local-stack`: Bring up, seed, smoke-test and troubleshoot the local ATProto network and AppView.
- `lexicon-change`: Step-by-step checklist for modifying or adding lexicons and synchronizing all layers.
- `new-screen`: Scaffold a new Circuit screen across `:app-screens`, `:app-presenters`, and `composeApp`.
- `gradle-runner`: Run Gradle tasks and get concise verdicts without huge log dumps. A subagent in Claude Code (`.agents/agents/`); the skill just points at it.
- `lexicon-auditor`: Read-only consistency audit across all lexicon layers before committing. Same agent/skill split.
- `openspec-*`: OpenSpec specifications, changes, and verification workflows.
