# Design

## Context

- **The source.** Bluesky's guide `alpha-migrations/prompt-10-29-26.md` on the atproto `permissioned-data` branch. This change applies:
  - §1a–1b: the policy union and the lifecycle methods;
  - §1d: the managing app;
  - §2: credential errors;
  - §4: the syncer side of `clearRecords`;
  - §5c: migrated repos, for syncers;
  - §6: the reference PDS.

  Wire shapes come from the lexicons at `5d88b414` (`lexicons/com/atproto/{space,memberspace,managedspace,personalspace}`). The set-hash algorithm comes from `packages/space/src/lthash.ts` and `formatSetHashElement` in `util.ts` at the same commit.
- **Today's flow.**
  - `AthleteSpaces.create` makes each space with `simplespace.createSpace(read = managingApp(AppView), write = memberList, appAccess = open)`. When the space already exists, it compares only `readPolicy`.
  - `SpaceAccess.check` answers `simplespace.checkUserAccess`, and the route's verifier requires `lxm` to equal the route's method NSID.
  - `SpaceSyncer.sync` drops a space on `SpaceDeleted` or `SpaceNotFound`.
  - `pull` buffers `listRepoOps` pages and applies them once the final commit's MAC verifies, but never looks at its `data`.
  - `backfill` reads the MAC-verified head (`headRev`), then lists `Indexer.spaceCollections(skey)` one collection at a time.
- **Earlier releases.** Audits of the Oct 1 and Oct 22 guides against the code found them applied, apart from the Oct 22 non-goal "value-to-CID checks and set-hash comparison" (see the `spaces-alpha-2026-10-22` design). This change takes on the set-hash half.

## Goals / Non-Goals

**Goals:**
- Every space flow works against the 2026-10-29 image without changing who can read or write athlete spaces.
- Only a deletion, or a not-found that lasts a day, removes indexed space data.
- The AppView never keeps serving records the authority's repo no longer holds once a pull has seen the commit that removed them.

**Non-Goals:**
- `personalspace` for `self` or `interactions`, since the AppView must read both.
- Published policy or member records.
- `removeWriter`, `clearRecords` calls from our own clients, account-migration tooling, `importRepo`, and `exportSpaceMeta`/`importSpaceMeta`.
- STAR `getRepo`, and checking each record's value against its CID. Backfill keeps using `listRecords`, which the protocol doesn't MAC; the set hash now ties the listing's CIDs to the MAC-verified head `data`.
- Re-registering with a new host when an authority migrates. Registrations are renewed before they expire, which upstream allows ("or at their next renewal").

## Decisions

1. **Policy union mirrors the lexicons, with their defaults.**
   - `SpacePolicy` stays an open union through `OpenUnionSerializer`, with four members:
     - `Personal` (`com.atproto.personalspace.policy`, no fields);
     - `Member` (`publicRead`, `publicWrite` and `publishPolicy`/`publishMembers` default `false`; `allowedApps` is nullable);
     - `Managed` (`managingApp`, with `publishPolicy` defaulting to `false`);
     - `Unknown`.
   - `AtJson` decodes missing booleans to the same defaults, so the policy the PDS stores, with its defaults filled in, compares `==` to the one the client builds. That lets `AthleteSpaces.create` compare whole policies.
   - Alternative: compare only `managingApp`. Rejected, because a space whose policy type changed, or that publishes its policy, would never be put back.
2. **Lifecycle methods sit in `object Space`.** `CreateSpace`, `GetSpace`, `UpdateSpace` and `DeleteSpace` sit beside the other `com.atproto.space.*` methods, since they now share that namespace. The callout gets its own `object ManagedSpace { CheckUserAccess }`. `SimpleSpace` and `SpaceAppAccess` are deleted outright, as in the Oct 22 change: no compatibility path, and the stack is pinned.
3. **The callout's `lxm` follows the method.** The verifier already compares `lxm` with the route's NSID, so renaming the descriptor moves both. `SpaceAccess` logic is unchanged: write checks, which `managedspace` now sends, already fall to `false`.
4. **`SpaceNotFound` persistence lives in a column, not the failure count.**
   - `space_sync.not_found_since` (epoch millis, nullable) is set on the first `SpaceNotFound` of a streak and cleared by any success or any other error.
   - The space is dropped when `now - not_found_since >= notFoundGrace` (default 24 h, a constructor tunable like `resyncAfter`). Until then it goes through `failed()`, which backs off up to an hour, so a missing space is retried about 24 times before it's dropped.
   - Alternative: count failures. Rejected, because backoff makes counts a poor clock and the tests would have to reason about doublings.
   - `SpaceDeleted` still drops at once.
   - The clients' `SpaceClient.outcome` mapping is unchanged: `SpaceNotFound` is already `NotFound`, and own-space reads use the session.
5. **LtHash is a port in the AppView.**
   - `sync/LtHash.kt` holds a 2048-byte state of 1024 little-endian `u16` lanes.
   - Each element is expanded with BLAKE3 in XOF mode to 2048 bytes, using BouncyCastle `Blake3Digest`, already an AppView dependency. Add and remove work lane-wise mod 2^16.
   - `digest()` is sha256 of the state. The element is `"$collection/$rkey/${cid.value}"`, with the CID's string form, as `formatSetHashElement` uses.
   - It lives in the AppView, not `core/atproto`, because only the syncer needs it and there's no multiplatform BLAKE3 library.
   - Vectors:
     - the empty digest `e5a00aa9…83ad` that the guide gives for a cleared repo;
     - upstream's algebraic tests;
     - a live cross-check against the local PDS (tasks).
6. **The state is stored per space.**
   - `space_sync.set_hash` (`BYTEA`, nullable) holds the state after the last recorded revision, written in the same transaction as `repo_rev`.
   - A row with a revision but no state backfills. That covers every row after the migration, so there's no data migration.
7. **Pull: fold, compare, then apply or resync.**
   - `pull` loads the state and folds each op as pages arrive: `prev` is removed and `cid` added, so an update moves one element and a delete only removes.
   - Ops whose `value` was superseded still carry `cid` and `prev`, so the fold doesn't depend on values.
   - After the final page, if `digest == commit.data` it applies the buffered ops as today and records the revision with the new state. Otherwise it applies nothing and returns to `backfill`.
   - That one rule covers all three cases. After a clear, `data` stops matching. After a migration there are no ops and `data` is unchanged, so the revision just advances. Drift from any other cause heals itself.
   - A pull that reaches no commit keeps `since` and the state, as today.
8. **Backfill: one listing of the whole repo, checked against the head.**
   - `headRev` returns the MAC-verified head commit, with `rev` and `data`, instead of only the revision.
   - `backfill` pages `listRecords` with no `collection`, so the listing covers every collection. It folds each listed `(collection, rkey, cid)` into a fresh LtHash. It indexes the records of `Indexer.spaceCollections(skey)` as it goes, as today, and collects their rkeys.
   - At the end it compares the digest with `head.data`:
     - **Match:** delete indexed rkeys absent from the listing, then record the head revision and state.
     - **Mismatch, and a re-read head moved:** a write raced the listing, so start over, up to 3 attempts.
     - **Mismatch, head unchanged** (or attempts used up): throw a verification error. `failed()` backs off, and indexed rows that weren't listed are kept, so nothing is purged on bad evidence.
   - Records listed and indexed before a mismatch stay indexed. They are the authority's records at that moment, the same exposure as today's backfill.
   - Alternative: STAR `getRepo`, which is atomic and MAC'd. Rejected for now: it needs a STAR reader and DRISL-to-JSON decoding for the indexer. Retrying on a moved head is enough for the few writes one athlete makes.
9. **Fakes compute real `data`.** `FakeSpacesPds` keeps an LtHash per repo, so each commit and each `listRepoOps` page carries a true `data`, and gains a `clearRecords`-like helper. The tests then exercise the real comparison instead of a stub.

## Risks / Trade-offs

- [Our LtHash disagrees with upstream on some detail (lane endianness, the CID string form, the XOF length), so every pull falls back to backfill and every backfill fails] → The empty-digest vector and upstream's tests pin the algebra. The live check in the tasks compares a backfill digest with the local PDS's `data`. A mismatch shows up as a backoff loop that purges nothing, never as data loss.
- [A busy space keeps moving its head during a backfill] → Three attempts, then backoff, and the next attempt retries. Athletes write rarely.
- [Listing every collection costs more than listing the indexed ones] → It happens only on first sync, after an hour without one, or after a mismatch. Athlete spaces hold few non-indexed records, such as the client-only ones.
- [A space truly gone without a tombstone (lost in an account migration) lingers for up to 24 hours] → It serves only what it served before, to the same viewers, and is then dropped.
- [Mixed-configuration warning from upstream §6] → Our spaces had an empty member list and open app access. `managedspace` asks the AppView about writes as well, and we already deny them. The authority is never asked. No access change.

## Migration Plan

1. Re-pin `pds-spaces-alpha` to the 2026-10-29 digest and run the CSRF patch check in the Dockerfile build.
2. Wipe and reseed the local stack (`scripts/dev.sh down` with volumes, then `scripts/dev.sh --seed alice`). The PDS's `006-space-policy` would also convert existing spaces in place, but the smoke checks start clean.
3. Flyway `V<next>__space_sync_set_hash.sql` adds both nullable columns. Existing rows backfill once.
4. Rollback means reverting the commit and the image pin together. The AppView ignores the two columns once they're unused.
