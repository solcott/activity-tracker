# Tasks

## 1. AppView managing-app identity

- [x] 1.1 Add a derived `managingAppService` (`"$serviceDid#activitytracker_space"`) to `AppViewConfig`. Verify with a config test that it follows `APPVIEW_DID` and `APPVIEW_PUBLIC_URL`.
- [x] 1.2 Add the `#activitytracker_space` / `AtprotoSpaceService` / `publicUrl` service to `wellKnownRoutes` in `api/Routes.kt`, keeping `#activity_appview`. Verify by extending the did.json test in `AppViewTest.kt` (around line 572) to assert both entries, then run `./gradlew :server-appview:test`.

## 2. Spaces-capable PDS image

- [x] 2.1 Resolve the current `ghcr.io/bluesky-social/atproto:pds-spaces-alpha` digest (`docker buildx imagetools inspect`) and switch `docker/pds.Dockerfile` to `FROM ...@sha256:<digest>`, with the tag and date in a comment. Verify that `docker compose -f docker/compose.yaml build pds` succeeds.
- [x] 2.2 Re-target the CSRF patch with a `find` for `@atproto/oauth-provider/dist/router/assets/csrf.js` that fails on zero or multiple matches, followed by `sed` and `grep -q` (design D3). Verify the build passes, and that pointing the `find` at a bogus path makes it fail.
- [x] 2.3 Add `platform: linux/amd64` to the `pds` service in `docker/compose.yaml`, and raise the healthcheck `retries` if emulated startup needs it. Verify that `docker compose ps` shows `pds` healthy on this arm64 Mac, and note the startup time.
- [x] 2.4 Wipe and recreate the local stack (`scripts/dev.sh down`, `docker compose -f docker/compose.yaml down -v`, `scripts/dev.sh --seed alice`, `docker/scripts/create-account.sh bob`). Verify that the relay and Jetstream index alice's profile and the AppView shows her in `actor.searchActors`.
- [x] 2.5 Verify OAuth sign-in against the new PDS on desktop (hot-reload MCP), on Android, and on iOS in the simulator, where it must not show "Missing CSRF header".

## 3. Spaces smoke script

- [x] 3.1 Write `docker/scripts/spaces-smoke.sh <name>`, following `create-account.sh`'s `post()` and `.env` pattern: `createSession`, `simplespace.createSpace` (continuing if the space already exists), `getSpace`, `space.putRecord`, `space.getRecord` (asserting the value round-trips) and `space.deleteRecord`. Each failure names the call and exits non-zero. Verify that it passes twice in a row for alice. If password sessions are refused, apply the app-password fallback from D6 and record it.
- [x] 3.2 Verify the failure path by pointing the script at a URL that doesn't serve Spaces. It must exit non-zero and name the failing call. (Done against the relay port and a closed port: both stop at `createSession`. With no non-Spaces PDS left on the stack, the `createSpace`-specific failure isn't exercised.)

## 4. Tunnel requirement

- [x] 4.1 Make `scripts/dev.sh --no-tunnel` print a one-line note that Spaces managing-app callouts need the tunnel. Verify by running `scripts/dev.sh --no-tunnel status`, or the equivalent path, and seeing the note.

## 5. Spike (throwaway, not committed)

- [x] 5.1 In the session scratchpad, set up a Node script for the spike. Verify it can `createSession` for alice and bob. (The `@atproto/*` alpha packages won't install with npm, because of their `workspace:*` dependencies. The script instead ports `@atproto/space`'s signature code, read from the pinned image, onto WebCrypto. See the note at the top of the design's findings.)
- [x] 5.2 Run S1 (member round trip via delegation token, `getSpaceCredential` and `getRecord`) and S7 (TTL and error codes). Record findings S1 and S7 in `design.md`.
- [x] 5.3 Run S2: create a space with a `managingAppPolicy` naming `<AppView did:web>#activitytracker_space`, request a credential as bob, and confirm the AppView log shows the `checkUserAccess` request (a 404 is expected). Record finding S2. If the PDS blocks the callout, record the needed patch before changing anything.
- [x] 5.4 Run S3 (an accepted `space:` scope string with `transition:generic`), S4 (blobs on space records and their visibility), S5 (public read policy) and S6 (owner reads with `read_self`). Record each finding in `design.md`.
- [x] 5.5 Review findings S1 to S7 against the plan for changes 2 to 5 (`~/.claude/plans/atprocol-spaces-is-close-shimmying-zebra.md`). Write any change of approach into this design's Decisions, or as an open question for the affected change. Verify that no finding is left _pending_.

## 6. Documentation

- [x] 6.1 Update `.agents/rules/docker.md`: the alpha image and digest pinning, amd64 emulation, the new CSRF `find`, the tunnel requirement for Spaces, and that tunnel restarts change the managing-app DID. Verify that `scripts/check-agent-sync.sh` passes.
- [x] 6.2 Update the `local-stack` skill (`.agents/skills/local-stack/SKILL.md`) with the smoke script and a troubleshooting row for "checkUserAccess never arrives" (no tunnel, or SSRF). Update README's local setup about the volume wipe and the slower amd64 PDS. Verify that the documented commands run as written.

## 7. Integration check

- [x] 7.1 On a fresh `scripts/dev.sh --seed alice` stack, run `./gradlew check` and `docker/scripts/spaces-smoke.sh alice`, and record a successful feed load on desktop. Verify that all three pass.

## Workflow follow-up

- Run `openspec validate spaces-local-stack --strict` before archiving.
- Archive with `/opsx:archive` once merged, then propose `spaces-client-protocol`, folding in spike findings S1, S3 and S7.
