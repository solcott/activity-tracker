# Tasks

## 1. Lexicons and the `core/lexicon` mirror

- [x] 1.1 Add `lexicons/app/activitytracker/graph/followApproval.json`: a record with `key: any`, required `subject` (did) and `createdAt`, and a description saying the rkey is the subject's DID and the record is public (D2). Add `Nsid.GRAPH_FOLLOW_APPROVAL` to `indexedCollections`. Verify the JSON matches the other `graph/*.json` files in style.
- [x] 1.2 Add `graph/followDecline.json`, with the same shape and a description saying it lives only in the athlete's `self` tier space (D4). Add `Nsid.GRAPH_FOLLOW_DECLINE`, kept out of `indexedCollections`, and add the collection to `space/athlete.json`'s `collections`. Verify that `indexedCollections` doesn't contain it.
- [x] 1.3 Add `graph/getFollowRequests.json`: query, params `limit`/`cursor`, output `requests` (`profileViewBasic[]`) plus `cursor`, and a description saying it requires auth (D5).
- [x] 1.4 Update `actor/profile.json` (`followPolicy`, `knownValues` `open`/`approve`) and `actor/defs.json` (`#profileView.followPolicy`, `#viewerState.followRequested`, and `followers` in `#visibility`, with its description). Also update `feed/activityStreams.json` (`followers` in `visibility.knownValues`) and the descriptions that list the tiers.
- [x] 1.5 Mirror these in `core/lexicon`:
  - `FollowApprovalRecord` and `FollowDeclineRecord` (`$type`);
  - `FollowPolicy` constants;
  - `ProfileRecord.followPolicy`, `ProfileView.followPolicy`, `ProfileViewerState.followRequested`;
  - `Visibility.FOLLOWERS` and `AthleteSpace.FOLLOWERS`;
  - a `GetFollowRequests` descriptor;
  - an `ApplyWrites` descriptor with `#create`/`#delete` writes (D6).

  Add round-trip cases to the lexicon serialization tests. Verify `./gradlew :core-lexicon:jvmTest`.
- [x] 1.6 Run the `lexicon-auditor` agent on the changed NSIDs and fix what it reports. The AppView layers are expected gaps until group 2. Verify a clean report for the JSON ↔ Kotlin layer.

## 2. AppView: approvals, policy and effective follows

- [x] 2.1 Add `V14__follow_requests.sql` (the `follow_approvals` table, `follow_approvals_pair_idx`, `actors.follow_policy`) and the matching `Tables.kt` entries. Verify `./gradlew :server-appview:test --tests "*.MigrationVersionsTest"` and that the full chain runs on H2.
- [x] 2.2 Indexer:
  - upsert `followApproval` records only when rkey == `subject` (D2), and delete them on delete events;
  - store `follow_policy` from profile records, keeping only `approve` (D3);
  - delete approvals in `deleteActor`.

  Test in `JetstreamEventTest`: an approval is indexed, a mismatched rkey is ignored, a delete is applied, an unknown policy value is stored as null. Verify `./gradlew :server-appview:test --tests "*.JetstreamEventTest"`.
- [x] 2.3 Add the `effectiveFollows()` predicate to `ActorDetails.kt` (D1) and apply it in `follows`/`isMutual`, `Views.timeline`, `Views.profile` (counts and `viewer.following`) and `GraphViews.followPage`. Add `viewer.followRequested` and `followPolicy` to `Views.profile`. Test in `AppViewTest`:
  - with an open policy, nothing changes;
  - a pending follower is missing from timeline, counts and both lists;
  - an approval indexed before or after the follow makes it effective;
  - a refollow stays effective;
  - `followRequested` is true only while pending.

  Verify `./gradlew :server-appview:test --tests "*.AppViewTest"`.
- [x] 2.4 Serve `getFollowRequests` (auth required, the caller as subject, keyset paging) in `GraphViews` and `Routes.kt`. Test: lists pending follows newest first and pages them, returns empty for an open policy, answers `401` without a token. Verify `./gradlew :server-appview:test`.
- [x] 2.5 Add the `followers` tier to `SpaceAccess.canRead` and `ActivityStreamsViews.mayView` (D7). Extend `SpaceAccessTest`: an effective follower is admitted, pending and non-followers are refused, a removed follower is refused after indexing, mutuals need effective follows both ways, unknown tiers are refused. Extend `ActivityStreamsTest`: `followers` streams for an effective follower are served, and for a pending follower are not found. Verify `./gradlew :server-appview:test --tests "*.SpaceAccessTest" --tests "*.ActivityStreamsTest"`.
- [x] 2.6 Update `.agents/rules/appview.md`: every read of `follows` goes through `effectiveFollows()`, and the indexer rule for approval rkeys. Verify `scripts/check-agent-sync.sh` passes.

## 3. `core/data`: requests, approvals, declines and the followers tier

- [x] 3.1 Add the `followers` tier to `AthleteSpaces.TIERS`. Add decline operations on the `self` space: put with ensure, delete, and list every subject with paged `listRecords`. Test with `MockEngine`: a decline put creates the `self` space when it's missing, and listing follows cursors. Verify `./gradlew :core-data:jvmTest`.
- [x] 3.2 Add to `ProfileRepository`:
  - `approve(did)` (`putRecord` at rkey = DID, then delete any decline);
  - `decline(did)`;
  - `removeFollower(did)`;
  - `followRequests(cursor)`, which returns requests minus declines and auto-loads past an all-declined page;
  - `declined()` and `requestCount()` (capped at 500, D5).

  Disable approve and decline for a DID that isn't a valid rkey. Test in a new `FollowRequestsRepositoryTest` with `MockEngine`: approve writes the right record and clears the decline, declines are filtered out of requests and appear in Declined, a declined account whose follow is gone isn't listed, the count cap works. Verify `./gradlew :core-data:jvmTest`.
- [x] 3.3 Make `ProfileRepository.save` handle `followPolicy` (D6): going from `open` to `approve`, page followers, list existing approvals, `applyWrites` the missing ones in chunks of 200, and only then put the profile record. Going from `approve` to `open`, just put the record. Test: approvals are written before the policy, existing approvals are skipped, a failed chunk leaves the policy `open`, and a retry writes only the rest. Verify `./gradlew :core-data:jvmTest --tests "*.ProfileRepositoryTest"`.
- [x] 3.4 Update `PendingFollows` and `follow()` for the requested state (D8). Make `withHiddenDetails` read `[followers, mutuals]` only for an effective follow (D7). Test in `ProfileFollowTest`: following an approve-policy athlete shows requested with no count change, cancelling clears it. In `ProfileRepositoryTest`: an approved follower sees Followers details, a pending follower makes no space request, and an own profile shows all four tiers. Verify `./gradlew :core-data:jvmTest`.
- [x] 3.5 Update `.agents/rules/data-and-auth.md`: declines live in the `self` space, approvals are repo writes keyed by DID, and approval mode writes approvals before the policy. Verify `scripts/check-agent-sync.sh` passes.

## 4. Screens

- [x] 4.1 Scaffold `FollowRequestsScreen` with the `new-screen` skill (D9): Requests and Declined segments, Approve/Decline and Approve events, paging, and errors including `SignInAgain` on `ScopeMissingError`. Add a `FollowRequestsPresenterTest`: approving or declining moves a row, approving from Declined clears it, and a scope-missing decline shows `SignInAgain`. Verify `./gradlew :app-presenters:jvmTest --tests "*.FollowRequestsPresenterTest"`.
- [x] 4.2 Profile:
  - show a Follow requests (n) entry on the athlete's own profile when their policy is `approve`, opening `FollowRequestsScreen`;
  - label the follow button Requested while pending, and cancel on tap.

  Extend `ProfilePresenterTest` for both. Verify `./gradlew :app-presenters:jvmTest --tests "*.ProfilePresenterTest"`.
- [x] 4.3 `FollowListScreen`: offer Remove on the athlete's own followers list when their policy is `approve`, and remove the row optimistically. Extend `FollowListPresenterTest`, including that an open policy offers no Remove. Verify `./gradlew :app-presenters:jvmTest --tests "*.FollowListPresenterTest"`.
- [x] 4.4 Edit profile: add an "Approve followers" switch to the state and the UI, saved through `ProfileRepository.save`, with the busy state and error while approvals are written. Extend `EditProfilePresenterTest`. Verify `./gradlew :app-presenters:jvmTest --tests "*.EditProfilePresenterTest"`.
- [x] 4.5 `VisibilityPicker`: add Followers and a `followersNote` shown for an open policy. Pass the athlete's policy from edit profile, training zones, activity detail charts and import. Add localized strings. Check that the picker fits at 360dp on Android and iOS, or switch it to a dropdown. Verify `./gradlew :composeApp:jvmTest composeStabilityCheck` and a desktop screenshot via the hot-reload MCP.
- [x] 4.6 Build the `FollowRequestsScreen` UI and the profile and follow-list additions from `ui/adaptive` wrappers. Verify `./gradlew check` (detekt `UseAdaptiveComponents`, ktfmt) and screenshots of Requests, Declined and the Requested button.

## 5. Docs and end-to-end

- [x] 5.1 Update AGENTS.md's architecture paragraph (effective follows, the four tiers, declines in `self`) and `.agents/rules/lexicons.md` (rkey = DID for approval and decline records). Add "Private activities" to BACKLOG.md, with what's left: activity visibility, tier-space activities, timeline fan-out, kudos and comments. Verify `scripts/check-agent-sync.sh` passes.
- [x] 5.2 Run end to end on the local stack (`scripts/dev.sh --seed alice`, plus bob and carol):
  - alice turns on Approve followers;
  - bob follows and sees Requested; alice approves; bob sees alice's Followers detail and charts;
  - carol follows; alice declines; carol still sees Requested and none of the Followers data; alice approves carol from Declined;
  - alice removes bob, and bob loses access.

  Verify each step in the app, and run `./gradlew check`.
