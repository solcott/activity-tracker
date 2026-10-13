---
paths:
  - "docker/**"
---

# Local ATProto stack

- Data flows PDS → relay (indigo) → Jetstream → AppView. Jetstream bootstraps from the relay's `listHosts`/`listRepos`, so it can't read a PDS directly.
- The PDS serves HTTP only when `PDS_HOSTNAME=localhost`, so the relay and Jetstream use `network_mode: "service:pds"` and their ports are published on the `pds` service. Keep it that way: what DID docs say (`localhost:2583`) has to resolve inside Docker too.
- The relay and Jetstream are built from pinned commits with one-line `sed` patches that relax their SSRF guards for `localhost`. Each `sed` is followed by a `grep -q` of the result, so a drifted target fails the build instead of silently not patching. To bump `INDIGO_COMMIT` or `JETSTREAM_COMMIT`, re-check that the patched file and line still exist upstream at the new commit and update both the `sed` and the `grep`.
- The PDS (`pds.Dockerfile`) is Bluesky's atproto Spaces alpha (`ghcr.io/bluesky-social/atproto:pds-spaces-alpha`), pinned by digest because the alpha breaks things under the same tag.
  - **amd64 only.** Compose runs it with `platform: linux/amd64`, under Rosetta on Apple Silicon. It still becomes healthy in about 10 s.
  - **Runs as `node`.** It's the atproto monorepo (`/app/packages/...`), not the `pds` distribution image. So the Dockerfile patches as `USER root` and creates `/pds` owned by `node`; without that, a fresh volume fails with `SQLITE_CANTOPEN`.
  - **CSRF patch.** The same kind of checked `sed` makes the OAuth CSRF cookie non-Secure, because WebKit drops Secure cookies on `http://localhost` and iOS sign-in then fails with "Missing CSRF header". A `find` must match exactly one `oauth-provider/dist/router/assets/csrf.js`.
  - **Bumping.** Re-check the patch path and its `secure: true,` line, wipe the volumes (schemas change without migrations), and run `docker/scripts/spaces-smoke.sh alice`.
- Spaces' managing-app callout (`checkUserAccess`) goes from the PDS to the AppView's `did:web`, which only resolves to the AppView through the `scripts/dev.sh` tunnel. `did:web:localhost%3A8081` is the PDS container itself. A tunnel restart changes that DID, so spaces created earlier name a managing app that no longer answers. Recreate them, since local space data is throwaway.
- Public requestCrawl rejects `localhost`, so the `relay-crawl` one-shot registers the PDS through the relay admin API (`admin:$RELAY_ADMIN_PASSWORD`).
- `docker/.env` holds dev-only secrets and is git-ignored. `.env.example` is the template, so add new variables there.
- `create-account.sh` hard-codes the `app.activitytracker.actor.profile` NSID. Keep it in step with `Nsid.NAMESPACE`.
