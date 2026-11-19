# Design

## Context

See proposal.md for why. What the design has to fit:

- **Repo records arrive only from Jetstream** (`index/JetstreamConsumer.kt`), filtered to `Nsid.indexedCollections`, and go through `Indexer.handle` → `commit`, which takes a per-URI striped lock (`lockedOn`) around each record's transaction and indexes with `Placement.REPO`. `app.bsky.graph.block` goes to `Indexer.bskyBlocks` instead. `JetstreamEvent.Commit` reads `operation`, `collection`, `rkey`, `record`, `cid`, but not the commit's `rev`.
- **Indexing is idempotent** (upserts keyed by AT-URI), but some upserts have side effects: segment matcher and elevation jobs, the daily segment creation limit (`SegmentCreations`), feed position from `indexedAt`. Most record tables keep the record's `cid` (`db/Tables.kt`).
- **Space holdings are listed per collection** by `SPACE_RKEYS` (`index/SpaceRkeys.kt`), which a space backfill uses to drop what a listing no longer holds. Nothing lists what the AppView holds from an account's *repo*.
- **Space sync** (`sync/SpaceSyncer.kt`): a `space_sync` row per known space; a row with no `set_hash` backfills (lists everything, drops what's gone, under its verified head); `dueNow` wakes the loop. Spaces are known only once a client announced them (`notifySpace`).
- **Prior art for repo reads:** `sync/BskyBlockBackfill.kt` pages `listRecords` from the account's PDS through `PdsLookup` (cached an hour) over the guarded client. Hosts learned from DID documents must go through `GuardedHttp` (`identity/outbound-requests`).
- **CBOR:** the AppView already depends on `jackson-dataformat-cbor` (labeler frames, `labels/LabelFrames.kt`, with a canonical DAG-CBOR encoder `DagCbor`), BouncyCastle (ES256K/ES256 in `auth/ServiceAuthVerifier.kt`, `auth/Multikey.kt`), and checks SHA-256 CIDs in `api/BlobCids.kt`.
- **Account status** (`moderation/account-status`): `actors.status` comes only from Jetstream `#account` events; inactive accounts keep their data, unserved. A `deleted` account's data is removed only on that event.
- The AppView is a single process (the URI locks and space locks already assume it).

## Goals / Non-Goals

**Goals:**
- Converge one account's repo-derived index on its repo at one revision, without racing live Jetstream events.
- Reuse the indexer's own upsert/delete paths, so a reindex can't produce a state Jetstream couldn't.
- Bounded memory, time and concurrency; a hostile or huge repo can't hurt the AppView.
- A durable per-run record an admin page can show.

**Non-Goals:**
- Reindexing everyone, or a region, or a collection across accounts (a later change can queue many accounts into the same service).
- Changing an account's status, or deleting an account, from what a PDS answers.
- Full MST proof checking (inclusion proofs, re-deriving the root from records): the AppView verifies the commit signature and every block hash it reads, which is what makes the snapshot the account's own.
- Any public XRPC method, admin page or audit entry (the caller, `admin-accounts`, does those through `admin-site`).
- Repairing derived data that isn't from the account's records (others' efforts on the account's segments are recomputed by the matcher as usual).

## Decisions

### 1. Fetch the whole repo with `getRepo`, not `listRecords` per collection

`com.atproto.sync.getRepo` returns a CAR holding one signed commit and every block under it, so the AppView gets the whole account at a single revision `R`. That revision is what lets live Jetstream commits be ordered against the snapshot (decision 5), and the signature ties every record to the account's key.

*Alternative:* page `listRecords` for each of the 18 indexed collections, as `BskyBlockBackfill` does. Simpler (no CAR), but pages are read at different moments with no common revision, so a record created between two pages and the live event for it can't be ordered, and stale-record removal could delete a record that was just written. It is also unsigned.

### 2. CAR and DAG-CBOR: hand-written on Jackson CBOR (no library fits)

Searched Maven Central (Nov 2026) per the "prefer libraries" rule:

| Candidate | Finding |
|---|---|
| `ipld/java-ipld-cbor`, `ipld/java-cid`, `multiformats/java-multihash` | Not on Maven Central (JitPack only); no CAR reader. |
| Peergos (`org.peergos`) | Own Maven repo, not Central; large dependency tree. |
| `com.apicatalog:copper-multibase` / `copper-multicodec`, `at.asitplus:multibase` | Maintained, but multibase/multicodec only, which `Cid` already covers. No CAR, no DAG-CBOR. |
| `sh.christian.ozone:*`, `kbsky`, `io.github.kikin81.atproto` | atproto client SDKs; none reads CARs or walks the MST; the repo's rule keeps atproto SDKs out anyway. |
| `com.upokecenter:cbor` | Generic CBOR (as Jackson's is); no CAR; last release 2024 alpha. |

Nothing on Central reads CAR v1 or walks an atproto repo, so the AppView writes the small pieces itself: CIDs, varints, CAR and DAG-CBOR in a new `ipld` package (the canonical `DagCbor` encoder moves there from `labels/`, which keeps using it), the repo walk and the service in `reindex`:

- **CAR v1 reader:** unsigned-varint-framed header (DAG-CBOR `{version: 1, roots: [cid]}`) then `varint(len) ‖ cid ‖ block` sections. CIDv1 is read as version, codec, multihash (varints). Streamed from the HTTP body to a temp file while counting bytes and blocks against the limits, keeping an in-memory map CID → (offset, length). Every block read back is checked: its CID must be CIDv1, codec `dag-cbor` (0x71) (or `raw` for nothing we read), SHA-256 multihash of exactly its bytes (the `blobMatchesCid` rule, generalised).
- **DAG-CBOR → atproto JSON:** Jackson's `CBORParser`, mapping tag 42 (CID link) to `{"$link": "<base32 cid>"}` and byte strings to `{"$bytes": "<base64>"}`, which is the JSON shape Jetstream delivers, so the indexer's `AtJson` decoding is unchanged. Rejects floats, tags other than 42, integers past 64 bits, duplicate keys and nesting deeper than 64. Jackson reads indefinite lengths and turns integer map keys into names without saying so; that is harmless here, since hashes are over the raw bytes and the only value re-encoded is the commit, whose signature then fails.
- **Repo walk:** commit `{did, version: 3, data, rev, prev, sig}`; MST nodes `{l, e: [{p, k, v, t}]}` with prefix-compressed keys, walked in key order from `data`. Keys are `collection/rkey` and must strictly increase. The whole tree is walked (nodes are small, a few per hundred records); only indexed collections' record blocks are read.

The commit is checked before anything is indexed: `did` equals the account, `version` is 3, and `sig` verifies over the commit's DAG-CBOR encoding without `sig` (the existing `DagCbor.encode`) against the `#atproto` key of the DID document resolved for this run, using the same key code as `ServiceAuthVerifier`. The CAR's root must be that commit.

Rationale: about 400 lines with no new dependency, every byte checked, and Jackson CBOR is already trusted with labeler frames. Recorded in the commit message per the rule.

### 3. Resolve fresh, fetch guarded, bound everything

- The DID document is resolved fresh through the guarded `identity` resolver (not `PdsLookup`'s hour-long cache), so a migrated account is read from its current PDS; the PDS endpoint and the signing key come from the same document.
- `getRepo` goes through `GuardedHttp`'s client, whose OkHttp read timeout (10 s without a byte) bounds a stalled PDS.
- Limits (in `AppViewConfig`, env-overridable): `REINDEX_MAX_CAR_BYTES` (default 512 MiB), `REINDEX_MAX_BLOCKS` (default 2,000,000), `REINDEX_TIMEOUT` (whole run, default 30 min). Exceeding any ends the run as `failed` with the limit named, having indexed nothing (limits are checked during download, before the apply phase). The temp file is deleted when the run ends, whatever the outcome.
- MST depth (64) and the number of nodes visited (no more than the CAR's blocks) are bounded, so a crafted tree can't recurse or loop without end.

### 4. Apply: unchanged records skipped, the rest through the indexer

The indexer gains a `repoHoldings(did)` listing (decision 6) returning, per indexed collection, rkey → indexed CID (null where the table keeps none). For each snapshot record of an indexed collection:

- indexed CID equals the snapshot CID → **unchanged**, skipped (no transaction, no side effects). Tables that keep no CID (the profile, follows, follow approvals, likes, reposts, comment hides, Bluesky blocks) have their records written again each run; those upserts are idempotent and none of them queues a job;
- otherwise → applied through the same path a Jetstream create would take (`Indexer.reindexWrite(did, collection, rkey, cid, record, rev)`, which is `commit`'s write branch plus the watch check of decision 5); `app.bsky.graph.block` goes to `bskyBlocks.put` as Jetstream's does, and the account's `bsky_blocks_synced_at` is stamped at the end.

Before writing anything, every block a run would write is read once and checked against its CID (and dropped), so a repo missing one or holding a mismatched one fails with nothing changed. Records are then decoded one at a time as they're written, so memory holds only the list of entries. Records that fail to decode are counted `invalid` and skipped, exactly as the indexer already drops them.

*Alternative:* re-upsert everything. Simpler, but every unchanged segment and activity would re-run its side-effect checks and every row would churn; skipping by CID makes a reindex of a healthy account nearly free.

### 5. Live Jetstream events: per-account watch ordered by revision

While a reindex of account A runs, the `Indexer` holds a `ReindexWatch` for A (in memory; the AppView is one process). TIDs (`rev`) sort as strings.

1. The watch is registered **before** `getRepo` is called. Any commit Jetstream delivered before that was committed by the PDS before the fetch, so its rev ≤ R: the snapshot already covers it.
2. Every Jetstream commit for A, under its URI lock, records `uri → rev` in the watch (a commit without `rev` records "unknown").
3. A snapshot write or removal for a URI, under the same lock, is **skipped as `live`** when the watch holds a rev > R or an unknown rev for it: the live event is newer.
4. A Jetstream commit for A whose rev ≤ R, for a URI the reindex already applied or removed, is **dropped**: the snapshot reflects it or something newer (a lagging Jetstream would otherwise put an older version back).
5. The watch outlives the run by `REINDEX_WATCH_GRACE` (default 15 min) for rule 4, since Jetstream may lag the PDS by minutes, then is removed.

`JetstreamEvent.Commit` gains `rev: String? = null` (the v2 wire's commit carries it, as the captured frame in `JetstreamEventTest` shows; if a Jetstream doesn't send it, rule 3's "unknown" makes the live event win, the safe choice).

*Alternative:* pause Jetstream for the account, or for everyone, during the reindex. Pausing everyone stalls the whole AppView for minutes; buffering one account's events in memory is unbounded and lost on restart. The watch needs no buffering.

### 6. Removal: list what the repo placement holds, delete as the repo would

Beside `index/SpaceRkeys.kt`, a repo counterpart, `REPO_HOLDINGS` in `index/RepoHoldings.kt`, with one entry per collection in `Nsid.indexedCollections` (rows with origin `repo` or no origin, by author, plus `repo`-origin copies in `RecordShadows`; the profile as a singleton at `self`; Bluesky blocks by author with origin `repo`). A test fails when an indexed collection has no entry, as `SPACE_RKEYS` does for spaces.

After the apply phase, every held (collection, rkey) not in the snapshot is deleted through `delete(collection, uri, Placement.REPO)` under its URI lock (rule 3 applies), so a shadow from a space is promoted and derived rows go exactly as on a Jetstream delete. Space-placed rows are never touched. Removal runs only after the commit verified and every block was read: a failure before then removes nothing.

### 7. Inactive and missing accounts

The reindex never writes `actors.status`. It runs for an account whatever its stored status (an inactive account's data is kept and unserved, so repairing it is harmless). A PDS answering `RepoTakendown`, `RepoSuspended` or `RepoDeactivated` ends the run as `unavailable` with that reason; `RepoNotFound`, a DID that doesn't resolve or a tombstoned DID end it `unavailable` too. None of these removes anything: deletion stays the `#account deleted` event's job. An account the AppView has never seen may be reindexed (that is how a wholly missed account is brought in); it gets its `actors` row as on first sight.

### 8. Queue, concurrency and progress in one table

`repo_reindexes` (Flyway, next free version, portable SQL, epoch-millis `BIGINT`s): `id`, `did`, `requested_by`, `requested_at`, `resync_spaces`, `started_at`, `finished_at`, `run_state` (`queued`, `running`, `succeeded`, `failed`, `unavailable`; not `state`, which Exposed quotes as a reserved word), `phase` (`resolving`, `fetching`, `applying`, `removing`, `spaces`), `rev`, `car_bytes`, `records_seen`, `records_unchanged`, `records_indexed`, `records_invalid`, `records_removed`, `records_live` (skipped for live events), `spaces_resynced`, `error_code` and `error_message` (a short code and our own message, never record content or the PDS's message). It is `V70__repo_reindexes.sql`, after main's V67 to V69 (`admin-site`, `worker-telemetry`, `segment-pins`).

- `RepoReindexer.request(did, requestedBy, resyncSpaces = true)` returns the run: an existing `queued` or `running` run for the DID is returned as is (one per account); otherwise a `queued` row is inserted.
- A worker loop starts queued runs oldest first, at most `REINDEX_PARALLELISM` (default 2) at once, waking on each request. Progress counters are written every few thousand records and at each phase change.
- On startup, runs left `running` become `failed` with `interrupted`; `queued` ones stay queued.
- Rows are kept 90 days, then pruned by the same loop. `RepoReindexer.runs(did)` and `run(id)` read them for the admin page.
- The loop is the `repo-reindexer` worker under `WorkerSupervisor` (dev/worker-telemetry), reporting each round; a run's own failure is its row's, not the worker's.

### 9. Spaces: force a backfill through the syncer

`SpaceSyncer.resyncAccount(did)` clears `set_hash` on every `space_sync` row whose authority is the DID (a row with no set hash backfills, dropping what its listing no longer holds, under its verified head), also marks them in an in-memory `forced` set (a sync running at that moment would otherwise store a set hash again over the cleared one), and makes them due now, and `announce`s each standard athlete space (`followers`, `mutuals`, `self`, `interactions`) that has no row. It returns the number of spaces queued and doesn't wait for them; their outcomes are each space's own `space_sync` state, which the admin page reads. It runs at the end of the reindex (and also when the repo was `unavailable`, since spaces sync independently of the repo).

An announced space that doesn't exist is answered `SpaceNotFound` and dropped after the existing 24-hour grace, as any announcement is.

## Risks / Trade-offs

- [A PDS serves an old repo (restored from backup, or the old PDS of a migrated account)] → the DID document is resolved fresh and the commit must verify against its current key; removal deletes only from the repo placement, and Jetstream rewrites whatever it later sees. An operator can rerun.
- [Jetstream lags more than the watch grace and then replays an old commit] → that old commit is applied as it would be without a reindex; the newer one follows in Jetstream order. Rule 4 only narrows the window.
- [A huge repo (the segment importer's, with thousands of segments and paths)] → limits are configurable; unchanged records are skipped, so a rerun is cheap; one run per account and a global cap keep it from crowding the indexer.
- [Hand-written parser bugs] → every block hash and the commit signature are checked; the parser is tested against a CAR written by the reference `@atproto/repo` (generated in the pinned PDS image) and against malformed inputs (truncated, wrong hash, floats, deep nesting, oversized).
- [Skipping by CID misses a row whose derived columns are wrong although its CID is right] → out of scope: that is an indexer bug, fixed by a migration; the `force` path (re-upsert everything) is left for later if needed.
- [Temp disk use] → bounded by `REINDEX_MAX_CAR_BYTES × REINDEX_PARALLELISM`; files deleted in `finally`.

## Migration Plan

One additive Flyway migration (`repo_reindexes`). No backfill. Rollback: drop the service (the table is unused by anything else). Nothing runs until `admin-accounts` calls it.

