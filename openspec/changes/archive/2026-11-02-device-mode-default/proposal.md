# Proposal

## Why

Device mode works, but it doesn't feel like the default.

- **Worktrees and new checkouts drop out of it.** It's configured per checkout, in `local.properties`, and every worktree gets its own stale copy of that file. The local Docker stack, though, is shared across checkouts. So a fresh worktree either comes up in localhost mode, or it refuses to start because the shared stack's accounts were made for the device host.
- **Only native builds use the public hosts.** Web and desktop still address the localhost ports, so the Mac and the phone see different endpoints, and the AppView keeps a second audience just for them.
- **Docs and scripts lead with localhost mode.** They present device mode as a side path for physical phones.

## What Changes

- **One host for every checkout.** Device mode is set once per developer: `activitytracker.deviceHost` in `~/.gradle/gradle.properties`, outside every checkout. Gradle builds, including the Xcode build phase, already read it there. `scripts/dev.sh` learns to read it too, with Gradle's precedence: a `-P` property, then the Gradle user home, then `local.properties`. `local.properties` keeps working for a single checkout.
- **Nothing device-specific stays per checkout.** In device mode the AppView's public URL is derived from the device host, so a checkout no longer depends on `dev.sh` having written it. The named tunnel's ingress file moves next to its credentials in `~/.cloudflared/`. `dev.sh` learns which hostname the shared stack's accounts were made under from the PDS container, not from the checkout's `docker/.env`.
- **Every client uses the public hosts in device mode.** Web and desktop resolve identities through the public PLC and PDS hostnames and call the AppView at its public hostname, as native builds already do. The AppView then has a single audience in device mode. Web and desktop keep their loopback OAuth clients.
- **Docs and the script lead with device mode.** The README, `.agents/rules/docker.md` and the `local-stack` skill present device mode as the normal setup. Localhost mode becomes the fallback when no host is set. In localhost mode, `dev.sh` says how to turn device mode on, and `dev.sh status` says where the host came from.
- Localhost mode is unchanged when no host is set anywhere. The repository still names no host and ships no default.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `dev/local-stack`: device mode is set per developer, across all of their checkouts, and covers every client. The developer's host may also live in a user-level file outside the repository. A new checkout picks up an existing device-mode stack without a wipe.

## Impact

- `scripts/dev.sh`: device-host lookup, the mode check against the running stack, the ingress-file location, `device-setup`, `status`, and the localhost-mode hint.
- `build-logic` `NativeOAuthConventionPlugin` and its TestKit test: the derived AppView URL, plus a generated AppView URL for every client.
- `composeApp` `NetworkConfigs`: web and desktop use the generated PLC, handle-resolver and AppView URLs. `server/appview/build.gradle.kts`: no extra `localhost` audience in device mode.
- Docs: README, `.agents/rules/docker.md`, `.agents/skills/local-stack/SKILL.md`, and comments in `docker/compose.yaml` and `.env.example`.
- **Behaviour change for existing device-mode checkouts:** web and desktop now go through the tunnel, so they need it running, and every call pays a Cloudflare round trip.
- No lexicon or app-feature changes. Production endpoints are untouched.
