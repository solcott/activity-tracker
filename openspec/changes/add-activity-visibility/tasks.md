# Tasks

## 1. Spike: AppView as a Spaces syncer (go/no-go)

- [x] 1.1 Write `docker/scripts/syncer-smoke.sh` (throwaway-quality is fine). With a hand-generated P-256 key published as `#atproto` in a stub `did:web` served through the tunnel, it should mint a delegation token, get a credential for alice's `followers` space, call `registerNotify`, put a record as alice, and log the `notifyWrite` the stub receives. Verify by running it against `scripts/dev.sh` and seeing the notification and a successful `listRepoOps`.
- [x] 1.2 Extend the spike to put a space record that references an uploaded blob, and to read that blob back with the syncer's credential through `getBlob`. Record which upload path works in design.md decision 5. Verify that the blob bytes round-trip.
- [x] 1.3 Record the spike outcome in design.md: proceed, or stop and revisit option A. Verify that the go/no-go is stated with the evidence (the commands run and the responses).

## 2. Lexicons

- [x] 2.1 Use the `lexicon-change` skill to add `actor.activityDefaults` (rkey `self`, `visibility`, `chartsVisibility`). Add `feed.activity` and `actor.activityDefaults` to `space.athlete` collections, update the `feed.activity` description (it lives in a tier space when non-public), and add `visibility` to `feed.defs#activityView`. Mirror all of it in `core/lexicon` and `Nsid`. Verify with `./gradlew :core-lexicon:jvmTest` serialization tests for each new or changed type.
- [x] 2.2 Add the `space.notifySpace` procedure lexicon (input `space`) and its `XrpcProcedure` descriptor. Verify with a serialization test and by running `docker/scripts/publish-lexicons.sh` cleanly on the local stack.
- [x] 2.3 Run the `lexicon-auditor` agent on the changed NSIDs. Verify that it reports no mismatches.

## 3. AppView identity and access

- [x] 3.1 Add `APPVIEW_SIGNING_KEY` to `AppViewConfig`, and have `scripts/dev.sh` generate it into `docker/.env` once (template in `.env.example`). Publish `#atproto` (Multikey) and the syncer service in `did.json`. Verify with `AppViewConfigTest` and a `did.json` route test that checks the new entries alongside the unchanged existing ones.
- [x] 3.2 Without a key, start with syncing disabled and log it. Verify with a test that the AppView serves public views and joins no space.
- [x] 3.3 Update `SpaceAccess` to admit the AppView's own DID for `read` on the four tiers, and to deny it `write` and unknown tiers. Verify with new `SpaceAccessTest` cases matching the managing-app delta scenarios.

## 4. Shared Spaces signing code

- [x] 4.1 Refactor `:core-atproto` so that `SpaceCredentials` takes a delegation-token source (the viewer's PDS, or a local signer), and expose what the AppView needs from `HttpMessageSignature`. Verify that `./gradlew :core-atproto:jvmTest` passes, including `HttpMessageSignatureTest` vectors and the existing credential reuse tests.
- [x] 4.2 Add a JVM local signer that mints `atproto-space-delegation+jwt` tokens (`iss`, `aud = {authority}#atproto_space_host`, `sub`, 60 s, `jti`, `kid #atproto`). Verify with a test that decodes the token and checks its signature against the published key.

## 5. AppView schema

- [x] 5.1 Write `V15__activity_visibility.sql`: `activities.visibility` (default `public`), `activities.origin` (default `repo`), `activity_streams.origin`, an index on `(author_did, visibility, sort_at)`, and the `record_shadows` and `space_sync` tables. Mirror it in `Tables.kt`. Verify that `MigrationVersionsTest` and `./gradlew :server-appview:test` pass on H2.

## 6. AppView syncer

- [x] 6.1 Implement the `notifySpace` route: service auth from the athlete, the space's authority must equal the issuer and its type must be `space.athlete`, and the call upserts `space_sync` and schedules a join. Verify with tests for the "Clients announce tier spaces" scenarios (new, someone else's, repeated).
- [x] 6.2 Implement join, register and backfill: credential through the local signer, `registerNotify`, and `listRepoOps` from no revision, applying `feed.activity` and `feed.activityStreams` ops through the indexer with `visibility` and `origin` from the space key. Drop every other collection. Verify with tests against a fake Spaces PDS (Ktor `MockEngine`), including "Private details ignored".
- [x] 6.3 Implement the `notifyWrite` route with its own audience verifier, plus pulls per space that never overlap and are idempotent. Verify with tests for the forged, duplicate and delete scenarios, and that a managing-app token is rejected on this route.
- [x] 6.4 Implement the reconcile job (renewal margin, a periodic pull, per-space backoff, `SpaceDeleted` → drop the rows), isolated so that one space's failure doesn't affect Jetstream or other spaces. Verify with tests for the "Notification lost", "Space deleted" and "PDS unreachable" scenarios.
- [x] 6.5 Make the narrower tier win for one URI held by two origins (the wider copy shadowed in `record_shadows`), and make deletes remove only the deleting origin's copy, promoting a shadow left behind. Verify with a test for the mid-move "Briefly in two places" scenario.

## 7. AppView views by tier

- [x] 7.1 Add the `visibleTo(viewer)` predicate and apply it in timeline, author activities (both orders), `getActivity`, recordings (same-visibility links only) and `activitiesCount`. Set `visibility` on activity views. Verify with `AppViewTest` cases for every "Non-public activities served by tier" scenario, including the counts and filtered paging.
- [x] 7.2 Return no kudos or comment counts, no `viewer.kudos` and no `getKudos` results for non-public activities, while keeping the rows. Verify with tests for "Activity made private after kudos" and "Made public again".
- [x] 7.3 Serve space-held media, the track and stream data through the AppView after a `visibleTo` check, using `getBlob` with the syncer credential and `Cache-Control: private`. Apply the tier check to space streams in `ActivityStreamsViews`. Verify with tests for "Follower views a photo" and "Non-follower requests a photo URL", plus a streams test.
- [x] 7.4 Run `EXPLAIN` on the timeline query against the local Postgres with seeded data. Verify that the new index is used, and note the plan in the PR description.
- [x] 7.5 Update `.agents/rules/appview.md` (tier-scoped storage replaces "stores nothing private", the syncer, and the indexed collections) and the AppView paragraph of `AGENTS.md`. Verify with `scripts/check-agent-sync.sh` and a read-through against the new specs.

## 8. Client data layer

- [x] 8.1 Add the nullable `space` column to `OutboxEntity` with a Room auto-migration 1 → 2, and route space rows in `OutboxProcessor` to space `putRecord`/`deleteRecord` after `AthleteSpaces.ensure`. Verify with outbox tests for repo and space rows, and that the regenerated schema JSON is committed.
- [x] 8.2 Make `AthleteSpaces` call `notifySpace` after ensuring a tier, and on profile saves that touch a tier. Verify with a `ProfileRepositoryTest` that checks the call is made.
- [x] 8.3 Add the defaults store: read `actor.activityDefaults` from `self` with the owner session, cache it, fall back to Public on failure, and cap charts on write. Verify with tests for the "Never set", "Charts default capped" and "Defaults unreadable" scenarios.
- [x] 8.4 In `ActivityDraft`, `ActivityRepository` and `publishAll`, add activity visibility, queue non-public activities and their streams to the tier space, and keep blobs on the path the spike chose. Verify with `ActivityRepositoryTest` for "Followers activity published", "Public activity published" and "Batch import".
- [x] 8.5 Implement visibility moves in `ActivityManager`: write the new place, delete the old, drag charts down to the cap, move recordings with their primary, and make grouping adopt the primary's visibility. Use direct calls, not the outbox. Verify with tests for "Interrupted move", "Narrowing the activity drags charts" and "Group into a Followers activity".
- [x] 8.6 Add a space-backed `ActivityStreamsStore` binding, so a visibility change moves the streams record between the repo and spaces. Remove the `TODO(spaces)` notes it resolves (`ActivityStreamsStore`, `Records.kt`, the lexicon description). Verify with a store test that moves Public → Followers → Public.
- [x] 8.7 Disable visibility choices other than Public when the session lacks the `space:` scope, with the existing sign-in-again path. Verify with a repository test using a scope-missing session.
- [x] 8.8 Update `.agents/rules/data-and-auth.md` with the outbox space rows, the moves being direct calls, and the defaults store. Verify with `scripts/check-agent-sync.sh`.

## 9. UI

- [x] 9.1 Add a shared visibility picker for the activity and its charts (charts options capped) to Record, Manual, Import (including batch) and Edit, prefilled from the defaults, using the `ui/adaptive` wrappers. Verify with presenter tests for "Override the default", "Wider charts not offered" and edit saves.
- [x] 9.2 Show the irreversible-copies warning when narrowing a Public activity, and save only after the athlete confirms. Verify with presenter tests for "Public to Only me" and "Between non-public tiers".
- [x] 9.3 Add a default visibility section in Settings (activity and charts, charts capped). Verify with a presenter test that saves and reloads it.
- [x] 9.4 Show a visibility badge on feed cards and the activity detail, and hide the kudos and comment actions and counts on non-public activities. Verify with presenter tests and a desktop hot-reload screenshot of a Followers card.
- [x] 9.5 Run `./gradlew composeStabilityCheck`. Verify that no new unstable params are reported.

## 10. Integration and docs

- [x] 10.1 End to end on the local stack: alice posts Followers, Mutuals and Only-me activities; bob (an effective follower), carol (a pending follower) and alice herself each see exactly what the specs allow, and a Followers → Public → Only me edit round-trip behaves. Verify by running the app against `scripts/dev.sh --seed alice` and checking each account's timeline and profile.
- [x] 10.2 Rewrite `BACKLOG.md` "Private activities" to describe what's left. Add a new "Kudos and comments on non-public activities" entry covering: in-space interaction records in the commenter's repo; `checkUserAccess` admitting tier members for `write`; records stranded in the old tier on a move (and whether an authority can remove members' records); keyed by the logical URI so no migration is needed. Add a "Before deploying" note that the AppView database now holds non-public data. Verify by reading the entries against the proposal's non-goals.
- [x] 10.3 Run `./gradlew check`. Verify that it passes on every target.

## Workflow follow-up

- Archive the change after review, syncing the delta specs to `openspec/specs/`.
- Open the follow-up change for in-space kudos and comments from the `BACKLOG.md` entry.
