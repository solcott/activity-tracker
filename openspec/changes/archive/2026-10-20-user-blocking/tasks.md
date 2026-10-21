# Tasks

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/graph/block.json` and `graph/mute.json` (`subject` did, `createdAt`, key `any`, rkey = subject documented), and add both to `space/athlete.json` `collections` and its description; verify with the `lexicon-auditor` agent
- [x] 1.2 Add `graph/getBlocks.json` and `graph/getMutes.json` (auth required, `cursor`/`limit`, output of `actor.defs#profileView` plus `source` for blocks) and `viewer.blocking` / `viewer.muting` on `actor/defs.json#viewerState`; verify the JSON parses in the lexicon tests
- [x] 1.3 Mirror them in `core/lexicon`: `Nsid.GRAPH_BLOCK`, `GRAPH_MUTE`, `BSKY_GRAPH_BLOCK` (in `indexedCollections`), `BlockRecord` / `MuteRecord` in `Records.kt`, `XrpcQuery` descriptors, `ProfileViewerState` fields; verify `./gradlew :core-lexicon:jvmTest` round-trips each with `AtJson`

## 2. AppView storage and indexing

- [x] 2.1 Add Flyway `V26__blocks_and_mutes.sql` (H2-portable) and `Blocks` / `Mutes` tables in `db/Tables.kt` with both composite indexes; verify `MigrationVersionsTest` and `:server-appview:test` pass
- [x] 2.2 Index `graph.block` and `graph.mute` from `interactions` spaces in `SpaceSyncer` (rkey must equal `subject`, ignore them in tier spaces, remove on space deletion); verify new `SpaceSyncTest` cases for each scenario in the `appview-syncer` delta
- [x] 2.3 Index `app.bsky.graph.block` from Jetstream before `ensureActor`, keeping only creates by authors with an `actors` row, and deleting by `uri`; verify an `Indexer` test drops a non-athlete's block (no actors row created) and keeps an athlete's block of a non-athlete
- [x] 2.4 Backfill each actor's Bluesky blocks with paged `com.atproto.repo.listRecords` while `actors.bsky_blocks_synced_at` is null, off the indexing path with retry; verify a test with a fake PDS covering success, paging and an unreachable PDS that doesn't block profile indexing

## 3. AppView enforcement

- [x] 3.1 Add a `blocked(a, b)` predicate (either direction, any source) and apply it in `admits` (including Public) and `effectiveFollow()`; verify new `BlocksTest` cases: timeline, single activity not found, streams, follow ineffective and restored after unblock
- [x] 3.2 Deny every key in `SpaceAccess.canRead` for a blocked pair; verify `SpaceAccessTest` "Blocked user" and "Unblocked user" scenarios for both block sources
- [x] 3.3 Filter likes and comments by viewer-vs-giver blocks in the like queries and `commentsVisibleTo`, keeping counts equal to the pages; verify comment tests for the three-athlete scenarios, placeholders and the count matching the pages
- [x] 3.4 Apply blocks to `getProfile` (not found for the blocked viewer, `viewer.blocking` for the blocker, empty author feed), `ActorSearch`, and `GraphViews` lists and counts; verify tests for each, including that no response names the blocked-by side
- [x] 3.5 Apply mutes to the timeline and to comments listed for the muter, and set `viewer.muting`; verify `MutesTest` cases, including that the muted account and others see no change
- [x] 3.6 Serve `graph.getBlocks` and `graph.getMutes` to the authenticated caller only, newest first with a cursor, with blocks marked `app` or `bluesky`; verify route tests including a caller who can't read someone else's blocks
- [x] 3.7 Run `EXPLAIN` on the timeline and comments queries with blocks present against the docker Postgres and note the result in the design's Risks; verify that no full scan of `blocks` appears

## 4. Client data layer

- [x] 4.1 Add a repository in `core/data/.../profile` that writes and deletes `graph.block` / `graph.mute` in the interactions space after `requireSpacesFor`, and refuses without the scope; verify `jvmTest` with `MockEngine` for write, idempotent re-block, unblock of a missing record, and the scope refusal
- [x] 4.2 Add `pendingBlocks` / `pendingMutes` overlays (like `pendingFollows`) applied to profiles, timeline pages and comment pages, restored on a failed write; verify tests for the at-once, reload and failure scenarios
- [x] 4.3 Add `getBlocks` / `getMutes` calls through `XrpcClient`; verify with `MockEngine`

## 5. Client UI

- [x] 5.1 Profile: Block, Unblock, Mute and Unmute in the profile menu (none on your own profile). Block asks for confirmation with the "won't be told / Public still visible outside the app" text, and a blocked profile shows Unblock and no activities. Verify presenter tests with `circuit-test`
- [x] 5.2 Comments: Block and Mute the author from a comment's menu (not on your own comments), so their comments leave at once; verify presenter tests
- [x] 5.3 Use the `new-screen` skill to add Blocked accounts and Muted accounts screens reached from Settings, with Unblock/Unmute and read-only Bluesky entries; verify presenter tests and `./gradlew composeStabilityCheck`
- [x] 5.4 Check the profile, comment and settings flows on the desktop app with Compose Hot Reload (screenshots via its MCP server)

## 6. Integration

- [x] 6.1 On the local stack (`scripts/dev.sh --seed alice`, plus bob), block and unblock end to end: an app block and an `app.bsky.graph.block` written with `putRecord`, checking feed, profile, comments and the `followers` space read refusal
- [x] 6.2 Run `./gradlew check` and verify it passes
