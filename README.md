# Activity Tracker

A Strava-style athlete activity tracker built on the [AT Protocol](https://atproto.com). Activities,
kudos, comments, follows and profiles are records in each athlete's own repository (their PDS); an
AppView indexes them from the firehose and serves feeds.

- **Clients:** Android, iOS, Web (Kotlin/Wasm), Desktop (JVM, mainly a hot-reload dev target). GPS recording is phone-only. Compose Multiplatform + Circuit + Metro + Ktor + Room 3.
- **Backend:** Ktor AppView (Postgres) fed by Jetstream. A local PLC + PDS + relay + Jetstream run in Docker.

## Local development

Requires JDK 25, Docker, Android SDK (for Android), Xcode (for iOS), and `cloudflared` for native sign-in
(`brew install cloudflared`).

```bash
scripts/dev.sh --seed alice     # Docker stack, tunnel, local.properties, AppView, adb reverse, alice.test
scripts/dev.sh status           # what's running, and whether the tunnel URL, AppView and local.properties agree
scripts/dev.sh down             # stop the AppView, the tunnel and the containers (data is kept)
```

`scripts/dev.sh` is safe to re-run: it checks each piece and only starts, restarts or rewrites what is
missing or stale. When the tunnel URL changes it rewrites `local.properties`, restarts the AppView and
reinstalls the Android debug app on connected devices (`--android` forces the reinstall, `--no-android`
and `--no-tunnel` skip those steps). The AppView and tunnel run in the background with logs in `.dev/`.
The steps below are what it automates.

```bash
cp docker/.env.example docker/.env
docker compose -f docker/compose.yaml up -d      # PLC :2582, PDS :2583, relay :2470, Jetstream :6008, Postgres :5433
docker/scripts/create-account.sh alice          # creates alice.test (password in docker/.env)
./gradlew :server-appview:run                    # AppView on :8081
./gradlew :webApp:wasmJsBrowserDevelopmentRun       # web app on http://127.0.0.1:8080
./gradlew :desktopApp:hotRun --auto                  # desktop app with Compose Hot Reload
```

The first `up` builds PLC, the relay and Jetstream from source, which takes a few minutes. The relay
and Jetstream are pinned and carry one-line patches that let them reach a plain-HTTP `localhost` PDS
(see `docker/relay.Dockerfile` and `docker/jetstream.Dockerfile`). The AppView starts at Jetstream's
live tip on first run, so records written before it first connects are not indexed.

Sign in on the web or desktop app with `alice.test`. Both use the ATProto *loopback* OAuth client,
so they need no hosted client metadata. The desktop app catches the redirect on `127.0.0.1:8091`.

**Native (Android/iOS) sign-in** needs OAuth client metadata the PDS can fetch over HTTPS, so expose
the AppView through a tunnel. The tunnel URL is set once and everything else (client ID, the redirect
scheme `com.trycloudflare.<name>`, the Android manifest, the AppView's `APPVIEW_PUBLIC_URL`) derives from it:

```bash
brew install cloudflared
cloudflared tunnel --url http://localhost:8081   # prints https://<name>.trycloudflare.com
echo "activitytracker.appviewPublicUrl=https://<name>.trycloudflare.com" >> local.properties
./gradlew :server-appview:run                    # restart so it serves the new URL
```

Then rebuild the apps. Quick tunnels get a new hostname each run, so update the property each time
(or pass `-Pactivitytracker.appviewPublicUrl=...`).

With a tunnel set, the AppView's DID is the tunnel host. `:server-appview:run` also sets
`APPVIEW_EXTRA_AUDIENCES=did:web:localhost%3A8081` so the web app keeps working against the same AppView.

- **Android** (emulator, or a device over USB/wireless adb): run `adb reverse tcp:2582 tcp:2582` (and 2583,
  8081) so the device's `localhost` reaches the stack. Repeat after the emulator/device reconnects.
- **iOS Simulator** shares the Mac's network, so it needs nothing extra.
- **Physical iPhone** is not supported against the local stack: the PDS identifies itself as
  `http://localhost:2583` (in DID docs and as the OAuth issuer), which a phone can't reach, and iOS has
  no `adb reverse`. That needs a config against public services instead.

## Layout

| Path | What |
|---|---|
| `lexicons/` | Lexicon JSON (`app.activitytracker.*`), the schema source of truth |
| `core/lexicon` | Kotlin models + typed XRPC method descriptors, shared by clients and AppView |
| `core/atproto` | XRPC client, identity resolution, OAuth (PAR/PKCE/DPoP), service auth |
| `core/data` | Room 3 cache + outbox, repositories, Metro bindings |
| `core/location` | GPS trackers per platform, recording engine, polyline/GPX |
| `app/screens` | Circuit contracts: each feature's `Screen`, `State` and `Event` |
| `app/presenters` | Circuit presenters (Compose runtime, no UI) |
| `composeApp` | Circuit UIs, shared components, Metro graphs and scopes, platform actuals |
| `androidApp`, `iosApp`, `webApp`, `desktopApp` | Thin platform shells (entry points, graph roots) |
| `web/sqlite-worker` | SQLite-WASM Web Worker backing Room on the web |
| `server/appview` | Ktor AppView: Jetstream indexer, XRPC queries, did:web |
| `docker/` | Local ATProto network |

## Known gaps

- Android records only while in the foreground (no foreground service yet).
- Route maps are a Canvas polyline placeholder.
