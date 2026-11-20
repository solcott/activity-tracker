# Proposal

## Why

The AppView learns of repo records only from Jetstream and of non-public data only from space syncs, so an event it missed (an outage longer than the cursor rewind, a Jetstream gap, a bug fixed since) leaves an account's indexed state wrong for good, and today the only repair is `psql`. The coming `admin-accounts` page needs a safe way for an operator to rebuild one account's indexed state from the source of truth: its repo and its spaces.

## What Changes

- A new internal AppView service reindexes one account: it resolves the account's PDS from its DID document, fetches the whole repo with `com.atproto.sync.getRepo` through the guarded HTTP client, checks the commit and every block it uses, and runs the existing idempotent indexer over every record in `Nsid.indexedCollections`.
- Repo records the AppView has indexed for the account that the fetched repo no longer holds are deleted, as a delete from the repo would delete them (shadows promoted, derived rows dropped). Nothing held from a space is touched by this step.
- Live Jetstream commits for the account that arrive during a reindex are reconciled by revision: a commit newer than the fetched repo wins over the snapshot, and a commit the snapshot already covers is not applied over it.
- The same request forces an immediate full resync (a backfill, not a pull) of every space the AppView syncs for the account through the existing space syncer, and asks after the account's standard athlete spaces it doesn't know yet.
- Bounded work: a configurable maximum repo size, block count and wall-clock time; one reindex per account at a time and a small global cap, with further requests queued.
- Takendown, suspended or deactivated accounts: the stored account status is never changed by a reindex; a PDS that refuses the repo ends the run as unavailable and changes nothing.
- Each run's progress and outcome (state, phase, counts, revision, error) is recorded in a new table so the admin page can show it; it holds counts and identifiers only, never record content.
- Exposed only as a Kotlin service for `admin-accounts`' operator actions to call. No XRPC method, no route, no lexicon change.
- No maintained CAR/IPLD library exists on Maven Central, so CAR reading and the repo tree walk are written in the AppView over the Jackson CBOR it already uses (see design.md).

## Capabilities

### New Capabilities
- `admin/repo-reindex`: rebuilding one account's indexed state on an operator's request: fetching and checking its repo, indexing it, removing what it no longer holds, reconciling with live events, limits and concurrency, inactive accounts, and the recorded progress and outcome of each run.

### Modified Capabilities
- `spaces/appview-syncer`: a forced resync of all of one account's spaces, which backfills each of them at once instead of waiting for its next reconcile, and announces its standard spaces the AppView doesn't sync yet.

## Impact

- **AppView** (`server/appview`): a new `reindex` package (CAR reader, DAG-CBOR to atproto-JSON decoding, repo tree walk, commit check, the service and its worker); `Indexer` gains a repo-placement entry point for snapshot records, a listing of what it holds from an account's repo for every indexed collection, and a hook that lets Jetstream commits and a running reindex see each other; `JetstreamEvent.Commit` reads the commit's `rev`; `SpaceSyncer` gains `resyncAccount`; `AppViewConfig` gains the reindex limits; a Flyway migration adds `repo_reindexes`.
- **Dependencies:** none new (Jackson CBOR and BouncyCastle are already on the AppView's classpath).
- **Builds on:** `admin-site` (the audit log and operator role are the caller's), and `worker-telemetry` (the reindex loop is a worker there, `repo-reindexer`). `admin-accounts` adds the buttons.
- No client, lexicon, iOS or public API change.
