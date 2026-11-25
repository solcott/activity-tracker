# Tasks

## 1. Image and reference material

- [x] 1.1 Resolve the `ghcr.io/bluesky-social/atproto:pds-spaces-alpha` digest built on 2026-11-24 (revision `1f510f8e`) and pin it in `docker/pds.Dockerfile` with the date, commit and release. Verify `docker compose -f docker/compose.yaml build pds` succeeds with the CSRF patch's checks intact.

## 2. Wire types (`core/lexicon`)

- [x] 2.1 `SpaceUri` in the `at:space/` form only, plus `SpaceRecordUri` and `SpaceUri.record`; `getRecord`/`putRecord` outputs keep `uri` as text (design D3). Verify `SpaceSerializationTest` round-trips both lengths and refuses the old form, a handle authority and other lengths.
- [x] 2.2 Update `notifySpace.json`'s description and `RecordSerializationTest`.

## 3. Client (`core/atproto`, `core/data`)

- [x] 3.1 `SpaceMember.getRecord`, `SpaceMac.forRecord` and `SpaceMacHost.recordMacHeaders` use `SpaceRecordUri`.
- [x] 3.2 Regenerate `SpaceMacVectors` from the npm release (design D4).
- [x] 3.3 Room version 12 with `MIGRATION_11_12` rewriting `outbox.space` (design D2). Verify `MigrationTest` finds an old-form row rewritten and new-form and repo rows untouched.
- [x] 3.4 Update the old-form literals in the core-data fakes and the presenter fixtures. Verify `:core-atproto:jvmTest`, `:core-data:jvmTest` and the presenter tests pass.

## 4. AppView

- [x] 4.1 Flyway `V79__space_uri_new_form.sql` over `space_sync.space_uri` and every `origin` column (design D2). Verify `SpaceUriMigrationTest` and `./gradlew :server-appview:test`.

## 5. Local stack and docs

- [x] 5.1 New-form URIs in `spaces-smoke.sh`, `syncer-smoke.py`, `seed-effort.sh` and `moderation-smoke.sh`.
- [x] 5.2 Update `.agents/rules/docker.md` and `.agents/rules/data-and-auth.md`. Verify `scripts/check-agent-sync.sh` passes.

## 6. Integration checks

- [x] 6.1 `./gradlew check` passes.
- [x] 6.2 On the upgraded shared stack (the PDS bumped in place, the AppView migrated by V79), `spaces-smoke.sh`, `syncer-smoke.sh` and `moderation-smoke.sh` pass.

## Workflow follow-up

- Archive the change with `/opsx:archive` once the tasks are done and the specs are synced.
