# Tasks

## 1. Build: one device-aware network config (D2, D5, D6)

- [x] 1.1 In `NativeOAuthConventionPlugin`, derive `appviewPublicUrl` as `https://appview-<host>` whenever a device host is set (overriding `activitytracker.appviewPublicUrl`), and add a TestKit case in `NativeOAuthConventionPluginTest` for a device host with a stale configured URL; verify with `./gradlew -p build-logic :convention:test`
- [x] 1.2 Rename `GenerateNativeNetworkConfig`/`GeneratedNativeNetworkConfig` to `GenerateNetworkConfig`/`GeneratedNetworkConfig` (composeApp task `generateNetworkConfig`) and add `APPVIEW_URL` (public AppView in device mode, `http://localhost:8081` otherwise), with TestKit cases for both modes; verify with `./gradlew -p build-logic :convention:test`
- [x] 1.3 Add a TestKit case showing a device host from the Gradle user home's `gradle.properties` (TestKit `withTestKitDir`/a `GRADLE_USER_HOME` gradle.properties) reaches the generated PLC, handle-resolver and AppView URLs; verify with `./gradlew -p build-logic :convention:test`
- [x] 1.4 In `composeApp` `NetworkConfigs`, take `PLC_URL`, `HANDLE_RESOLVER_URL` and `APPVIEW_URL` from `GeneratedNetworkConfig`, drop the `NATIVE_PLC_URL`/`NATIVE_HANDLE_RESOLVER_URL` duplicates, keep `web()`/`desktop()` on loopback OAuth, and update the KDoc; verify `./gradlew :composeApp:jvmTest :webApp:compileKotlinWasmJs :desktopApp:compileKotlin` pass
- [x] 1.5 In `server/appview/build.gradle.kts`, have the `run` task use the effective (set or derived) public URL and add `APPVIEW_EXTRA_AUDIENCES` only when no device host is set, updating its comment; verify by running `./gradlew :server-appview:run` in device mode and checking the served client_id is `https://appview-<host>/oauth/client-metadata.json` (`scripts/dev.sh status`)

## 2. `scripts/dev.sh`: device mode per developer (D1, D3, D4)

- [x] 2.1 Add a user-level property lookup (`${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties`) and make `device_host()` check it before `local.properties`, remembering the source; verify `scripts/dev.sh status` in a worktree with no `deviceHost` in its `local.properties` reports device mode "from ~/.gradle/gradle.properties"
- [x] 2.2 In device mode, stop writing `activitytracker.appviewPublicUrl` and remove a stale line from `local.properties`; keep the quick-tunnel path unchanged; verify `local.properties` has no `appviewPublicUrl` after `scripts/dev.sh` in device mode and still gets the quick-tunnel URL in localhost mode
- [x] 2.3 Move `DEVICE_CONFIG` to `~/.cloudflared/activitytracker-dev.yml` (written by `device-setup`, read by `ensure_named_tunnel`, `named_tunnel_pids`, `stop_named_tunnel`), migrating a matching `.dev/cloudflared-device.yml` and restarting a tunnel started from the old path once; verify `pgrep -fl "cloudflared tunnel --config"` shows the new path and `https://<host>/xrpc/_health` answers
- [x] 2.4 Make `ensure_mode` read the volumes' hostname from the PDS container's `PDS_HOSTNAME` (`docker inspect` of `compose ps -aq pds`), falling back to `docker/.env`; verify a worktree whose `docker/.env` says localhost comes up against the device-mode stack without `--reset`, and that a genuine mode switch still refuses with the `--reset` hint
- [x] 2.5 Print one localhost-mode `warn` naming `activitytracker.deviceHost` in `~/.gradle/gradle.properties` and `device-setup`, update the header comment and `device-setup`'s missing-host message to name the user-level file; verify by running `scripts/dev.sh status`/`up` with no host set
- [x] 2.6 Run `shellcheck scripts/dev.sh` (if installed) and `scripts/check-public-safety.sh`; verify both pass and no tracked file names a real host

## 3. Docs and rules (D7)

- [x] 3.1 Rewrite the README's local setup so device mode is the main path (host in `~/.gradle/gradle.properties`, `device-setup`, `--reset --seed`), localhost mode the fallback, and add the migration steps for existing device-mode setups; verify every command in the section runs as written
- [x] 3.2 Update `.agents/rules/docker.md`, `.agents/skills/local-stack/SKILL.md` (setup and troubleshooting rows), `.agents/rules/hot-reload.md` (desktop sign-in now uses the public AppView in device mode), and the `docker/compose.yaml` / `docker/.env.example` comments; verify `scripts/check-agent-sync.sh` passes

## 4. Integration checks

- [x] 4.1 Run `./gradlew check` (via the gradle-runner agent) and verify it passes
- [x] 4.2 In device mode, sign in to `alice.test` from the web app (`:webApp:wasmJsBrowserDevelopmentRun`) and confirm in the network log that AppView calls go to `https://appview-<host>`; sign in from the desktop app (`:desktopApp:hotRun`) likewise
- [x] 4.3 Sign in on the iOS simulator and a physical device in device mode and verify both still work
- [x] 4.4 With the user-level host removed and `scripts/dev.sh --reset --yes --seed alice`, verify localhost mode behaves as before: PDS `http://localhost:2583`, web and desktop sign in on localhost, `APPVIEW_EXTRA_AUDIENCES` is set; then restore device mode with `--reset`

## Workflow follow-up

- Archive the change with `/opsx:archive` once the integration checks pass.
