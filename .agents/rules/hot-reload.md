---
paths:
  - "desktopApp/**"
  - "composeApp/**"
---

# Desktop app and Compose Hot Reload

`:desktopApp` is the JVM shell (like `:webApp`): `DesktopAppGraph`, `main()`, and `LoopbackRedirectServer`. It is mainly a dev
target for fast UI iteration. It has no GPS recording (`UnsupportedLocationTracker`, shared with web), so the feed hides Record.

**Nothing in the build declares hot reload.** The Compose Multiplatform Gradle plugin auto-applies
`org.jetbrains.compose.hot-reload` to every Kotlin JVM/Multiplatform project that applies it, so there is no catalog alias to bump.
`-Porg.jetbrains.compose.hot.reload.disable=true` turns it off. Hot reload needs a JetBrains Runtime. The toolchain is Corretto,
so `gradle.properties` sets `compose.reload.jbr.autoProvisioningEnabled=true` and foojay fetches a JBR on the first `hotRun`.

```
./gradlew :desktopApp:hotRun --auto   # continuous build; reloads on save
./gradlew :desktopApp:hotRun          # explicit mode; reload with ./gradlew :desktopApp:reload
```

`:desktopApp` is a plain `kotlin("jvm")` module, so its tasks have no target suffix: `hotRun`, `hotMcpServer`, `reload`. The KMP
Compose modules also get `hotRunJvm`/`hotMcpServerJvm`. Don't use those. That's why `.mcp.json` names `:desktopApp:hotMcpServer`
in full.

**Sign-in.** The desktop app uses the ATProto loopback client against the local AppView (`NetworkConfigs.desktop`), so it needs
the Docker stack and AppView but no tunnel. The browser redirects to `http://127.0.0.1:8091/callback` (`-PoauthPort=` changes it).
The DB and the HTTP cache live in `DesktopAppDirs` (`~/Library/Application Support/ActivityTracker` on macOS). The secret key is in the
Keychain (`io.github.solcott.activitytracker`/`oauth_store_key`), or in a `secret.key` there on Linux. Delete that directory to
start signed out with an empty DB.

**Reload output is not in the `hotRun` terminal.** `--auto` forks a second Gradle daemon. Its recompile/reload log goes to
`desktopApp/build/run/main/main.chr.log`. Read it before assuming the app is wedged, and check for a stale app process from an
earlier session (each `hotRun` starts its own, and a second one fails to bind the OAuth port).

**MCP server.** `.mcp.json` registers `compose-hot-reload`: `status`, `reload`, `await_reload`, `take_screenshot`,
`get_semantic_tree`, `get_ui_error`, `get_logs`, `list_windows`, `click`, `type_text`, `scroll`, `resize_window`, `restart`,
`reset_ui`. It waits for the app and connects when it appears, so launch `hotRun` separately. Check `status.buildContinuous` to
choose between `reload` (explicit mode) and `await_reload` (`--auto`). This is the quickest way to verify a UI change: edit,
reload, `take_screenshot` / `get_semantic_tree`.

- **`take_screenshot` grabs the screen region, not the window.** If another window covers the app, you get that window instead.
  Prefer `get_semantic_tree` for checking content, and screenshot only when the app is in front.
- **Node ids change on every recomposition or navigation.** Re-read `get_semantic_tree` before each `click`/`type_text`. A
  stale id can report `success` and do nothing.
- **The Metro graph is not reloaded.** `createGraphFactory<DesktopAppGraph.Factory>()` runs outside the composition, so DI,
  binding and presenter-signature changes need `restart`, not `reload`.
- **Retained state survives a reload.** Presenter state persists across a swap. After a `State` shape change, `reset_ui` drops
  the composition, and `restart` restarts the process.
- A `hotRun --auto` build shares `build/` with other Gradle runs, and the `warn-hotrun-race` hook warns about that. Stop it
  (`pkill -f "desktopApp:hotRun"`) before `check`, or re-run a failure that names modules you didn't touch.
