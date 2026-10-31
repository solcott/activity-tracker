# Proposal

## Why

A physical iPhone or iPad can't sign in to the local stack. The PDS identifies itself as `http://localhost:2583` in DID documents and as its OAuth issuer, the clients resolve identities through `http://localhost:2582` (PLC) and `http://localhost:2583`, and iOS has no `adb reverse`. That blocks testing what only a real device does. The trigger was the paced outbox's iOS background upload task, which ran on a Personal-Team-signed iPad but had no PDS to upload to.

## What Changes

- An opt-in **device mode** for the local stack. A named Cloudflare tunnel on a developer's own domain gives the PDS, PLC and AppView stable public HTTPS hostnames. The PDS runs with `PDS_HOSTNAME` set to its public hostname, so DID documents and the OAuth issuer name a URL any device can reach, on Wi-Fi or cellular.
- Each developer sets one value, `activitytracker.deviceHost` in `local.properties` (git-ignored): a subdomain of their own Cloudflare zone, such as `dev.example.com`. PLC's and the AppView's hostnames are derived from it. The repository has no default and names no real domain, so device mode is on exactly when the value is set.
- `scripts/dev.sh` gains a one-time `device-setup` command (log in, create the tunnel, route its DNS) and, in device mode, runs the named tunnel instead of the AppView quick tunnel. Because the hostname is baked into accounts, it refuses to switch an existing stack between modes unless asked to wipe its volumes.
- `docker/compose.yaml` takes the PDS hostname and the relay crawl host from `docker/.env`, defaulting to today's `localhost` values.
- `GeneratedNativeNetworkConfig` gains the PLC and handle-resolver URLs, derived from `deviceHost`. The native clients use them. When it is unset, the generated values are today's localhost URLs, so the simulator, Android over `adb reverse`, web and desktop behave as they do now.
- In device mode the AppView URL also stays stable across runs, so native builds stop needing a rebuild, and Spaces stop needing to be recreated, every time `dev.sh` starts a new tunnel.
- README, `.agents/rules/docker.md` and the `local-stack` skill document the mode and replace the "Physical iPhone is not supported" note.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `dev/local-stack`: adds a physical-device mode with stable public hostnames, and requires the default localhost mode to keep working unchanged.

## Impact

- `scripts/dev.sh` (new command, tunnel mode, mode-switch guard), `docker/compose.yaml`, `docker/.env.example`.
- `build-logic` `NativeOAuthConventionPlugin` and its TestKit test; `composeApp` `NetworkConfigs`.
- Docs: README, `.agents/rules/docker.md`, `.agents/skills/local-stack/SKILL.md`.
- External dependency, only in device mode: a Cloudflare account with a domain whose nameservers point at Cloudflare (free plan), and `cloudflared` (already required).
- No lexicon, AppView or app behavior changes. The relay and Jetstream patches stay for localhost mode.
