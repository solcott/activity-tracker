# Tasks

## 1. CAR and DAG-CBOR reading (`server/appview`, new `ipld` and `reindex` packages)

- [x] 1.1 Write a CIDv1 reader/encoder (varint version, codec, SHA-256 multihash; base32 string form matching `Cid`) and a block check that the bytes hash to the CID (generalising `api/BlobCids.kt`'s `blobMatchesCid`); verify with unit tests for good, wrong-hash, CIDv0 and non-SHA-256 CIDs.
- [x] 1.2 Write a streaming CAR v1 reader that copies the body to a temp file while counting bytes and blocks against limits, indexes CID → (offset, length), and reads blocks back checked; verify with tests on a CAR written by the reference `@atproto/repo` (generated inside the pinned PDS image by `src/test/resources/reindex/gen-fixture.mjs`, test-only key, no account involved) and on truncated, oversized and over-block-count inputs.
- [x] 1.3 Write the DAG-CBOR → atproto JSON decoder on Jackson's `CBORParser`, beside the existing encoder moved to `ipld/DagCbor.kt` (tag 42 → `{"$link"}`, bytes → `{"$bytes"}`; reject floats, other tags, integers past 64 bits, duplicate keys, excess depth; Jackson reads indefinite lengths, which is harmless since only the commit is re-encoded and its signature then fails); verify the fixture's records decode to Jetstream's JSON shape, and the rejects are tested.
- [x] 1.4 Write the repo walk: commit decoding and checks (`did`, `version` 3, signature over `DagCbor.encode` of the commit without `sig`, against the DID document's `#atproto` key via the code `ServiceAuthVerifier` uses), and an in-order MST walk of the whole tree with prefix-decompressed keys, strictly increasing keys, and depth and node-count bounds (record blocks are decoded only for indexed collections); verify with the fixture (every record found with its CID), a bad-signature commit, a wrong-DID commit and a crafted over-deep tree.

## 2. Indexer support

- [x] 2.1 Add `rev: String? = null` to `JetstreamEvent.Commit` after confirming the pinned Jetstream's v2 commit payload field name; verify with a decode test on a captured frame.
- [x] 2.2 Add `REPO_HOLDINGS` beside `SPACE_RKEYS`, in `index/RepoHoldings.kt`: for each indexed collection, the account's repo-placed rkeys with their indexed CID (null where the table keeps none), including `repo`-origin `RecordShadows`, the profile singleton and Bluesky blocks; verify with a test that fails when a collection in `Nsid.indexedCollections` has no entry, and a test (`reindex/ReindexIndexerTest`) that the listing matches what Jetstream commits indexed and leaves space-held rows out.
- [x] 2.3 Add `ReindexWatch` to `Indexer` (register/unregister per DID, `uri → rev` recorded under the URI lock by `commit`, applied-URI set, grace expiry) and the rule that a Jetstream commit with rev ≤ the snapshot rev for a URI the reindex applied is dropped; verify with `ReindexIndexerTest` cases for each rule in design decision 5, including a commit with no `rev` and the grace expiry.
- [x] 2.4 Add `Indexer.reindexWrite` and `reindexDelete` (repo placement, under the URI lock, reported `HELD` when the watch holds a newer or unknown rev, `INVALID` when the record doesn't decode; Bluesky blocks through `bskyBlocks`); verify in `ReindexIndexerTest` that a delete promotes a space's copy and an undecodable record is reported invalid. (Unchanged records are skipped by CID in the service, so they never reach the indexer: checked in 4.4.)

## 3. Space syncer

- [x] 3.1 Add `SpaceSyncer.resyncAccount(did)`: clear `set_hash`, mark forced in memory (so a sync running meanwhile can't store a set hash over it) and make due every `space_sync` row of the authority, and `announce` its standard athlete spaces with no row; return the count. Verify in `SpaceResyncTest` that a missed space delete is dropped by the forced backfill, that an unannounced space starts syncing, and that a `SpaceNotFound` one backs off without dropping anything early.
- [x] 3.2 Update `.agents/rules/appview.md`'s syncer bullet with the forced resync; verify `scripts/check-agent-sync.sh` passes.

## 4. Reindex service

- [x] 4.1 Add the Flyway migration (`V70__repo_reindexes.sql`, portable SQL) for `repo_reindexes` with the columns in design decision 8 and indexes on (`did`, `requested_at`) and (`run_state`, `requested_at`); verify `:server-appview:test` migrates on H2 and `MigrationVersionsTest` passes.
- [x] 4.2 Add the reindex limits to `AppViewConfig` (`REINDEX_MAX_CAR_BYTES`, `REINDEX_MAX_BLOCKS`, `REINDEX_TIMEOUT`, `REINDEX_PARALLELISM`, `REINDEX_WATCH_GRACE`) with defaults, and document them in `docker/.env.example` and the README's AppView settings; verify a config test reads overrides.
- [x] 4.3 Write `RepoReindexer.request(did, requestedBy, resyncSpaces)`, `run(id)` and `runs(did)`: one queued or running run per DID returned as is, otherwise a new queued row; verify with tests for a repeated request and for reading runs.
- [x] 4.4 Write the run: fresh DID resolution through the guarded `identity` resolver, `getRepo` through `GuardedHttp`'s client (its read timeout) under the overall timeout, watch registered before the fetch, verification, apply (skipping unchanged CIDs, so an unchanged segment or activity queues no job), removal, Bluesky block stamp, then `resyncAccount`; phases and counts written as it goes; temp file deleted in `finally`. Verify end to end in `reindex/RepoReindexerTest` with a mocked PDS serving a signed test repo: a missed create indexed, a missed delete removed, a space-held copy kept, a second run skipping unchanged CIDs, a commit live during the fetch kept, counts and state recorded.
- [x] 4.5 Map PDS answers to outcomes: `RepoTakendown`, `RepoSuspended`, `RepoDeactivated`, `RepoNotFound`, unresolvable or tombstoned DID → `unavailable` with the reason; limits, verification and network errors → `failed` with a short code; neither changes `actors.status` nor removes anything. Verify each with a test, including a stored `deactivated` account that is reindexed and stays unserved.
- [x] 4.6 Write the worker loop: starts queued runs oldest first under `REINDEX_PARALLELISM`, wakes on requests, marks `running` rows `failed`/`interrupted` at startup, prunes runs older than 90 days; start it in `Application.kt`. Verify with tests for the cap, ordering, restart and pruning.
- [x] 4.7 Check no run record or log line carries record content (errors are codes and limits); verify with a test that a decode failure's stored error holds no field of the record.
- [x] 4.8 Add a "Repo reindex" bullet to `.agents/rules/appview.md` (internal service only, the watch rules, removal by repo placement only, never changes status) and note the hand-written CAR reader and why in the commit message; verify `scripts/check-agent-sync.sh` passes.

## 5. Integration

- [x] 5.1 Run `./gradlew :server-appview:test` and `./gradlew check` (through the gradle-runner agent); verify both pass.
The local stack check (records missed by Jetstream reappear after a reindex, deleted ones go, and the run reads `succeeded` with matching counts) moved to `admin-accounts`, whose "Reindex repo" action is the first thing that can start a run on the running AppView.

## Workflow follow-up

- `admin-accounts` wires "Reindex repo" and "Resync spaces" to `RepoReindexer.request` and shows `runs(did)`, writing its audit entries through `admin-site`.
- Archive the change after review.
