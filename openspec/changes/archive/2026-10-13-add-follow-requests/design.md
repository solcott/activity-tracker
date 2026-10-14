# Design

## Context

- **Follows today.** `graph.follow` records are indexed into `follows` (`uri`, `author_did`, `subject_did`, `indexed_at`) by `Indexer`. Every reader queries that table directly:
  - `Views.timeline`: authors = the viewer's follow subjects.
  - `Views.profile`: counts and `viewer.following`.
  - `GraphViews.followPage`: lists, keyset on `(indexed_at, uri)`.
  - `ActorDetails.follows`/`isMutual`: used by `SpaceAccess.canRead` and `ActivityStreamsViews.mayView`.
- **Tiers today.** `Visibility`/`AthleteSpace` define `public`, `mutuals` and `self`. `AthleteSpaces.TIERS` is `[mutuals, self]`. `ProfileRepository.withHiddenDetails` reads `mutuals` for anyone the viewer follows. `VisibilityPicker` is a three-segment `AppSegmentedControl`.
- **Client follow state.** `ProfileRepository.follow/unfollow` call `putRecord`/`deleteRecord` directly (not the outbox). `PendingFollows` keeps the optimistic state until the AppView catches up.
- **Spaces.** Space records never reach Jetstream. The PDS grants only the collections that `space/athlete.json` declares, at sign-in, so adding one means signing in again (`ScopeMissingError`, which edit profile already turns into a sign-in-again prompt).
- **No `applyWrites`** descriptor in `core/lexicon` yet. The PDS caps one `applyWrites` at 200 writes.
- **Next Flyway version:** `V14`.

## Goals / Non-Goals

**Goals:**
- One definition of "effective follow", applied in SQL wherever follows are read, so no reader can forget it.
- Changing the policy, approving and removing never rewrite rows in bulk on the AppView.
- Declines never leave the athlete's PDS: not in a public record, and not sent to the AppView.

**Non-Goals:**
- Notifications that a request arrived or was approved (there's no notification system).
- Blocking, or stopping someone from following again. Declined accounts stay quietly pending.
- Activity visibility (see proposal).
- Bluesky follows. They still count for nothing.

## Decisions

### D1. Effective follow is a query predicate, not a stored flag
In `ActorDetails.kt`, add `effectiveFollows(): Op<Boolean>` for use on `Follows` rows:

```
follows.subject_did NOT IN (SELECT did FROM actors WHERE follow_policy = 'approve')
OR EXISTS (SELECT 1 FROM follow_approvals a
           WHERE a.author_did = follows.subject_did AND a.subject_did = follows.author_did)
```

- **Applied everywhere follows are read.** `follows(a, b)`, timeline authors, both counts, `followPage` and `viewer.following` all AND it in. `isMutual` keeps calling `follows` both ways, so it inherits the rule.
- **Requested state.** `viewer.followRequested` is "the viewer's follow exists and the predicate fails for it".
- **No profile row means open.** A subject without an `actors` row is treated as `open`, as the specs require for a missing policy.
- **Why not a stored flag:** an `effective` column on `follows` would have to be recomputed for every follower when the policy flips, and on every approval in either indexing order. The predicate is order-independent by construction, which covers the "approval indexed first" scenario.
- **Cost:** one indexed `EXISTS` per row. `follow_approvals` gets an index on `(author_did, subject_did)`.

### D2. Approvals: rkey = follower DID, and only those are indexed
New public record `app.activitytracker.graph.followApproval` with `"key": "any"`, `subject` (did) and `createdAt`. Clients always write it at rkey = `subject`.
- **The indexer drops any approval whose rkey ≠ `subject`.** One approval per pair means deleting that record really does remove the follower (Remove follower), even if another client wrote extra approvals under TIDs.
- **Why DID as rkey:** `putRecord` makes approving idempotent, there's nothing to look up before deleting, and approval survives refollows (keyed on the person, not the follow record).
- **Alternative rejected:** a strongRef to the follow record would require approving again after every refollow, and lookups by URI.
- **Table:** `follow_approvals(uri PK, author_did, subject_did, created_at, indexed_at)`. Deleting an actor deletes their approvals too.

### D3. `followPolicy` lives on the profile record and the `actors` row
`actor.profile.followPolicy` (`knownValues: open, approve`), mirrored in `ProfileRecord`. The indexer stores it in a new nullable `actors.follow_policy` column, keeping only the known value `approve` (anything else becomes null, meaning open). `profileView.followPolicy` returns `approve` or `open`.
- **Why public:** requesters need it to show "Request to follow" or "Requested", and it isn't sensitive. A private policy would also be unenforceable, since the AppView has to know it.

### D4. Declines in the `self` space, filtered by the client
New record `app.activitytracker.graph.followDecline` (`"key": "any"`, `subject`, `createdAt`), rkey = follower DID, written only to the athlete's `self` tier space through `AthleteSpaces`. It's added to `space/athlete.json`'s `collections`, and it isn't in `Nsid.indexedCollections`.
- **The AppView knows nothing about declines.** `getFollowRequests` returns every pending follow. The client lists its own declines (owner read, paged `listRecords`) into a set, shows requests minus the set, and shows Declined as the requests within the set. The Declined list therefore needs no separate AppView query, and a decline whose follow is gone just drops out (spec: Declined list).
- **Approve** = put the approval, then delete the decline. If the delete fails, the approval still wins, because the requester is no longer pending.
- **Alternatives rejected:**
  - A public decline record (rejected in exploration).
  - Sending the decline set to the AppView as a filter parameter. It would leak declines to the operator, which breaks "the AppView stores nothing private" in spirit.

### D5. `getFollowRequests`
- **Shape.** Query, auth required (the caller is the subject). Params `limit` and `cursor`. Output `requests: [profileViewBasic]` and `cursor`. Keyset on `(follows.indexed_at, follows.uri)` descending, reusing the `follows_subject_page_idx` index.
- **Open policy returns empty.** This falls out of the predicate: with an open policy every follow is effective, so none are pending.
- **The Follow requests count on the profile** comes from the client loading request pages minus declines. It stops after 5 pages (`limit` 100) and then shows "500+". That's plenty for a personal account, and it avoids a count endpoint that would include declined accounts.

### D6. Turning approval on: approvals first, idempotently, in batches
Edit profile's save, when the policy goes from `open` to `approve`:
1. Page `getFollowers` for the athlete. Under an open policy every follow is effective, so this is everyone.
2. List the athlete's existing `followApproval` records.
3. Write the missing approvals with `com.atproto.repo.applyWrites` `#create` (rkey = DID), in chunks of 200.
4. Only then `putRecord` the profile with `followPolicy: approve`.

- **Retries are safe.** If anything fails, the policy isn't written, and a retry skips approvals that already exist (spec: approval batch fails).
- **Why `applyWrites`:** 300 sequential `putRecord`s would be slow and burn rate limit. Listing first keeps `#create` from failing on existing records, which `applyWrites` would reject as a whole batch.
- **Accepted race:** someone who follows between step 1 and step 4 lands in requests.
- **New mirror:** `ApplyWrites` descriptor in `core/lexicon` (create and delete only, since that's all this needs).

### D7. The `followers` tier
- **Constants and tiers.** Add `Visibility.FOLLOWERS`/`AthleteSpace.FOLLOWERS = "followers"`, and `AthleteSpaces.TIERS = [followers, mutuals, self]`.
- **AppView access.** `SpaceAccess.canRead` gets `FOLLOWERS -> follows(user, authority)`. `ActivityStreamsViews.mayView` gets `FOLLOWERS -> isSelf || follows(viewer, author)`.
- **Client reads.** `withHiddenDetails` reads `[followers, mutuals]` when `viewer.following != null && viewer.followRequested != true`, and nothing otherwise. A pending follower therefore asks for no credential (spec).
- **Merging tiers.** Tiers merge under public with `under`, as now. A detail is only ever in one place, so the order among tiers doesn't matter.
- **Older clients** that don't know `followers` see an unknown visibility. The AppView already treats unknown streams visibility as `self`, so nothing leaks.

### D8. Optimistic requester state
- **Follow.** `PendingFollows` records whether the write went to an `approve`-policy athlete. `applyTo` then sets `followRequested = true` and leaves `followersCount` alone. For a refollow of an athlete who approved them earlier, the optimistic "Requested" turns into "Following" on the next load. That's acceptable, and better than guessing "Following".
- **Cancel.** "Requested" cancels through the existing `unfollow` path.

### D9. Screens
- **New `FollowRequestsScreen`** (via the `new-screen` skill), with two segments: Requests (Approve or Decline per row) and Declined (Approve per row). It's opened from a Follow requests entry on the athlete's own `ProfileScreen`, shown only when their policy is `approve`.
- **Why not more `FollowListKind`s:** follow lists are read-only and paged from the AppView, while requests mix AppView paging with a client-side decline set and row actions.
- **`FollowListScreen`** gains Remove on the athlete's own followers list when their policy is `approve`.
- **Edit profile** gains an "Approve followers" switch, saved with the profile (D6).
- **`VisibilityPicker`** gains Followers, and becomes a dropdown (`MenuField`): four segments truncated at phone width (checked at 375 px on the web app). While Followers is chosen and the policy is `open`, it notes that anyone who follows can see it. Every caller (edit profile, training zones, activity detail charts, import) passes the athlete's policy.
- **Re-consent.** A `ScopeMissingError` from a decline write shows the existing sign-in-again `UiError`.

## Risks / Trade-offs

- **[Every session must sign in again once]**, because `followDecline` is added to the space declaration. → The existing sign-in-again prompt appears on the first decline, or the first save of a non-public detail. Only declining needs the new scope: approving, removing and toggling the policy are repo writes.
- **[`getFollowRequests` includes declined accounts]**, so an athlete with many declines pages through more. → The count is capped at 5 pages (D5). The list loads more on scroll, and a page that's entirely declined loads the next one automatically.
- **[A DID that isn't a valid rkey]**, for example a `did:web` with a `%3A` port, can't be approved or declined. Athletes are `did:plc` accounts on a PDS, so this only matters for hand-made test accounts. → Approve and Decline are disabled for such a DID, and the reason is logged.
- **[Four segments may not fit at phone width]** in `VisibilityPicker`. → Check on Android and iOS at 360dp. If labels truncate, switch the wrapper to a dropdown menu (still an adaptive wrapper).
- **[The predicate adds a subquery to the timeline]**, which runs `IN (effective subjects)`. → The subquery is per follow of one viewer, and both sides are indexed. Check the plan on H2 and Postgres with seeded data.
- **[The approval write during the toggle can partly succeed]** → It's idempotent and the policy is written last (D6).
- **[Pending followers still read public activities]** → This is the documented limit. Activity visibility goes to BACKLOG as the follow-up change.

## Migration Plan

1. Migration `V14__follow_requests.sql`:
   - Create table `follow_approvals`, with index `follow_approvals_pair_idx (author_did, subject_did)`.
   - `ALTER TABLE actors ADD COLUMN follow_policy VARCHAR(16)`.
   - It must be H2-portable.
2. Existing data needs no backfill: every actor's policy is null (open), so every existing follow stays effective. Approvals published before this AppView version are picked up only by a Jetstream rewind. There are none, because no client writes them yet.
3. Deploy the AppView before clients. Old clients never send `approve`, and the new view fields are optional, so old clients keep working.
4. Rollback: an AppView without the predicate treats every follow as effective. An athlete's `followers` tier would then admit pending followers, so roll back the clients' Followers option along with it.
