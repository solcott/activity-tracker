# Proposal

## Why

Private profile details and activity streams are moving into atproto Spaces (permissioned repos on the athlete's PDS). See the BACKLOG items "Move private profile details to an atproto Space" and "Move activity streams to a Space before going live". The AppView will act as each space's *managing app*: when a viewer asks for a space credential, the athlete's PDS calls the AppView's `com.atproto.simplespace.checkUserAccess`. None of that can be built or tested until the local stack runs a Spaces-capable PDS, and that PDS can resolve and reach the AppView. This change is the first of six. It also runs a spike that pins down how the alpha actually behaves, because the spec changes weekly and the later changes depend on those details.

## What Changes

- **Spaces-capable local PDS.** The local PDS image moves from `ghcr.io/bluesky-social/pds:0.4` to Bluesky's `ghcr.io/bluesky-social/atproto:pds-spaces-alpha`, pinned by digest. It runs as `linux/amd64`, because the image is amd64-only. The existing checked `sed` patch that makes the OAuth CSRF cookie non-Secure still applies, re-targeted to the new image's layout.
- **The AppView advertises itself as a managing app.** Its `did:web` document gains an `AtprotoSpaceService` service entry (`#activitytracker_space`) pointing at the AppView's public URL. The AppView's own `did:web` plus that fragment is the `managingApp` value that later changes put in each space's policy.
- **The PDS can reach the AppView.** Spaces flows need the AppView addressed by its tunnel `did:web` (the default `scripts/dev.sh` mode), because `did:web:localhost%3A8081` resolves to the PDS container itself. `--no-tunnel` stays supported for non-Spaces work, and the docs say that Spaces needs the tunnel.
- **Spaces smoke script.** `docker/scripts/spaces-smoke.sh` signs in with a local test account's password session and does `simplespace.createSpace`, `getSpace`, `space.putRecord`, `space.getRecord` and `space.deleteRecord`. This proves the image serves Spaces.
- **Spike (throwaway, not committed).** It exercises the full delegation-token → `getSpaceCredential` → member read round trip. That includes the managing-app callout reaching the AppView, the accepted `space:` OAuth scope string, how a blob attaches to a space record, and how a `public` read policy behaves. The findings are recorded in this change's `design.md`.
- **BREAKING (local dev only).** The new PDS image may not read the old PDS database, so the local stack's volumes are wiped as part of the switch. Local accounts must be re-created (`scripts/dev.sh --seed alice`).

## Capabilities

### New Capabilities
- `spaces/managing-app`: How the AppView identifies itself to PDSes as the managing app for athletes' spaces. This change adds the DID-document service entry; later changes add `checkUserAccess`.
- `dev/local-stack`: What the local development stack guarantees: a Spaces-capable PDS, sign-in on every client including iOS, and a smoke check that the PDS serves Spaces.

### Modified Capabilities
<!-- None: no existing spec covers the local stack or Spaces. -->

## Impact

- **Docker:** `docker/pds.Dockerfile` and `docker/compose.yaml` (the `pds` service gets an image, a platform and possibly env vars). New `docker/scripts/spaces-smoke.sh`.
- **AppView:** `server/appview/.../api/Routes.kt` (`wellKnownRoutes` did.json) and possibly `AppViewConfig.kt` (the managing-app service id). Tests in `server/appview/src/test`.
- **Docs and agent config:** `.agents/rules/docker.md`, the `local-stack` skill, README local setup, and `CLAUDE.md` if commands change.
- **Dev experience:** the PDS runs under amd64 emulation on Apple Silicon, so startup is slower, and existing local data is wiped.
- **No client, lexicon or AppView data-model changes** in this step.
