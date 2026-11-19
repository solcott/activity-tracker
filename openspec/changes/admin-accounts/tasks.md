# Tasks

## 1. Repairs callable for one account (`server/appview`)

- [ ] 1.1 Make `Indexer.recheckHandle(did)` return the `HandleCheck` it stored (null with no `actors` row), leaving `HandleSweep` and the `#identity` path unchanged; verify with `HandleSweepTest`/`HandleVerificationTest` still passing and a new case that a recheck of an unknown DID returns null and inserts no row.
- [ ] 1.2 Move `BskyBlockBackfill`'s per-actor step (backfill, stamp and clear the failure row, or upsert the failure with `pauseAfter`) into one function shared by `runDue` and a new `backfillNow(did): Int` (blocks put; throws after recording a failure; "not indexed" with no `actors` row, recording nothing); verify in `BskyBlockBackfillTest` that `backfillNow` indexes blocks for a stamped actor and one backing off, counts a failure exactly as `runDue` does (failures + 1, same next attempt), and clears the failure row on success.
- [ ] 1.3 Build `BskyBlockBackfill` once in `main` and pass it through `WorkerInputs` to `startWorkers`; verify `:server-appview:test` passes and the `bskyBlockBackfill` worker still registers under `WorkerNames.BSKY_BLOCK_BACKFILL`.

## 2. Account read model

- [ ] 2.1 Add `repoHoldingCount(did, collection)` beside `repoHoldings` (`index/RepoHoldings.kt`) and `spaceRkeyCount(space, collection)` beside `spaceRkeys` (`index/SpaceRkeys.kt`); verify with a test that both equal the listings' sizes for a seeded account with repo, shadowed and space-held rows.
- [ ] 2.2 Write `admin/AccountSummary.kt`: lookup (`@`/case normalized, handle pattern, `handle.invalid` refused, match on stored `actors.handle`; DID parsed), and `load(db, did)` returning only status, handle, verified flag, next check, block backfill state (`bsky_blocks_synced_at`, failure row), segment matching and grouping as booleans (`GroupMode.ON`), counts per collection by origin over the account's `space_sync` rows plus its four standard spaces (zero rows dropped), and `space_sync` rows without `notify_token` or `set_hash`; verify with `AccountSummaryTest` cases for each spec scenario of lookup, status, counts, opt-ins (connected → yes, unknown mode → no) and a failing space.

## 3. Accounts page and actions

- [ ] 3.1 Write `admin/AccountsPage.kt` (`path = "accounts"`): the lookup form and `?q=` redirect to `accounts/{did}`, the account page drawn from `AccountSummary`, the `accounts/{did}/runs` fragment from `RepoReindexer.runs(did)` (requester, state, phase, times, rev, counts, spaces queued, error code and message) polling `every 3s` only while a run is active, and `privacyProbes` covering page, fragments and lookup; verify in `AccountsPageTest` (on `AdminTestBase`) that a viewer sees every section without action controls, an unknown DID shows not indexed, and the runs fragment has a trigger while queued and none once succeeded.
- [ ] 3.2 Add the four actions through `AdminRoutes.action` with the DID as `target`: `account.recheckHandle`, `account.backfillBlocks`, `account.reindex` (`resyncSpaces` checkbox, default on; reports an existing active run), `account.resyncSpaces` (only drawn and registered when a syncer is given); each refuses a non-DID target; verify in `AccountsPageTest` for each action: an operator's post runs it and writes one `admin_actions` row (`ok`, DID target, params), a viewer's is `403` with a `refused: viewer` row and no effect, a post without the CSRF token is refused, a second reindex while one is queued returns the same run id, and with no syncer the page has no "Resync spaces" control.
- [ ] 3.3 Register `AccountsPage` in `main`'s `appView(adminPages = …)` with the indexer, shared backfill, reindexer and syncer; verify `:server-appview:test` passes.
- [ ] 3.4 Add `AccountsPage` to `AdminPrivacyTest.pagesUnderTest`, and extend its seed for carol with a segment track, a failing `space_sync` row, a block backfill failure, `GroupPrefs` `connected` and `SegmentPrefs` with `hideFromLeaderboards`, plus a finished reindex run; verify the test passes (no probe prints the marker, the track's activity URI, `connected` or the space's notify token) and that `catchesAPageThatPrintsARecord` still fails a leaky page.
- [ ] 3.5 Update `.agents/rules/appview.md`'s admin-site bullet with the Accounts page (reads only through `AccountSummary`; actions and their audit names) and `.agents/skills/local-stack/SKILL.md`'s admin paragraph with where to reindex an account; verify `scripts/check-agent-sync.sh` passes.

## 4. Integration

- [ ] 4.1 Run `./gradlew :server-appview:test` and `./gradlew check` through the gradle-runner agent; verify both pass.
- [ ] 4.2 Local stack (carried over from repo-reindex): `scripts/dev.sh --seed alice`; stop only the AppView JVM (the process listening on 8081; not `scripts/dev.sh down`, which also stops the containers, Jetstream and the PDS included); as alice on the local PDS, create a public `feed.post` and a `gear.item`, and delete a public record indexed earlier; with the AppView still down, set `jetstream_cursor.time_us` in `appview-db` past those commits (plus the consumer's 5 s rewind); start it again (`scripts/dev.sh`); confirm with `getProfile`/`getActorGear`/the post's thread that the AppView missed them; then on `/admin/accounts/<alice's DID>` press "Reindex repo". Verify the created records are served, the deleted one is gone, and the run reads `succeeded` with `indexed` equal to the created records, `removed` equal to the deleted ones, and `seen` = `unchanged` + `indexed` + `invalid` + `live`; and that the audit log holds the `account.reindex` row.
- [ ] 4.3 Browser check: in the browser pane, open `http://localhost:8081/admin`, sign in as `siteadmin.test`, look up `alice.test`, and run "Re-verify handle now", "Backfill Bluesky blocks now" and "Resync spaces"; verify each shows its outcome, the runs list stops refreshing once the run ends, the page shows only identifiers, statuses, counts and times, and the audit log lists every action with alice's DID.

## Workflow follow-up

- Merge beside `admin-health`, `admin-job-queues` and `admin-segments`: keep every page in `main`'s `adminPages` list and `AdminPrivacyTest.pagesUnderTest`.
- Archive the change after review.
