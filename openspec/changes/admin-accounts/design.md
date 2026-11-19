# Design

## Context

See proposal.md for why. What exists, in `server/appview/src/main/kotlin/io/github/solcott/activitytracker/appview/`:

- **Admin site** (`admin/`): `AdminPage` (path, title, `AdminRoutes.install()`, `privacyProbes(subject)`), `AdminRoutes.get` for pages and htmx fragments and `AdminRoutes.action(path, name)`, which already refuses viewers (`403`, audited `refused: viewer`), cross-origin posts and a missing CSRF token, runs the body, and writes `AdminAudit.record(admin, name, target, params, outcome)` with `ok` or `failed: <class>` in a `finally`. `ActionCall.target` is the form's `target` field, `params` the rest. `AuditPage` is the model page. `AdminSite(pages = …)` puts operator pages between Home and the audit log; `appView(adminPages = …)` passes them, and `main` passes none yet.
- **Accounts**: `Actors` holds `handle` (a verified handle or `handle.invalid`), `status` (null while active), `handle_check_due_at`, `bsky_blocks_synced_at`. `BskyBlockBackfillFailures` holds failures, `last_error` (`ErrorSummary`) and `next_attempt_at`. `SegmentPrefs.matching`, `GroupPrefs.mode` (on only for `GroupMode.ON`).
- **What is indexed**: `index/RepoHoldings.kt` lists, per collection in `Nsid.indexedCollections`, the rkeys held from an account's repo (`repoHoldings(author, collection)`); `index/SpaceRkeys.kt` lists the rkeys held from a space (`spaceRkeys(space, collection)`) for the collections `Indexer.spaceCollections(skey)` names. Both fail for a collection they don't list, so a count built on them can't silently miss one.
- **Repairs**: `Indexer.recheckHandle(did)` (returns early with no `actors` row; stores `handleOf(check)` and `nextHandleCheck`), `BskyBlockBackfill.runDue()` (per-actor `backfill` then stamp or record a failure; built inside `startWorkers`), `RepoReindexer.request(did, requestedBy, resyncSpaces)` / `runs(did)` (one active run per DID), `SpaceSyncer.resyncAccount(did): Int` (syncer is null without `APPVIEW_SIGNING_KEY`).
- `SpaceSync` also holds `notify_token` (a secret) and `set_hash`, which the page must never print.

## Goals / Non-Goals

**Goals:**
- One page that answers "what does the AppView hold for this account, and is it healthy", and the four repairs, with no new table or migration.
- Privacy by construction: the page reads into a summary type that has no field able to carry record content.

**Non-Goals:**
- Listing or searching many accounts, or any account content (moderators use `moderation.getSubject`).
- Changing `actors.status`, deleting an account, or editing any record.
- Worker-level status (admin-health) and segment job queues (admin-job-queues).

## Decisions

1. **One `AccountsPage` at `accounts`, keyed by DID in the URL.** `GET /admin/accounts` shows the lookup form; `GET /admin/accounts?q=…` resolves the input and redirects to `/admin/accounts/{did}`, so the page is linkable and the audit log's target (the DID) matches. Fragments `accounts/{did}/runs` (the reindex runs) and `accounts/{did}/spaces` are separate so htmx can refresh them. `privacyProbes(subject)` returns the page and both fragments for a DID subject, and the lookup URL for a handle. *Alternative:* one page with the DID in a query string; rejected because fragments and redirects read cleaner by path.

2. **Lookup by stored verified handle only.** A handle is lower-cased, a leading `@` dropped, checked against the handle pattern, `handle.invalid` refused, then matched against `actors.handle`. The page doesn't resolve arbitrary handles over the network: an operator with an unindexed account pastes its DID (which reindex accepts). This keeps a page load from making outbound requests on an input. *Alternative:* resolve through `IdentityResolver`; deferred (see Open Questions).

3. **`AccountSummary` as the only read model.** `AccountSummary.load(db, did, spacesSynced)` returns a data class of DIDs, space URIs, enum-like statuses, booleans, counts, epoch times, revisions and `ErrorSummary` strings, and nothing else; the page renders only that. Opt-ins are `Boolean` (`SegmentPrefs.matching`; `GroupPrefs.mode in GroupMode.ON`), so the mode and `hideFromLeaderboards` never reach the page. Space rows select only the columns shown, never `notify_token` or `set_hash`. Recorded errors are already `ErrorSummary`, which drops decoder messages that quote input (dev/worker-telemetry).

4. **Counts reuse the reindex and syncer listings.** Repo counts are `repoHoldings(did, c).size` for every `repoHoldingCollections` entry; space counts are `spaceRkeys(space, c).size` for each `c` in `Indexer.spaceCollections(space.skey)`, over the account's `space_sync` rows plus its four standard athlete spaces (so records still held from a space that stopped syncing are counted). Collections with zero everywhere are left out of the table. Small `repoHoldingCount`/`spaceRkeyCount` helpers wrap these so a later change can swap in `COUNT(*)` without touching the page. *Alternative:* hand-written `COUNT` per table; rejected because it would duplicate the origin rules those two files already own and could drift from them.

5. **`recheckHandle` returns what it found.** It becomes `suspend fun recheckHandle(did): HandleCheck?` (null with no `actors` row); callers ignoring the result are unchanged. The action reports `verified <handle>`, `invalid` or `unreachable`, read back with the new due time. No new verification rule: the sweep and the action run the same code.

6. **`BskyBlockBackfill.backfillNow(did): Int`.** The per-actor step in `runDue` (backfill, stamp and clear the failure row, or upsert the failure with the doubled pause) moves into one private function both call, so a forced run records exactly as a scheduled one; `backfill` returns the number of blocks it put. It runs whether or not the actor is stamped or backing off, and throws after recording on failure, so the admin site audits `failed: <class>`. With no `actors` row it reports "not indexed" and does nothing. `main` builds the instance once and passes it to both `startWorkers` (through `WorkerInputs`) and the page.

7. **Actions post to the page with the DID as `target`.** `accounts/recheck-handle` (`account.recheckHandle`), `accounts/backfill-blocks` (`account.backfillBlocks`), `accounts/reindex` (`account.reindex`, param `resyncSpaces=true|false`, a checkbox defaulting on), `accounts/resync-spaces` (`account.resyncSpaces`). Each parses `target` as a DID (refusing anything else with a failure, still audited) and returns an `ActionResult` with identifiers and counts only: the handle check, the block count, `run <id> <state>` (saying when an existing active run was returned), `<n> spaces queued`. Forms use `hx-post` with `hx-target` on an outcome slot; the reindex form also triggers a refresh of the runs fragment. Controls are drawn only for operators (`admin.isOperator`), and "Resync spaces" and the reindex form's checkbox only when a syncer exists.

8. **Runs fragment polls only while active.** It renders `reindexer.runs(did)` (default limit) and, when any run is queued or running, carries `hx-get` of itself with `hx-trigger="every 3s"` and `hx-swap="outerHTML"`; once none is active the returned fragment has no trigger, so polling stops. No inline script, matching the site's CSP and htmx config (`allowEval:false`).

9. **Page dependencies are passed in, not reached for.** `AccountsPage(db, indexer, blockBackfill, reindexer, syncer?)`; tests build it with H2, a lambda `HandleLookup`, a mock-engine `HttpClient` for the backfill, and a `RepoReindexer` whose worker loop isn't started, so a request stays `queued` and is observable.

## Risks / Trade-offs

- [Counting by listing rkeys loads every rkey of an account into memory] → bounded to one account per page load; the count helpers let a later change move to `COUNT(*)` if a large account makes it slow.
- [A reindex or backfill action of a large account takes long] → reindex only queues (the worker runs it); backfill pages through `listRecords` like the loop, and the request's own timeout bounds it. The outcome is still audited if the browser gives up.
- [Space error text could quote something private] → it is `ErrorSummary` text, already the rule for every stored failure; the privacy test seeds a failure row too.
- [Merge conflict with sibling admin changes on `main`'s `adminPages` list and `AdminPrivacyTest.pagesUnderTest`] → each adds one list entry; resolve by keeping all.
- [Showing per-tier counts reveals that an athlete has, say, Only me activities] → allowed by admin-site's privacy rule ("may show how many activities are indexed"), and only to configured admins.

## Migration Plan

No migration, table or config. Deploy with the AppView; rollback is removing the page from `adminPages`. The `recheckHandle` return type change is source-compatible for existing callers.

## Open Questions

- Should lookup by handle also resolve a handle the AppView hasn't indexed (through the guarded `IdentityResolver`), for accounts it never saw? Pasting the DID covers it for now.
