# Tasks

## 1. Prerequisite and relay check

- [x] 1.1 With the developer: confirm their domain's Cloudflare zone is Active, and set `activitytracker.deviceHost` in their `local.properties` (D2). Verify with `dig +short NS <zone>`, which should show Cloudflare nameservers. Nothing goes in tracked files.
- [x] 1.2 Read the pinned indigo commit's admin `requestCrawl` and host-client code (`cmd/relay`), and record whether a bare public hostname is crawled over `https`/`wss`, and whether `RELAY_ALLOW_INSECURE_HOSTS` matters (the design's Open Question). Write the answer into the design's Risks. Verify by citing file and line from the commit.

## 2. Compose takes the PDS hostname from the environment

- [x] 2.1 In `docker/compose.yaml`, read `PDS_HOSTNAME` as `${PDS_HOSTNAME:-localhost}`, `PDS_INVITE_REQUIRED` as `${PDS_INVITE_REQUIRED:-false}`, and the `relay-crawl` hostname as `${RELAY_CRAWL_HOST:-localhost:2583}` (or the form 1.2 found). Update the header comment. Add the variables, empty and with comments, to `docker/.env.example`. Verify that `docker compose -f docker/compose.yaml config` renders today's values with them unset, and that `scripts/dev.sh` on an existing localhost stack recreates nothing.
- [x] 2.2 Make `docker/scripts/create-account.sh` mint an invite code through the admin API (`com.atproto.server.createInviteCode`, Basic `admin:$PDS_ADMIN_PASSWORD`) and pass it when the PDS requires invites (`describeServer`'s `inviteCodeRequired`). Do the same for the other scripts that create accounts (`publish-lexicons.sh`, `setup-labeler.sh`). Verify by running each against a PDS started with `PDS_INVITE_REQUIRED=true`, and by checking that a bare `createAccount` curl without a code is refused.
- [x] 2.3 Update `.agents/rules/docker.md` with the hostname and invite variables, why containers hairpin through Cloudflare in device mode (D5), and that the localhost patches stay. Verify that `scripts/check-agent-sync.sh` passes.

## 3. Clients read PLC and handle-resolver URLs from generated config

- [x] 3.1 In `NativeOAuthConventionPlugin`, read `activitytracker.deviceHost` the same way as the existing keys, derive the PDS and PLC URLs from it (D2), and generate `PLC_URL` and `HANDLE_RESOLVER_URL` in `GeneratedNativeNetworkConfig`, defaulting to `http://localhost:2582` and `http://localhost:2583`. Wire the new inputs wherever the generate task is configured. Add TestKit cases to `NativeOAuthConventionPluginTest` for the defaults, `local.properties`, a Gradle property override and the sibling derivation (`dev.example.com` → `https://plc-dev.example.com`). Verify with `./gradlew -p build-logic :convention:test`.
- [x] 3.2 Make `NetworkConfigs.native()` use the generated PLC and handle-resolver URLs. Web and desktop keep the localhost constants. Update the KDoc. Verify with `./gradlew :composeApp:jvmTest`, and by checking that an iOS simulator build without the properties still signs in to `alice.test`.

## 4. `scripts/dev.sh` device mode

- [x] 4.1 Add `scripts/dev.sh device-setup`. It reads `activitytracker.deviceHost`, derives the three hostnames (D2) and rejects a host that isn't one label below a zone, runs `cloudflared tunnel login` if `~/.cloudflared/cert.pem` is missing, creates the `activitytracker-dev` tunnel if absent, routes DNS for each hostname, and writes `.dev/cloudflared-device.yml`. It must be safe to rerun. Verify that a second run changes nothing, and that `cloudflared tunnel info activitytracker-dev` lists the tunnel.
- [x] 4.2 In `cmd_up`, when `deviceHost` is set: write `PDS_HOSTNAME`, `RELAY_CRAWL_HOST` and `PDS_INVITE_REQUIRED=true` to `docker/.env`, and run the named tunnel (`cloudflared tunnel --config .dev/cloudflared-device.yml run`) instead of the quick tunnel. Set `TUNNEL_URL` to the derived AppView URL so `ensure_local_properties` and `ensure_appview` keep working, and give the named tunnel its own pid match in `down` and `status`. With the property unset, clear those keys from `docker/.env`. Verify that `dev.sh status` shows the named tunnel and that `curl https://<pds-host>/xrpc/_health` succeeds from the Mac.
- [x] 4.3 Add the mode-switch guard and `--reset [--yes]` (D6). Compare the hostname `docker/.env` records for the existing volumes with the configured one, before compose runs. On a mismatch, exit non-zero with the explanation the spec requires. With `--reset`, confirm, run `compose down -v`, clear the DID-derived `docker/.env` keys, and continue the bring-up. Verify both spec scenarios: switching without `--reset` exits non-zero, and `--reset --seed alice` ends with `describeServer` reporting `did:web:<pds-host>`.
- [x] 4.4 Update `dev.sh`'s header and `--help`, the README's local-development section (replacing the "Physical iPhone is not supported" bullet with a device-mode section: Cloudflare zone, properties, `device-setup`, `--reset`, rebuild, `dev.sh down` when idle), and the `local-stack` skill. Use `example.com` placeholders only. Verify that `scripts/check-public-safety.sh` and `scripts/check-agent-sync.sh` pass, and that `git grep` finds the developer's domain in no tracked file.

## 5. End-to-end on a physical device

- [x] 5.1 In device mode with `--seed alice`, confirm that `alice.test`'s DID document on PLC names `https://<pds-host>`, and that the relay lists the host and Jetstream emits a test record written with `create-account.sh`'s profile. Verify by curling PLC, `com.atproto.sync.listHosts` and the AppView's `getProfile`.
- [x] 5.2 Build the iOS app signed with a free Personal Team, install it on the iPad, sign in to `alice.test` on cellular, and save a manual activity. Verify that the AppView serves it in `alice.test`'s feed.
- [x] 5.3 Return to localhost mode (remove the device properties, `scripts/dev.sh --reset --seed alice`), then confirm that the iOS simulator and web sign in and the PDS reports `http://localhost:2583`. Verify with `./gradlew check`.

## Workflow follow-up

- The paced-outbox change's background-upload check on the iPad runs against this device mode (its session's follow-up, not this change's).

- Archive the change once the end-to-end run is done and the user has reviewed it.
