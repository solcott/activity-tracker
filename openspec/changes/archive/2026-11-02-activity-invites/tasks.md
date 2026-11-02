# Tasks

## 1. Lexicons and Kotlin mirror

- [x] 1.1 Add `lexicons/app/activitytracker/feed/activityInvite.json` (record, `subject` at-uri, `invitee` did, `createdAt`; description: stored only in `self`, rkey `{activityRkey}:{inviteeDid}`) and `feed/activityInviteDecline.json` (record, `subject` invite at-uri, `createdAt`, TID key; never stored by the AppView). Add their records in `core/lexicon` and their `Nsid` constants (not in `indexedCollections`), and list both in `space/athlete.json` `collections`. Verify with `AtJson` round-trip tests.
- [x] 1.2 Add the optional `copyOf` (at-uri) to `feed/activity.json` and its Kotlin record. Verify with a round-trip test, with the field absent and with it present.
- [x] 1.3 Add the queries `feed.getActivityInvites` (cursor, limit → `inviteView`s), `feed.getActivityInvite` (`invite` → `inviteView` + granted summary + streams parts) and `feed.getActivityInvitees` (`activity` → invitees with `pending|joined`). Add the `feed.defs` objects they use and their `XrpcQuery` descriptors (D5). Verify with the `lexicon-auditor` agent over these NSIDs and `feed.activity`.

## 2. AppView: storage and indexing

- [x] 2.1 Write `V48__activity_invites.sql`: `activity_invites`, `activity_invite_pairs` and `activities.copy_of` with the keys and indexes in the design's Migration Plan, all H2-portable. Add the Exposed tables to `db/Tables.kt`. Verify that `./gradlew :server-appview:test` boots the migration on H2.
- [x] 2.2 Index `feed.activityInvite` from `self` only (add it to the self set in `Indexer.spaceCollections`, with `index()`/`delete()` branches). Reject a record whose rkey doesn't match its fields or whose subject isn't the author's activity. Delete removes the invite and its invite pair. `dropSpace(self)` and account delete drop the author's invites and invite pairs. Add `feed.activityInviteDecline` to the never-stored list. Verify with indexer tests: a repo or `followers` copy is ignored, a mismatched rkey is ignored, a decline in `self` stores nothing, and deleting the invite drops its pair.
- [x] 2.3 Index `activities.copy_of`. On activity index, insert an invite pair when D4 holds (a live invite passing D2, the first copy for that invite). Drop the pair when `copyOf` is removed or changed, or when either activity is deleted. Verify with indexer tests: valid copy pairs, forged `copyOf` doesn't, a second copy for the same invite doesn't, an edit clearing `copyOf` drops the pair, and deleting the original keeps the copy unpaired.
- [x] 2.4 Drop invite pairs between two authors on a block either way (beside `dropPairsBetween` in `Indexer`). Confirm that choosing grouping Off and re-matching leave `activity_invite_pairs` alone. Verify with indexer tests: block drops, Off keeps.

## 3. AppView: serving

- [x] 3.1 Add the shared D2 filter for invites (effective follow invitee→inviter, no block, activity exists, first 20 per activity) in an `api/InviteViews.kt`. Serve `getActivityInvites` (the caller's incoming, unpaired, newest first, paged) and `getActivityInvitees` (owner only, Pending/Joined). Register the routes. Verify with route tests:
  - only the inviter and invitee are served;
  - a pending follow, an unfollow and a block each hide the invite, and refollowing revives it;
  - a 21st invite is ignored;
  - a deleted activity hides its invites;
  - a Joined invitee is listed as Joined and gone from the inbox.
- [x] 3.2 Serve `getActivityInvite` with the grant (D5): pass a category filter (core, START_TIME, MAP, PACE_SPEED) into `DetailsViews` and `ActivityStreamsViews` in place of `visibleTo`, over every tier, and filter streams channels by category. Verify with route tests:
  - an Only-me Map is served to the invitee through the invite but `getActivity` still hides it;
  - heart rate and power are never served;
  - a non-invitee gets not found;
  - a cancelled or accepted invite gets not found;
  - no full track is served.
- [x] 3.3 Union `activity_invite_pairs` into `GroupViews.partnersOf`, with the invite-pair rule (the invite exists, the copy still names the original, no block, the original not `group_excluded`) and the existing `visibleTo`, block and mute filters at the `narrowestOf` tiers. Verify with Views tests:
  - both athletes with grouping Off still list each other;
  - an Only-me copy is hidden from third viewers;
  - unfollow after accept keeps the pair;
  - the original leaving its group hides the pair until rejoin;
  - a muted invitee is hidden from the muter.

## 4. Client data

- [x] 4.1 Add the `ActivityInvitesRepository` in `core/data` and bind it in `SessionScope`. It has:
  - `add(activity, did)` and `cancel(...)` as direct `self` writes (D8);
  - `invitees(activity)`;
  - `incoming()` split into Pending and Declined by listing `feed.activityInviteDecline` from `self`;
  - `decline(invite)` as a direct write, and `undecline` on accept;
  - `canInvite`, from the session's granted `space:` collections.

  Verify with `MockEngine` tests: an offline add reports failure and leaves the invitees unchanged, a decline moves the invite to Declined, and an old scope reports sign-in-again.
- [x] 4.2 Add the copy path: `accept(invite, choice)` builds an `ActivityDraft` from `getActivityInvite`, with granted fields and channels, `copyOf`, the title, and the invitee's visibility and categories. The route is a timestamp-free polyline route. Extend `ActivityDraft`/`ActivityRepository.queue()` with a copy flag that trims the route by the invitee's zones via `PublishTrim` and writes no `activityTrack`, `segmentTrack` or source. Verify with data tests:
  - the copy holds no heart rate, power, calories, photos, gear or description;
  - its polyline is trimmed by the invitee's zone;
  - with no zones read it is written with no polyline;
  - no track or segment track is queued;
  - accepting from Declined deletes the decline.
- [x] 4.3 Let editing a copy clear `copyOf` (Leave group on a copy) through `ActivityManager`. Verify with a test that the edit writes the record without `copyOf` through the outbox.

## 5. Client screens

- [x] 5.1 Scaffold `feature/invites/{api,presenter,ui}` with the `new-screen` skill: `ActivityInvitesScreen` (Pending and Declined, like `FollowRequestsScreen`) and `AcceptInviteScreen` (route map and summary, overlap warning via `FeedRepository.ownActivitiesStartedBetween` + `ActivityMatcher`, visibility and category pickers from `ActivityDefaults`, editable title, Accept and Decline). Add strings to `:ui-resources` and samples to `:app-fixtures`. Verify with presenter tests (accept publishes and leaves the list, decline moves to Declined, the overlap warning shows yet allows accept), UI tests, and `./gradlew :composeApp:jvmTest --tests "*.ContentLoadingConformanceTest"`.
- [x] 5.2 Add `AddPeopleScreen` in `feature/invites`: the athlete's followers (`ProfileRepository.followers()`), excluding the invited, up to 20; the grant notice; invitees as Pending or Joined with Cancel or Remove; and sign-in-again on an old scope. Verify with presenter tests: an offline add shows an error and keeps the list, cancel removes a pending invite, Remove on a Joined invitee deletes the invite, and the 20 cap hides Add.
- [x] 5.3 Wire up the entry points through `-api` only:
  - **feature/detail:** Add people on the owner's page; Leave group on the owner's own copy.
  - **feature/profile:** the Activity invites entry with its count on the athlete's own profile, hidden at zero.

  Verify with presenter tests for both features, and with `./gradlew checkModuleBoundaries composeStabilityCheck`.
- [x] 5.4 Add screenshot references for the inbox, accept, add-people and profile-entry samples. Verify with `./gradlew :androidApp:updateDebugScreenshotTestDefaultTestSuite`, then `testDebugScreenshotTestDefaultTestSuite` passing.

## 6. Docs and integration

- [x] 6.1 Describe activity invites in AGENTS.md's data-flow paragraph: the private invite in `self`, the grant via the invite query only, copies with `copyOf`, invite pairs independent of grouping prefs, and declines never stored. Verify that `scripts/check-agent-sync.sh` passes.
- [x] 6.2 End to end on the local stack (`scripts/dev.sh --seed alice` plus `docker/scripts/create-account.sh bob`, with Bob following Alice):
  - Alice adds Bob to a Followers ride with an Only-me Map;
  - Bob sees the invite with its route and accepts at Only me;
  - both pages show Together with to their owners;
  - Alice removing Bob drops it.

  Verify by observing each step in the desktop app.

  Run in the web app instead, with fresh accounts `ana.test` and `ben.test`, the ride imported from a generated GPX. It found two things, both fixed: the inbox kept an invite answered on its own page until the AppView caught up (the repository now lays the session's answers over every read), and the ordinary pair viewer rules hid the two owners from each other in exactly this setup (members of an invite pair now always see each other; see the `feed/group-activities` delta and D3).
- [x] 6.3 Verify that `./gradlew check` passes.

## Workflow follow-up

- Archive the change with `/opsx:archive` after review, syncing `feed/activity-invites`, `feed/group-activities` and `spaces/appview-syncer`.
