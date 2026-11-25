# Proposal

## Why

Bluesky's 2026-11-24 Spaces alpha moves space URIs from `at://{did}/space/{type}/{skey}[/{repo}/{collection}/{rkey}]` to `at:space/{did}/{type}/{skey}[/…]`, and the upgraded PDS emits and accepts only the new form, in every `space` parameter, record `uri`, delegation-token `sub`, the MAC headers and STAR metadata. Against it, `SpaceUri` refuses every space the PDS names, `SpaceMember.getRecord` throws building a record URI, and every space the AppView and clients have stored stops matching what hosts send. No non-public data would be readable or synced.

## What Changes

- **BREAKING. Space URIs are `at:space/`.**
  - `SpaceUri` builds and accepts only `at:space/{did}/{type}/{skey}`, refusing the old form, a handle authority and any other length.
  - A new `SpaceRecordUri` names a record in a space. `SpaceMember.getRecord` and the record MAC check use it, so the `Atproto-Space-Record` `uri` must be the new form.
  - `getRecord` and `putRecord` outputs keep their `uri` as text; only the member read checks it.
  - Delegation-token `sub`, `notifySpace`, `checkAccess`, the syncer callbacks and `listRepoOps`' commit follow `SpaceUri`.
- **Stored URIs rewritten.**
  - AppView Flyway `V79__space_uri_new_form.sql` rewrites `space_sync.space_uri` and every `origin` column, touching only old-form values. Each space keeps its `notify_token` with its row.
  - Room `MIGRATION_11_12` does the same for `outbox.space`.
- **Vectors.** `SpaceMacVectors` are regenerated from `@atproto/space` and `@atproto/crypto` `0.0.0-spaces-alpha-20261124235736`, with new-form URIs.
- **Local stack.**
  - `pds-spaces-alpha` is re-pinned to the 2026-11-24 build, atproto `1f510f8e`. The image migrates its own databases on start.
  - The smoke and seed scripts build new-form URIs.
- **Lexicons:** `notifySpace`'s description gives the new form. No shape changes.

Not applied, with evidence:
- **App passwords refused on space methods:** every script signs in with the account password. The segment importer and Ozone triage use app passwords but call no space method.
- **The npm SDK changes:** client code uses no atproto SDK, by the hard rule.
- **Account migration, archives, `reserveKemKey` and the `#atproto_kem` activation check:** nothing here migrates accounts or imports archives.
- **Lenient reading of old-form URIs in record values:** no record of ours embeds a space URI. Likes, comments, invites and `copyOf` use public-style `at://{did}/{collection}/{rkey}`.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `spaces/client-protocol`: space and space-record URIs in the `at:space/` form only; the owner-write scenario's URI.
- `spaces/appview-syncer`: stored space URIs are rewritten to the new form once.
- `profile/private-details`: the tier space's URI in the first-use scenario.
- `dev/local-stack`: the local PDS serves the 2026-11-24 alpha.

## Impact

- **`core/lexicon`:** `atproto/Space.kt` (`SpaceUri`, `SpaceRecordUri`, the `getRecord`/`putRecord` outputs), `SpaceSerializationTest`, `RecordSerializationTest`, `lexicons/app/activitytracker/space/notifySpace.json`.
- **`core/atproto/space`:** `SpaceMember`, `SpaceMac`, `SpaceMacHost`, plus `SpaceMacVectors`, `SpaceMacTest`, `SpaceFixtures` and `SpaceClientTest`.
- **`core/data`:** `AppDatabase` (version 12, `MIGRATION_11_12`, `schemas/12.json`), `MigrationTest`, and the fakes that answer space URIs.
- **Presenter fixtures:** `app/common/presenter-testing` and the feature presenter tests' literals.
- **AppView:** `V79__space_uri_new_form.sql` and `SpaceUriMigrationTest`.
- **Docker:** `docker/pds.Dockerfile` (digest and header), `spaces-smoke.sh`, `syncer-smoke.py`, `seed-effort.sh`, `moderation-smoke.sh`.
- **Docs:** `.agents/rules/docker.md`, `.agents/rules/data-and-auth.md`.
- **Data:**
  - Both migrations are idempotent.
  - Upgrading the local PDS in place works, because the image migrates its own databases. Wiping and reseeding still works.
  - Credentials and registrations minted before the upgrade carry old-form `sub`s and fail, so they're renewed on the next authorization.
