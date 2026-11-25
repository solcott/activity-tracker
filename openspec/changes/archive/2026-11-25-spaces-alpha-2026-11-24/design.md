# Design

## Context

- **The source.** Bluesky's guide `alpha-migrations/prompt-11-24-26.md` on the atproto `permissioned-data` branch, for `0.0.0-spaces-alpha-20261124235736` (atproto `1f510f8e` on `permissioned-data-alpha`). The URI change is proposals#146 and atproto#6010. The stored-URI rewrite follows `packages/pds/src/db/rewrite-space-uris.ts`.
- **Today.**
  - `SpaceUri` is `at://{did}/space/{type}/{skey}`.
  - `SpaceMember.getRecord` builds a record URI by appending to `space.value` and wraps it in `AtUri`, whose `init` requires `at://`.
  - The AppView stores the space URI as `space_sync.space_uri` and as each held row's `origin`, and compares them as exact strings: lookups, deletes, `dropSpace`, and the notify-token check.
  - Clients store it in `outbox.space`.
- **Earlier releases.** Everything the Nov 12 change applied still holds.

## Goals / Non-Goals

**Goals:**
- Every space flow works against the 2026-11-24 image without changing who can read or write.
- Nothing stored before the upgrade stops matching. A synced space stays synced, and a queued write goes to the same space.

**Non-Goals:**
- Reading the old form anywhere. Nothing of ours embeds a space URI in a record value, so there's nothing to read leniently (guide §1d).
- The guide's app-password, SDK, account-migration and archive sections (see the proposal).

## Decisions

1. **Rewrite stored values; parse strictly.**
   - Every stored space URI is ours to rewrite: AppView rows and the Room outbox.
   - So both migrations rewrite in place, and `SpaceUri` accepts only the new form. No code path carries a legacy reader that would need removing before beta.
   - Alternative: normalize old-form values where they're read. Rejected: exact-string matches (`space_sync` keys, `origin` deletes, the token lookup) would still miss unless every query normalized too.
2. **The rewrite is upstream's `toNewForm`.**
   - `'at:space/' || {DID} || {rest after '/space'}`, only `WHERE col LIKE 'at://%/space/%'`. A DID holds no `/`, so the first `/space/` ends it.
   - It's written with `SUBSTRING … FROM … FOR` and `POSITION`, which H2 in PostgreSQL mode and Postgres both run, and with `substr`/`instr` in SQLite.
   - A second run changes nothing, and `'repo'` never matches.
3. **A record URI is its own type.**
   - `SpaceRecordUri` (six parts after the prefix, repo a DID) replaces the `AtUri` the record MAC took, and `SpaceUri.record(repo, collection, rkey)` builds it. `AtUri` stays public-only.
   - The `getRecord` and `putRecord` outputs keep `uri` as text: only `SpaceMember.getRecord` reads it, comparing it exactly with the URI it asked for. Typing it would make every fake that answers both repo and space writes from one handler spell out a space record URI that nothing reads.
4. **Vectors from npm, as before.**
   - `@atproto/space` and `@atproto/crypto` at the release version install without `overrides` now that alpha packages pin each other exactly.
   - The shared secret is unchanged (same keys). Only the commit and record signatures change, because their headers carry the URIs.

## Risks / Trade-offs

- [A credential or registration minted before the upgrade carries an old-form `sub`] → Credentials last 5–10 minutes and are re-exchanged on expiry, and the AppView's registrations renew on its next reauthorization. The token stays bound to the rewritten row.
- [A client on an old build sends old-form URIs to `notifySpace`] → It gets `InvalidRequest` and its space is announced again by the next updated session. The AppView's reconcile picks up spaces it already syncs regardless.
- [The rewrite misses a column added later] → The guide's form is final, and any new `origin` column is written in the new form from the start.

## Migration Plan

1. Re-pin `pds-spaces-alpha` to the 2026-11-24 digest. The image migrates its account-manager and actor-store databases on start.
2. Deploy the AppView. Flyway V79 rewrites stored URIs before the syncer starts.
3. The app's Room database migrates 11 → 12 on next open.
4. Rollback means reverting the code and the image together. V79 and the PDS's own `010` migration aren't reversed, so an older AppView would refuse the rewritten rows: restore the database backup instead.
