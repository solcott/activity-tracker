# Design

## Context

See proposal.md for the motivation. This is how things stand today:

- **How the host is read.** `NativeOAuthConventionPlugin.property()` reads a Gradle property before the root `local.properties` (`gradleProperty(name).orElse(localProperties…)`). A Gradle property covers `-P`, the Gradle user home's `gradle.properties` and the tracked root `gradle.properties`. So `activitytracker.deviceHost` in `~/.gradle/gradle.properties` already reaches every build, including Xcode's build phase. `scripts/dev.sh` `device_host()` reads only `local.properties`.
- **What is shared and what isn't.** The Docker stack is shared by every checkout: compose project `activity-tracker`, fixed volume and container names, and fixed ports (2582, 2583, 8081). Per checkout are the mode marker `PDS_HOSTNAME` in `docker/.env`, the ingress file `.dev/cloudflared-device.yml`, and `activitytracker.appviewPublicUrl` in `local.properties`, which `dev.sh` writes. Worktrees start with copies of the git-ignored files made when the worktree was created, and some current worktrees have no `deviceHost` in theirs.
- **How each client is configured.** `GeneratedNativeNetworkConfig` (composeApp `commonMain`, so every target compiles it) carries the native AppView URL, redirect scheme, labeler DID, PLC URL and handle-resolver URL. `NetworkConfigs.web()` and `desktop()` use the `localhost` constants instead. The AppView's `run` task adds `APPVIEW_EXTRA_AUDIENCES=did:web:localhost%3A8081` whenever it has a public URL. The AppView's CORS is `anyHost()`.

## Goals / Non-Goals

**Goals:**
- One user-level setting turns device mode on for every checkout, with Gradle and `dev.sh` agreeing on it.
- No device-mode state left per checkout that a stale copy can contradict.
- One set of endpoints for every client in device mode.

**Non-Goals:**
- **Running two AppViews or two stacks at once from different worktrees.** Port 8081 and the compose project stay single.
- **Moving the rest of `docker/.env` to user level.** That covers the lexicon authority, Ozone, triage and importer DIDs. It is a separate per-checkout-state problem, and it already exists in localhost mode.
- **A per-checkout opt-out from a user-wide host.** See D1.
- **Changing production endpoints or the native OAuth flow.**

## Decisions

### D1. The host is looked up in Gradle's order, and `dev.sh` mirrors it
The effective device host is the first non-empty value among:

1. `-Pactivitytracker.deviceHost` (Gradle only).
2. `activitytracker.deviceHost` in `${GRADLE_USER_HOME:-~/.gradle}/gradle.properties`.
3. `activitytracker.deviceHost` in the checkout's `local.properties`.

`dev.sh` gains a `user_property` lookup and `device_host()` checks it before `property`. `dev.sh status` names the source: "device host dev.example.com (from ~/.gradle/gradle.properties)".

- *Alternative: `local.properties` wins, so a checkout can opt out.* That would mean reversing the plugin's order, which TestKit already covers ("device host gradle property wins"), only for this key. The opt-out it buys is rarely needed. Stale `local.properties` copies are also what this change is fixing, so letting them win would bring the problem back. To use localhost mode, take the host out of the user-level file.
- *Alternative: a project-specific `~/.config/activitytracker`.* Both the plugin and the script would have to learn a new file. Gradle already reads the user-home file.

The tracked root `gradle.properties` must never set the key. `scripts/check-public-safety.sh` already rejects real hosts in tracked files, and the docs say to use the user-home file.

### D2. The AppView's public URL is derived from the device host
When a device host is set, `NativeOAuthExtension.appviewPublicUrl` is `https://appview-<host>` (`sibling("appview", host)`), whatever `activitytracker.appviewPublicUrl` says. Otherwise it is the configured value, or `http://localhost:8081` as before. The redirect scheme follows from it as today.

- In device mode, `dev.sh` stops writing `appviewPublicUrl` to `local.properties` and drops a stale line. `ensure_local_properties` runs only for the quick tunnel.
- The AppView `run` task reads the effective URL (`appviewPublicUrl` when set or derived, not only `configuredAppviewPublicUrl`). Without that, a worktree that never ran `dev.sh` would serve no public client metadata.
- *Alternative: keep writing it from `dev.sh`.* A checkout's builds would then depend on `dev.sh` having run there, which is exactly the worktree failure.

### D3. The ingress file lives with the tunnel's credentials
`DEVICE_CONFIG` becomes `~/.cloudflared/activitytracker-dev.yml`. `device-setup` writes it, `ensure_named_tunnel` checks it, and `named_tunnel_pids` and `stop_named_tunnel` match the process by that path. Every checkout therefore sees, starts and stops the same tunnel process. The log stays in the checkout's `.dev/`.

- **Migration.** When the new file is missing and the checkout's `.dev/cloudflared-device.yml` names the same host, `dev.sh` moves it there. A running tunnel started from the old path is stopped and restarted once.
- *Alternative: keep the file in `.dev/` and copy it into new checkouts.* That leaves the copy problem in place.

### D4. The stack's mode comes from the PDS container
`ensure_mode` reads `current` from the PDS container's environment: `docker inspect` on `"${COMPOSE[@]}" ps -aq pds`, taking `PDS_HOSTNAME`. Compose resolves that container the same way from every checkout. When no container exists but the volume does (someone removed containers by hand), it falls back to the checkout's `docker/.env` as today. With neither, the stack is fresh. The `--reset` path is unchanged. It still writes the chosen mode into the checkout's `docker/.env`, because compose reads it from there.

- *Alternative: a marker file in a user-level directory.* That is a second source of truth that can drift from the volumes. The container is created from the same `.env` the volumes were made under.

### D5. One generated network config for every client
The generated object becomes `GeneratedNetworkConfig`, written by a `GenerateNetworkConfig` task registered in `composeApp` as `generateNetworkConfig`. The plugin id `activitytracker.native.oauth` and the `nativeOAuth` extension keep their names. They still own native OAuth, and renaming them would only churn every build script. The object gains:

- `APPVIEW_URL`: `https://appview-<host>` in device mode, else `http://localhost:8081`.

It keeps the native ones (`APPVIEW_PUBLIC_URL`, `REDIRECT_SCHEME`). `PLC_URL` and `HANDLE_RESOLVER_URL` are already device-aware.

In `NetworkConfigs`, `PLC_URL`, `HANDLE_RESOLVER_URL` and `APPVIEW_URL` come from the generated object, and the `NATIVE_PLC_URL` / `NATIVE_HANDLE_RESOLVER_URL` duplicates go away. `web()` and `desktop()` keep their loopback OAuth clients: the redirect stays on `127.0.0.1` and doesn't depend on the PDS host. `appViewDid` is still derived from the URL, so in device mode it becomes `did:web:appview-<host>`, which is the AppView's own identity.

- *Alternative: only switch PLC and the handle resolver, and keep the AppView on localhost.* Faster on the Mac, but the user chose a single set of endpoints. The extra audience would also have to stay.

### D6. No extra AppView audience in device mode
The `run` task sets `APPVIEW_EXTRA_AUDIENCES=did:web:localhost%3A<port>` only when no device host is set. An explicit environment variable still wins. CORS is already `anyHost()`, so the web origin `http://127.0.0.1:8080` can call the public hostname.

### D7. Docs and the script nudge toward device mode
- **README:** "Device mode" becomes the main local setup section. Its first step puts the host in `~/.gradle/gradle.properties`, and existing setups can move the line there. Localhost mode is described as what you get without a Cloudflare domain.
- **`dev.sh up` in localhost mode:** prints one `warn` that names the setting and `device-setup`.
- **`.agents/rules/docker.md` and the `local-stack` skill:** list the new lookup order, the ingress location and the container-based mode check.
- **KDoc** in `NetworkConfigs` and the plugin, and the comments in `docker/compose.yaml` and `.env.example`, say that web and desktop follow device mode too.

## Risks / Trade-offs

- **Web and desktop now need the tunnel in device mode, and pay a Cloudflare round trip on every call.** → The user accepted this. `dev.sh` starts the tunnel. If the tunnel is down, `status` says so, and so does the existing `check_named_tunnel`.
- **A user-wide host changes behaviour in every checkout at once, including old branches whose `dev.sh` doesn't read the user home.** → An old branch's `dev.sh` sees no host and refuses with the existing mode-mismatch message, which doesn't destroy anything. Its Gradle builds already target device mode.
- **Removing the user-level host from a device-mode stack needs `--reset`, as switching modes always has.** → The existing refusal message names it.
- **The rest of `docker/.env` is still per checkout** (lexicon authority, Ozone and triage DIDs). A brand-new checkout may re-run labeler setup against the shared stack. → Out of scope (non-goal). It already happens in localhost mode. Note it in the rules if the work shows it bites.
- **Renaming the generated object touches every reference.** → They are confined to `NetworkConfigs.kt`, the plugin and its test.

## Migration Plan

For a developer already on device mode:

1. Move `activitytracker.deviceHost` from `local.properties` to `~/.gradle/gradle.properties`. This is optional, since `local.properties` still works.
2. Run `scripts/dev.sh`. It moves the ingress file to `~/.cloudflared/` and restarts the named tunnel once. No `--reset` is needed, because the hostnames don't change.
3. Rebuild web and desktop to pick up the public endpoints. Native builds are unchanged.

To roll back, revert the change, and `device-setup` writes the ingress file back to `.dev/`.
