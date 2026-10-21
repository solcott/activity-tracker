# Tasks

## 1. Local labeler (Ozone)

- [ ] 1.1 Add a digest-pinned `ozone` service and its database to `docker/compose.yaml`, and a `docker/scripts/setup-labeler.sh` (called by `scripts/dev.sh`) that creates `moderation.test`, adds `#atproto_labeler` and `#atproto_label` to its DID document on the local PLC, and adds an admin team member. Verify by running `scripts/dev.sh` on a fresh stack and resolving the DID document
- [ ] 1.2 Spike: confirm local Ozone accepts the AppView's `did:web` as a `triage` member, and that a service-auth call from it to `tools.ozone.moderation.getSubjectStatus` / `queryStatuses` and `tools.ozone.team.listMembers` succeeds. Record the result, and switch to the design's fallback if it fails, in `design.md`. Verify with a curl transcript in the PR description
- [ ] 1.3 Document the labeler in `.agents/rules/docker.md` and the `local-stack` skill (setup, Ozone URL, adding moderators); verify `scripts/check-agent-sync.sh` passes

## 2. Reports

- [ ] 2.1 Mirror `com.atproto.moderation.createReport` (reason types, `strongRef` / `repoRef` union) in `core/lexicon`; verify `:core-lexicon:jvmTest` round-trips with `AtJson`
- [ ] 2.2 Add per-call headers to `XrpcClient` and a labeler DID to `AtprotoConfig` (`local.properties` locally); verify a `MockEngine` test that the PDS call carries `atproto-proxy: <did>#atproto_labeler`
- [ ] 2.3 Add a `ReportRepository` in `core/data` that builds subjects (activity or comment strongRef, photo as activity plus a position note, account repoRef) and maps reasons; verify `jvmTest` for each subject type and the "other needs a note" rule
- [ ] 2.4 Build a report sheet sub-screen (SubCircuit, three-module split) reached from activity, gallery photo, comment and profile menus. Hide it on your own content and when signed out. On success it says thanks and offers to block; on failure it keeps the input. Verify with presenter tests
- [ ] 2.5 Report end to end on the local stack, and verify the report appears in the local Ozone queue

## 3. AppView label ingestion

- [ ] 3.1 Add the labeler config (own labeler DID and URL, Bluesky labeler DID and URL, both optional) to `AppViewConfig`; verify `AppViewConfigTest`
- [ ] 3.2 Add Flyway `V27__labels_and_account_status.sql` with the `labels`, `label_cursors` and `moderation_access` tables and `actors.status`, in H2-portable SQL, plus the matching `Tables.kt`; verify `MigrationVersionsTest`
- [ ] 3.3 Add a CBOR dependency to `:server-appview` and a `subscribeLabels` consumer per labeler that resumes from its cursor and is isolated on failure; verify tests with a fake label stream for resume, a disconnect, and one stream failing
- [ ] 3.4 Add label signature verification (canonical DAG-CBOR re-encode, `#atproto_label` key, one key refresh on failure) using the existing BouncyCastle code; verify with fixture labels from local Ozone and a captured Bluesky label, plus a tampered label that fails
- [ ] 3.5 Apply rules: trusted sources only, Bluesky account-DID subjects for athletes only, newest `cts` wins, `neg` removes, `exp` filtered; verify unit tests for each `moderation/labels` scenario on these rules

## 4. AppView enforcement

- [ ] 4.1 Store `#account` status in `Indexer` and add the `served` predicate (account status and `!takedown`, author exempt) to `admits`; verify `AccountStatusTest` (takendown, suspended, deactivated, reactivated, restart) and takedown tests for activity, comment and profile
- [ ] 4.2 Add the `unhidden` predicate to list and count queries only (`shownInFeeds`, comments, likes, search, follow lists), keeping direct lookups; verify `!hide` tests, including that counts still match the pages
- [ ] 4.3 Refuse media in `BlobRedirects` for taken-down activities, profiles and accounts, except to the author; verify tests for public redirects and private media
- [ ] 4.4 Attach `labels` to `activityView`, `commentView` and `profileView` with one batched query per page, and update the lexicon JSON and Kotlin defs; verify view tests and the `lexicon-auditor` agent
- [ ] 4.5 Run `EXPLAIN` on the timeline and comments with labels and statuses present on the docker Postgres; verify there is no sequential scan of `labels`

## 5. Moderator access to private subjects

- [ ] 5.1 Add the lexicon and route for `app.activitytracker.moderation.getSubject`: verify the caller's team role (cached), check open review status through the AppView's triage membership for non-public subjects, sign moderator photo URLs, write `moderation_access` entries, and refuse when Ozone is unreachable. Verify tests with a fake Ozone for each `moderation/private-subjects` scenario
- [ ] 5.2 Add a scheduled prune of `moderation_access` rows older than 400 days; verify a test with a fixed clock
- [ ] 5.3 Use the `new-screen` skill to add a Moderation screen (desktop and web only) in Settings for team members. It opens a subject by URI and shows "not under review" on not found. Verify presenter tests, and check it on the desktop app with Compose Hot Reload

## 6. Client label treatment

- [ ] 6.1 Add a `ModerationDecision` mapping in `core/data` from the view's labels and its author's labels to Takedown, Hidden, Blur, Warn or None, ignoring unknown values; verify `commonTest` table tests
- [ ] 6.2 Add blur with tap-to-reveal (current view only) for feed rows, the gallery and avatars, a collapsed card with a warning for hidden content opened by URI, a warning chip, and a taken-down marker on the author's own content, all through `ui/adaptive` wrappers; verify presenter tests and desktop Hot Reload screenshots
- [ ] 6.3 Update the copy in the block confirmation, the report sheet and the privacy text to say moderators can view reported non-public content; verify the strings in the screens

## 7. Integration

- [ ] 7.1 On the local stack, report bob's Followers activity as alice, open it as a moderator in the app, apply `!hide` and then `!takedown` in Ozone, and check alice's feed, the lookup and the photos at each step, then negate. Verify each expected outcome
- [ ] 7.2 Run `./gradlew check` and verify it passes
