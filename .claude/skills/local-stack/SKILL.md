---
name: local-stack
description: Bring up, seed, smoke-test and troubleshoot the local ATProto network (PLC, PDS, relay, Jetstream, Postgres) plus the AppView. Use when the user wants to run the app end to end, create test accounts, or asks why records aren't showing up in the AppView or feed, or when bumping the relay or Jetstream pin.
argument-hint: [up | seed <name>... | check | debug | bump-pin]
---

# Local stack

Pipeline: client → **PDS** :2583 → **relay** :2470 → **Jetstream** :6008 → **AppView** :8081 (Postgres :5433). PLC is :2582. The `docker.md` rule explains why the relay and Jetstream share the PDS network namespace and carry patches.

Run docker compose as `docker compose -f docker/compose.yaml ...` from the repo root (it's pre-allowed in `settings.local.json`). Mode: `$ARGUMENTS` (default `check`).

## up

Prefer `scripts/dev.sh [--seed <name>]` (run it in the background on a cold start; the first Docker build takes minutes). It brings up Docker, the cloudflared tunnel, `local.properties`, the AppView, `adb reverse` and, after a tunnel URL change, the Android debug app, skipping whatever is already current. `scripts/dev.sh status` is the quickest health overview. The manual steps:

1. `[[ -f docker/.env ]] || cp docker/.env.example docker/.env`
2. `docker compose -f docker/compose.yaml up -d`. The first run builds PLC, the relay and Jetstream from source and takes minutes, so run it in the background and wait for it rather than polling.
3. Start the AppView with the `appview` launch config (`preview_start {name: "appview"}`), or `./gradlew :server-appview:run` in the background. **Start it before writing records you want indexed.** On first run it starts at Jetstream's live tip, so earlier records are never indexed.
4. Web client: the `web` launch config. Sign in from `127.0.0.1`/`localhost` with a `.test` handle.

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

The last call returning a profile with the seeded `displayName` proves the whole pipeline. `ProfileNotFound` with everything else healthy means the record was written before the AppView first connected. Seed a fresh account to confirm the live path works.

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
| Physical iPhone can't sign in | Unsupported against the local stack (the PDS advertises `localhost`) |

Destructive resets (`down -v`, deleting volumes) wipe every local account and record. Ask the user first.

## bump-pin

For `INDIGO_COMMIT` in `docker/relay.Dockerfile` or `JETSTREAM_COMMIT` in `docker/jetstream.Dockerfile`:
1. Fetch the patched file at the new commit (`cmd/relay/relay/host_checker.go` / `internal/ingest/backfill/run.go`) and confirm the `sed` target text still exists verbatim.
2. If it moved, update both the `sed` expression and the `grep -q` assertion after it. The `grep` is what makes a stale patch fail the build instead of silently doing nothing.
3. Rebuild: `docker compose -f docker/compose.yaml build --no-cache relay` (or `jetstream`), then `up -d` and run **check**.
