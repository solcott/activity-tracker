---
paths:
  - "docker/**"
---

# Local ATProto stack

- Data flows PDS → relay (indigo) → Jetstream → AppView. Jetstream bootstraps from the relay's `listHosts`/`listRepos`, so it can't read a PDS directly.
- The PDS serves HTTP only when `PDS_HOSTNAME=localhost`, so the relay and Jetstream use `network_mode: "service:pds"` and their ports are published on the `pds` service. Keep it that way: what DID docs say (`localhost:2583`) has to resolve inside Docker too.
- The relay and Jetstream are built from pinned commits with one-line `sed` patches that relax their SSRF guards for `localhost`. Each `sed` is followed by a `grep -q` of the result, so a drifted target fails the build instead of silently not patching. To bump `INDIGO_COMMIT` or `JETSTREAM_COMMIT`, re-check that the patched file and line still exist upstream at the new commit and update both the `sed` and the `grep`.
- The PDS (`pds.Dockerfile`) is the pinned `pds:0.4` image with the same kind of checked `sed`: its OAuth CSRF cookie is made non-Secure, because WebKit drops Secure cookies on `http://localhost` and iOS sign-in then fails with "Missing CSRF header". When bumping the image, re-check the `csrf.js` path and its `secure: true,` line.
- Public requestCrawl rejects `localhost`, so the `relay-crawl` one-shot registers the PDS through the relay admin API (`admin:$RELAY_ADMIN_PASSWORD`).
- `docker/.env` holds dev-only secrets and is git-ignored. `.env.example` is the template, so add new variables there.
- `create-account.sh` hard-codes the `app.activitytracker.actor.profile` NSID. Keep it in step with `Nsid.NAMESPACE`.
