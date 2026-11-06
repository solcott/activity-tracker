# Tasks

## 1. Space deletion and scope

- [x] 1.1 On the local stack, confirm the pinned 2026-11-05 Spaces alpha PDS (atproto `704b9e2`) accepts a grant with `manage=delete` and that `com.atproto.space.deleteSpace` deletes a space and sends `notifySpaceDeleted` to the AppView (purge in the AppView log). Add the step to `docker/scripts/spaces-smoke.sh`. Stop and bump the pin in its own change if either fails
- [x] 1.2 Mirror `com.atproto.space.deleteSpace` in `core/lexicon` `Space.kt` and add `SpaceClient.deleteSpace`, treating NotFound as done and surfacing `ScopeMissingError`; cover delete, repeat delete and missing scope in `SpaceClientTest` (`./gradlew :core-atproto:jvmTest --tests "*.SpaceClientTest"`)
- [x] 1.3 Add `&manage=delete` to `OAuthClientConfig.DEFAULT_SCOPE` and the hosted `/oauth/client-metadata.json`; update `OAuthFlowTest` and the metadata-matches test, and add a grant-status test showing an old grant as `Outdated` (`./gradlew :core-atproto:jvmTest :core-data:jvmTest :server-appview:test`)

## 2. AppView segment handoffs

- [x] 2.1 Add a Flyway migration and Exposed table `subject_handoffs` (id, kind, old_uri, sport, segment_kind, line, centre, radius, original_name, successor_uri, staged_at), with H2-portable SQL; `./gradlew :server-appview:test` passes
- [x] 2.2 Stage a handoff in `deleteSegments` for each active, non-importer segment without `!takedown`, with a deterministic id from the old URI, and re-key its name-history rows to the handoff instead of deleting them; tests for a record delete, `deleteActor`, proposed, importer and taken-down segments, and a repeated delete
- [x] 2.3 Link successors per design Decision 3: a duplicate made live by `recheckDuplicatesOf`, the importer's record under a handoff id (or its `duplicateOf` when it's indexed as duplicate), re-pointing handoffs whose successor is itself handed off, and the original taking its pending handoff over when its old URI is written again (surviving a replayed create and delete); carry `current_name` and history to the successor and queue matching; tests for each, including efforts rebuilt from stored tracks and the leaver's own tracks giving none
- [x] 2.4 `feed.getSegment` resolves a handed-off URI to its successor's view and answers `NotFound` while it's pending; route tests for both
- [x] 2.5 Add the `app.activitytracker.importer.getHandoffs` lexicon JSON, Kotlin model and `XrpcQuery`, and an importer-only route that links handoffs a live subject duplicates and lists the rest with no rider data; tests for the importer, an athlete (forbidden), no importer configured, and a covered handoff; run the `lexicon-auditor` agent on the new NSID

## 3. AppView dive-site handoffs

- [x] 3.1 Stage site handoffs in the dive-site removal path (`DiveSiteIndex.kt`, including `deleteDiveSitesBy`), keeping centre, radius and names; tests for an active site, a proposed one and `deleteActor`
- [x] 3.2 Index an importer site under a handoff id as `active` when it duplicates nothing, carry its names and history, link successors as for segments, and recompute visits; `feed.getDiveSite` resolves old URIs; `getHandoffs` lists sites with the site duplicate test; tests mirroring 2.3 and 2.4

## 4. Importer `handoff` command

- [x] 4.1 Add `handoff` to `server-segment-importer`: page `importer.getHandoffs` and `putRecord` each one under its handoff id as a `feed.segment` or `feed.diveSite` (`source` `athlete`, `CC0-1.0`, no attribution) only where no record exists, reporting published and already-published counts; tests with a fake AppView and PDS for a segment, a site, and a re-run that writes nothing
- [x] 4.2 Add an optional handoff step to `scripts/segment-import.sh` and the command to the AGENTS.md command list; run `scripts/check-agent-sync.sh`

## 5. Client: never recreate, detect a leave

- [x] 5.1 Remember the spaces this device used (`AccountMarks`, in `key_value`), written when `AthleteSpaces.ensure` first succeeds for a space; `ensure` raises `AccountLeftException` instead of creating a known space that's gone; `core-data` jvm tests with `FakeAthletePds`
- [x] 5.2 Check `self` with `getSpace` at session start and before the first drain of a session, and again after any `SpaceNotFound` write failure; `AccountLeft` stops the drain with nothing sent; tests for a desktop after a leave, a new device that never used the spaces, and an offline device that reconnects
- [x] 5.3 An account-left prompt in the app shell: with nothing pending, sign out with the left note; with pending work, show the count and Discard and sign out / Rejoin (Rejoin forgets the remembered spaces and drains); presenter and UI tests, samples in `:app-fixtures`, strings in `:ui-resources`

## 6. Client: the leave job

- [x] 6.1 Add a `LeaveJob` in `core/data` per design Decision 6, its step recorded in `key_value` (`AccountMarks.leaving`); discard the outbox, imports and recording; delete spaces `self` first; delete the repo records of every collection in `AppCollections.records` plus any other `app.activitytracker.*` collection `describeRepo` reports, via `listRecords` and `applyWrites` under `AccountPacing`; clear the account's local data, export files and secrets; refuse to start while the account's export runs or its PDS can't be reached; jvm tests for the full run (Bluesky records untouched), a retired collection still in the repo, a running export, an unreachable PDS, no spaces, a 429 mid-run, a non-rate-limit refusal stopping with Retry, and resuming at each step
- [x] 6.2 The app shell resumes a `leaving` row on start or sign-in instead of opening the session UI, and shows the left note on the login screen after it finishes; tests in `composeApp` and the login presenter
- [x] 6.3 Rewrite a segment star when `getSegment` returns another URI (a `NotFound` one stays unavailable as `segment-favorites` already says); `SegmentStars` tests and the starred list's presenter test

## 7. Leave screen

- [x] 7.1 Add `LeaveScreen`, `LeaveState` and `LeaveEvent` in `feature/settings/api`, and a Leave Activity Tracker row on the Account page; `composeStabilityCheck` passes
- [x] 7.2 `LeavePresenter`: the deleted/stays summary, the count of active segments and dive sites that stay (from the AppView's segment and site lists for the athlete) and of the rest that go, pending device work, the export states (start one, wait for or cancel a running one, save a ready one first, using the Account page's export), the grant and PDS-reachability checks, a typed-handle gate, and the job's progress and errors; presenter tests for each `auth/leaving` scenario
- [x] 7.3 `LeaveUi` from `ui/adaptive` wrappers, with the progress and error states; strings and plurals in `:ui-resources`, `LeaveSamples` in `:app-fixtures`, `LeaveUiTest`, `ContentLoadingConformanceTest` if it applies, and screenshot tests in `:androidApp` (`ScreenshotCoverageTest` passes)
- [x] 7.4 Regenerate screenshot references with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite` and review the changed Account, Leave and Login images

## 8. Integration

- [x] 8.1 On the local stack: alice creates an active segment (3 riders), a site and private activities, then leaves on one client while a second stays signed in with a queued write. Verify her spaces and `app.activitytracker.*` records are gone, her `app.bsky.*` records remain, the second client stops and offers Rejoin, `handoff` republishes the segment and site with their names, riders' efforts come back, and the old URIs resolve
- [x] 8.2 `./gradlew check` passes

## Workflow follow-up

- Archive the change after review, and sync its specs into `openspec/specs`.
