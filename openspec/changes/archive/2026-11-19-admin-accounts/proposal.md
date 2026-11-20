# Proposal

## Why

When an athlete says "my activity isn't showing" or "my handle shows as invalid", an operator today has to read Postgres by hand to see what the AppView holds for them, and can fix it only by waiting for a background loop (the handle sweep, the Bluesky block backfill, the space syncer) or, since repo-reindex landed, with no way at all to start `RepoReindexer` on a running AppView. The admin site (admin/admin-site) now gives operators a signed-in, audited surface, so one account page can show what is indexed for an account and run the four repairs that exist.

## What Changes

- A new **Accounts** admin page (`/admin/accounts`), added through the `AdminPage` registry: look up an account by handle (verified handles only) or DID.
- The page shows, for that account, only identifiers, statuses, counts and times:
  - hosting status (`actors.status`: active, takendown, suspended, deactivated) and whether an `actors` row exists;
  - the stored handle, whether it is verified or `handle.invalid`, and when it is next checked (`handle_check_due_at`);
  - per indexed collection, how many records are held from the repo and from each of the account's spaces (by tier or `self`/`interactions`);
  - every `space_sync` row of the account (space, revision, last success, failures, last error, next attempt, not-found-since, registration expiry; never the notify token or set hash);
  - segment matching and grouping opt-ins as yes/no only;
  - Bluesky block backfill state (last backfilled, failures, next attempt, last error);
  - its reindex runs (`RepoReindexer.runs`) with state, phase, revision, counts, spaces queued, requester, times and error code, refreshing while a run is active.
- Four operator actions, each audited through `AdminAudit` with the DID as target, refused for viewers (`403`) and without the CSRF token:
  - **Re-verify handle now** (`Indexer.recheckHandle`, now returning its `HandleCheck`);
  - **Backfill Bluesky blocks now** (a new `BskyBlockBackfill.backfillNow(did)` sharing the loop's per-actor step);
  - **Reindex repo** (`RepoReindexer.request(did, operator, resyncSpaces)`);
  - **Resync spaces** (`SpaceSyncer.resyncAccount`), absent when the AppView syncs no spaces.
- `main` wires the page into `appView(adminPages = …)`, hoisting `BskyBlockBackfill` out of `startWorkers` so the loop and the page share one instance.
- `AdminPrivacyTest` covers the page for an account with non-public activities, comments, a segment track and private prefs.
- The local-stack check carried over from repo-reindex: records created and deleted while the AppView missed them come right after **Reindex repo**, and the run reads `succeeded` with matching counts. Then a browser check signed in as `siteadmin.test`.

No lexicon, XRPC or client change. No new table: everything shown is read from existing tables.

## Capabilities

### New Capabilities
- `admin/account-tools`: the admin site's account page: account lookup, what it shows about one account (and never shows), and the operator repairs it offers (handle re-verification, Bluesky block backfill, repo reindex, space resync).

### Modified Capabilities
None. The actions call behavior already specified in `identity/handle-verification`, `graph/blocks`, `admin/repo-reindex` and `spaces/appview-syncer` ("Forced resync of an account's spaces"); running them on request adds no rule to those specs.

## Impact

- `server/appview/.../admin/`: new `AccountsPage.kt` (page, fragments, actions) and `AccountSummary.kt` (the read model, privacy-safe by construction).
- `index/Indexer.kt`: `recheckHandle` returns the `HandleCheck` (or null with no `actors` row); `index/RepoHoldings.kt` and `index/SpaceRkeys.kt` gain count helpers used by the page.
- `sync/BskyBlockBackfill.kt`: `backfillNow(did)` beside `runDue`.
- `Application.kt`: page wiring and the shared `BskyBlockBackfill`.
- Tests: `AccountsPageTest`, `AdminPrivacyTest` (`pagesUnderTest`), `BskyBlockBackfillTest`.
- Merges beside the sibling admin changes (`admin-health`, `admin-job-queues`, `admin-segments`), which each add their own page to the same `adminPages` list in `main`.
