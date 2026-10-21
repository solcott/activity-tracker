---
name: local-stack
description: Bring up, seed, smoke-test and troubleshoot the local ATProto network (PLC, PDS, relay, Jetstream, Postgres, the Ozone labeler) plus the AppView. Use when the user wants to run the app end to end, create test accounts, or asks why records aren't showing up in the AppView or feed, or when bumping the relay or Jetstream pin.
argument-hint: [up | seed <name>... | check | moderate | debug | bump-pin]
---

# Local stack

Pipeline: client → **PDS** :2583 → **relay** :2470 → **Jetstream** :6008 → **AppView** :8081 (Postgres :5433). PLC is :2582. The labeler, **Ozone** :3010 (`moderation.test`), takes reports the PDS proxies to it and streams labels to the AppView. The `docker.md` rule explains why the relay and Jetstream share the PDS network namespace and carry patches.

Run docker compose as `docker compose -f docker/compose.yaml ...` from the repo root (it's pre-allowed in `settings.local.json`). Mode: `$ARGUMENTS` (default `check`).

## up

Prefer `scripts/dev.sh [--seed <name>]` (run it in the background on a cold start; the first Docker build takes minutes). It brings up Docker, the labeler (`docker/scripts/setup-labeler.sh`), the cloudflared tunnel, `local.properties`, the AppView, `adb reverse` and, after a tunnel URL change, the Android debug app, skipping whatever is already current. `scripts/dev.sh status` is the quickest health overview. The AppView logs to `.dev/appview.log` (each start gets a fresh file; the previous run is `.dev/appview.prev.log`). The manual steps:

1. `[[ -f docker/.env ]] || cp docker/.env.example docker/.env`
2. `docker compose -f docker/compose.yaml up -d`. The first run builds PLC, the relay and Jetstream from source and takes minutes, so run it in the background and wait for it rather than polling.
   Then `docker/scripts/publish-lexicons.sh` (creates `lexicons.test` if needed, publishes our space-type lexicons, prints its DID). Put that DID in `PDS_LEXICON_AUTHORITY_DID` in `docker/.env` and run `up -d` again; without it, OAuth sign-in fails with `invalid_scope`.
   Then `docker/scripts/setup-labeler.sh` (creates `moderation.test`, `mod.test` and `triage.test` with its app password, the label key and the labeler's DID document entries, writes `OZONE_SERVER_DID`, starts Ozone, prints the labeler DID). Put that DID in `activitytracker.labelerDid` in `local.properties` so the apps send reports to it.
3. Start the AppView: in Claude Code use the `appview` launch config (`preview_start {name: "appview"}`); elsewhere run `./gradlew :server-appview:run` as a background command. **Start it before writing records you want indexed.** On first run it starts at Jetstream's live tip, so earlier records are never indexed.
4. Web client: in Claude Code use the `web` launch config (`preview_start {name: "web"}`); elsewhere run `./gradlew :webApp:wasmJsBrowserDevelopmentRun --continuous` as a background command. Sign in from `127.0.0.1`/`localhost` with a `.test` handle.

## seed

`docker/scripts/create-account.sh <name>` creates `<name>.test` and writes a profile record. The password is `TEST_ACCOUNT_PASSWORD` in `docker/.env`; don't echo it into chat. Re-running for an existing name fails with a PDS "handle taken" error, which is harmless.

## check (smoke test, stop at the first failing stage)

```bash
curl -s localhost:2583/xrpc/_health                                   # PDS:   {"version":...}
curl -s localhost:2470/xrpc/_health                                   # relay: {"status":"ok"}
curl -s localhost:2470/xrpc/com.atproto.sync.listHosts                # relay knows localhost:2583, status "active"
curl -s 'localhost:2470/xrpc/com.atproto.sync.listRepos?limit=5'      # relay sees the accounts
docker compose -f docker/compose.yaml logs --tail 30 jetstream        # no repeated connect/backfill errors
curl -s localhost:8081/xrpc/_health                                   # AppView: {"status":{"ok":true}}
curl -s 'localhost:8081/xrpc/app.activitytracker.actor.getProfile?actor=alice.test'   # indexed end to end
```

The last call returning a profile with the seeded `displayName` proves the whole pipeline.

Labeler:

```bash
curl -s localhost:3010/xrpc/_health                                   # Ozone: {"version":...}
curl -s localhost:2582/$(sed -n 's/^OZONE_SERVER_DID=//p' docker/.env) | jq '.service, .verificationMethod'   # #atproto_labeler, #atproto_label
curl -s 'localhost:3010/xrpc/com.atproto.label.queryLabels?uriPatterns=*'   # {"labels":[...]}
```

Moderation end to end: `docker/scripts/moderation-smoke.sh` reports, blocks, opens a reported Followers activity as `mod.test`, and applies and negates `!hide` and `!takedown`, checking feeds, lookups (signed in and out) and photos at each step. It prints `PASS` or names the failing check.

## moderate

Ozone's web UI can't sign in to `.test` accounts locally (see the `docker.md` rule), so act through its API as the admin token. `source docker/.env` first; `OZONE=http://localhost:3010`, `ADMIN=(-u "admin:$OZONE_ADMIN_PASSWORD")`.

- **Queue:** `curl -s "${ADMIN[@]}" "$OZONE/xrpc/tools.ozone.moderation.queryStatuses?reviewState=tools.ozone.moderation.defs%23reviewOpen" | jq '.subjectStatuses[] | {subject, reviewState}'`.
- **Label** a record (`!hide`, `!takedown`, `nudity`, …; `negateLabelVals` removes): POST `tools.ozone.moderation.emitEvent` with `{"event":{"$type":"tools.ozone.moderation.defs#modEventLabel","createLabelVals":["!hide"],"negateLabelVals":[]},"subject":{"$type":"com.atproto.repo.strongRef","uri":"<at-uri>","cid":"<cid>"},"createdBy":"$OZONE_SERVER_DID"}`. For an account use `{"$type":"com.atproto.admin.defs#repoRef","did":"<did>"}`.
- **Takedown** (also emits `!takedown`): the same with `{"$type":"tools.ozone.moderation.defs#modEventTakedown"}`; `#modEventReverseTakedown` undoes it.
- **Add a moderator:** create the account with `create-account.sh`, then POST `tools.ozone.team.addMember` with `{"did":"<did>","role":"tools.ozone.team.defs#roleModerator"}` (or `#roleAdmin`, `#roleTriage`). `mod.test` is an admin already.

Spaces: `docker/scripts/spaces-smoke.sh alice` creates (or reuses) a space, writes, reads and deletes a record, and checks that an anonymous read is refused. It exits non-zero and names the failing call. `ProfileNotFound` with everything else healthy means the record was written before the AppView first connected. Seed a fresh account to confirm the live path works.

## debug: symptom → cause

| Symptom | Likely cause → fix |
|---|---|
| `listHosts` empty | `relay-crawl` didn't run or failed → `docker compose ... logs relay-crawl`, then `up -d relay-crawl` |
| Relay or Jetstream log `ssrf`/"public IP"/https-only errors | The patch didn't apply (pin drifted) → see bump-pin |
| Jetstream healthy but AppView indexes nothing | AppView not running, wrong `JETSTREAM_URL`, or the collection isn't in `Nsid.indexedCollections` |
| A record type indexes in tests but not live | Missing from `Nsid.indexedCollections` (the Jetstream filter), or the `Indexer` dropped it as undecodable (debug log "Skipping …") |
| AppView fails at startup with a Flyway checksum or validation error | A committed migration was edited → revert it and add a new `V<n+1>`, or (with the user's OK) drop the `appview-db` volume |
| AppView returns 401 | Service-auth JWT issue: `aud` must be the AppView DID (`did:web:localhost%3A8081`), and the `lxm` must match the NSID |
| Native sign-in fails | Tunnel URL changed → update `activitytracker.appviewPublicUrl`, restart the AppView, rebuild the app. On an Android device, run `adb reverse tcp:2582 tcp:2582` (and 2583, 8081) |
| Spaces `getSpaceCredential` says `UserNotAuthorized` and the AppView log has no `checkUserAccess` | The PDS can't reach the managing app: no tunnel (`--no-tunnel`), or the space names an old tunnel's `did:web` → run `scripts/dev.sh` and recreate the space. A 404 there means the AppView route is missing |
| OAuth fails with `invalid_scope: Unable to retrieve space declarations` | The PDS can't resolve the `space:` scope's space-type lexicon. Run `scripts/dev.sh`: it publishes the declarations from `lexicons.test` and sets `PDS_LEXICON_AUTHORITY_DID`. A lookup that failed earlier stays cached for 5 minutes, so wait it out or `docker compose ... restart pds` |
| Spaces call fails with `403 ScopeMissingError` | The session was signed in before the `space:` scope (or a space collection) was added → sign out and in again |
| PDS exits with `SQLITE_CANTOPEN` | The `/pds` volume isn't writable by `node` → the volume predates `pds.Dockerfile`'s `chown`; wipe it (ask first) |
| Reporting fails with `could not resolve proxy did service url` | The PDS's DID cache (`/pds/did_cache.sqlite`, an hour before it refreshes) holds `moderation.test`'s document from before its labeler entries → `docker/scripts/setup-labeler.sh` drops that entry |
| A report never reaches Ozone | The app was built without `activitytracker.labelerDid`, or with an old one → run `scripts/dev.sh` (it writes the property) and rebuild. `docker compose ... logs ozone` shows the request if it arrived |
| `ozone` logs `OZONE_SERVER_DID unset` | `setup-labeler.sh` hasn't run on this `docker/.env` → run `scripts/dev.sh` |
| Ozone answers a service-auth call with 500 `Could not parse pds from doc` | The caller's DID document isn't an account document (no handle or `#atproto_pds`), like the AppView's `did:web` |
| Physical iPhone can't sign in | Unsupported against the local stack (the PDS advertises `localhost`) |

Destructive resets (`down -v`, deleting volumes) wipe every local account and record. Ask the user first.

## bump-pin

For `INDIGO_COMMIT` in `docker/relay.Dockerfile` or `JETSTREAM_COMMIT` in `docker/jetstream.Dockerfile`:
1. Fetch the patched file at the new commit (`cmd/relay/relay/host_checker.go` / `internal/ingest/backfill/run.go`) and confirm the `sed` target text still exists verbatim.
2. If it moved, update both the `sed` expression and the `grep -q` assertion after it. The `grep` is what makes a stale patch fail the build instead of silently doing nothing.
3. Rebuild: `docker compose -f docker/compose.yaml build --no-cache relay` (or `jetstream`), then `up -d` and run **check**.
