# Tasks

## 1. Image and reference material

- [x] 1.1 Resolve the `ghcr.io/bluesky-social/atproto:pds-spaces-alpha` digest built on or after 2026-10-29 (`docker buildx imagetools inspect`), and pin it in `docker/pds.Dockerfile` with the date in the comment. Verify `docker compose -f docker/compose.yaml build pds` succeeds with the CSRF patch's checks intact.
- [x] 1.2 Copy the `com.atproto.space.{createSpace,getSpace,updateSpace,deleteSpace}`, `com.atproto.{personal,member,managed}space.policy` and `com.atproto.managedspace.checkUserAccess` lexicons and `packages/space/src/lthash.ts`, at `5d88b414`, into the session scratchpad. Verify each file is there and the lexicons match the new image's `/app/lexicons`.

## 2. Wire types (`core/lexicon`)

- [x] 2.1 Rewrite the Spaces mirror in `atproto/Space.kt` (design D1, D2):
  - the `SpacePolicy` union with defaults;
  - `Space.CreateSpace`, `GetSpace`, `UpdateSpace` and `DeleteSpace`;
  - `ManagedSpace.CheckUserAccess`;
  - `SpaceErrors`: drop `UNSUPPORTED_APP_ACCESS`, add `POLICY_MISMATCH`, and document `SPACE_NOT_FOUND` as "not found or not authorized";
  - delete `SimpleSpace` and `SpaceAppAccess`, and update the file's pinned-image note.

  Verify `SpaceSerializationTest` round-trips each policy member, decodes a `managedspace` policy with omitted `publishPolicy` as equal to one built with defaults, keeps an unknown `$type` as `Unknown`, and that `./gradlew :core-lexicon:jvmTest` passes.

## 3. Client (`core/atproto`, `core/data`)

- [x] 3.1 Change `SpaceClient` to `createSpace(type, skey, policy)`, `getSpace` and `updateSpace(space, policy)`, with the KDoc saying an update replaces the whole policy. Update `SpaceClientTest` and `SpaceFixtures`. Verify they cover creating, already-exists, get and a wholesale update, and that a `SpaceNotFound` member read maps to not found while `UserNotAuthorized` still maps to no access.
- [x] 3.2 Change `AthleteSpaces.create` to `SpacePolicy.Managed(managingApp())` with a whole-policy compare (design D1), and update `FakeAthletePds` and the core-data and presenter fixtures that stub `simplespace` paths. Verify `AthleteSpacesTest` covers each "Owner writes" scenario, including "Matching policy left alone" and a space whose policy type differs. Verify `./gradlew :core-atproto:jvmTest :core-data:jvmTest :app-presenters:jvmTest` passes.
- [x] 3.3 Update `.agents/rules/data-and-auth.md` where it names simplespace or the read and write policies. Verify `grep -rn simplespace .agents AGENTS.md` shows no stale client wording.

## 4. Managing app (AppView)

- [x] 4.1 Route `ManagedSpace.CheckUserAccess` in `api/Routes.kt`, and update the `SpaceAccess` imports and KDoc (write checks arrive without `clientId` and are denied). Update `SpaceAccessTest` and `FakeSpacesPds`. Verify tests cover each scenario of "Callouts authenticated as the space's authority", including the old `lxm` refused with 401, "Old method name" and "Only the authority writes".

## 5. AppView syncer

- [x] 5.1 Add `sync/LtHash.kt` (design D5). Verify `LtHashTest`:
  - the zero state's digest is `e5a00aa9991ac8a5ee3109844d84a55583bd20572ad3ffcd42792f3c36b183ad`;
  - add then remove returns to zero;
  - order independence and multiset behaviour hold;
  - the state round-trips and a wrong-length state is rejected;
  - one fixed element's digest is pinned once 7.2 confirms it against the PDS.
- [x] 5.2 Add Flyway `V<next>__space_sync_set_hash.sql` (`set_hash BYTEA NULL`, `not_found_since BIGINT NULL`) and the `SpaceSync` columns in `db/Tables.kt`, H2-portable per `.agents/rules/appview.md`. Verify `./gradlew :server-appview:test` migrates H2 cleanly.
- [x] 5.3 Make `FakeSpacesPds` keep a real LtHash per repo, so commits, pages and the head carry true `data`, and add a clear helper (design D9). Verify the existing `SpaceSyncTest` and `SyncedSpaceWriteTest` cases still pass against it before the syncer changes.
- [x] 5.4 Change `headRev` to return the verified head commit, and make `backfill` and `pull` fold, compare and store the state (design D6–D8). Verify `SpaceSyncSetHashTest` covers each scenario of:
  - "Synced spaces verified against the commit's set hash": pull matches, non-indexed collections, backfill matches, a write during backfill, never matches, a row with no stored hash;
  - "Cleared space repos resynced": cleared, cleared then written, account migrated.
- [x] 5.5 Keep spaces on `SpaceNotFound` with `not_found_since` and a `notFoundGrace` tunable (design D4). Verify `SpaceSyncSetHashTest` covers:
  - each new scenario of "Space sync failures are contained": a refusal keeps indexed data and backs off, a 24 h streak drops, a broken streak resets;
  - `SpaceDeleted` still dropping at once.

  Verify `./gradlew :server-appview:test` passes.
- [x] 5.6 Update `.agents/rules/appview.md` (the syncer paragraph: set-hash checks, clear resync, not-found grace, the callout NSID) and the AGENTS.md data-flow paragraph (`checkUserAccess` namespace). Verify `scripts/check-agent-sync.sh` passes.

## 6. Local stack and smoke scripts

- [x] 6.1 Update `docker/scripts/spaces-smoke.sh`, `moderation-smoke.sh`, `seed-effort.sh` and `syncer-smoke.py` to the `com.atproto.space.*` lifecycle methods with `managedspace` or `memberspace` policies, and `syncer-smoke.py` to `com.atproto.memberspace.putMember`. Verify each runs green on a fresh stack (`scripts/dev.sh down` with volumes, then `scripts/dev.sh --seed alice`).
- [x] 6.2 Update `.agents/rules/docker.md` (image date, smoke wording) and the `local-stack` skill (troubleshooting row: `SpaceNotFound` where it said `UserNotAuthorized`). Verify `scripts/check-agent-sync.sh` passes.

## 7. Integration checks

- [x] 7.1 `./gradlew check` passes.
- [x] 7.2 Live set-hash cross-check: with the AppView running on the fresh stack, record a Followers activity as alice. Confirm the AppView syncs and serves it, and that its log shows a backfill or pull whose digest matched the PDS's `data` (no verification failure).
- [x] 7.3 Live clear: call `com.atproto.space.clearRecords` on alice's `followers` space with her session. Confirm the activity disappears from the AppView after the next sync, and the space keeps syncing.
- [x] 7.4 Live managing app: confirm a mutual reads alice's `mutuals` space through her PDS, and a non-mutual is refused with `SpaceNotFound`.

## Workflow follow-up

- Archive the change with `/opsx:archive` once the tasks are done and the specs are synced.
