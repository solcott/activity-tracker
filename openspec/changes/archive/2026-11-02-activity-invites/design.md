# Design: activity-invites

## Context

See proposal.md for why, and the specs for behavior. This is the current state the design builds on:

- **Grouping.** `server/appview` already pairs activities for `feed/group-activities`. Matched pairs live in `activity_group_pairs` (V47). `GroupPairing` writes them, and `api/GroupViews.kt` `partnersOf()` serves them, rechecking the pair rule at read time (`pairRuleHolds()`) and applying `visibleTo`, blocks and mutes. Pairs are dropped from about ten call sites (`GroupIndex.dropPairsOf/dropPairsOfActivities/dropPairsBetween`, from `Indexer`, `MatchInputs`, `SegmentIndex` and `SegmentMatcher`).
- **Self-space indexing.** `self` records are indexed through `Indexer.spaceWrite/spaceDelete`, gated by `Indexer.spaceCollections(skey)` (`SEGMENT_COLLECTIONS` ⊂ `SELF_COLLECTIONS`). `graph.followDecline` is deliberately never stored. The client reads declines straight from `self` (`FollowRequestsRepository`).
- **Access.** Access is `TierAccess.visibleTo` per tier. Streams come from `ActivityStreamsViews` (per-tier records merged) and details from `DetailsViews.detailsOf`. The AppView never trims: polylines are already trimmed by the athlete's client (`PrivacyZones`/`PublishTrim`). Streams hold no positions. Exact positions live only in `feed.activityTrack` in `self`, which is never served.
- **Client.** Writes go through `ActivityRepository.publish(draft)` → outbox. Activity defaults come from `ActivityDefaults`. Followers come from `ProfileRepository.followers()`. The overlap check is `FeedRepository.ownActivitiesStartedBetween` + `ActivityMatcher`.
- **Scope.** `space:` collections are declared in `lexicons/app/activitytracker/space/athlete.json`. The PDS grants them at sign-in, so older sessions lack new ones.

## Goals / Non-Goals

**Goals:**
- Invites and invite pairs that never leak who was invited to anyone but the two athletes.
- A copy built only from data the invite grants, written through the normal publish path.
- Invite pairs that reuse the Together-with views without disturbing matched-pair bookkeeping.

**Non-Goals:**
- Push notifications or email. The inbox is pull-only, and the invite record is the future hook.
- Segment efforts on copies. A copy has no `feed.activityTrack` and so no `feed.segmentTrack`, because the polyline has no timestamps. Copies don't appear on leaderboards.
- Inviting non-followers or people without an account.
- Syncing later edits from the original to the copy.

## Decisions

### D1. The invite is a `self`-space record with a deterministic rkey
`app.activitytracker.feed.activityInvite` has `{subject: at-uri of the activity, invitee: did, createdAt}` and is stored in the inviter's `self` space. Its rkey is `{activityRkey}:{inviteeDid}`, which is valid rkey charset and well under 512 chars. That makes adding idempotent and cancelling a single delete. The AppView ignores a record whose rkey doesn't match its fields, or whose subject isn't the author's own activity.
- *Alternative: a public repo record.* Rejected, because it reveals who was there before they consent.
- *Alternative: the inviter's `interactions` space.* It works, but `self` is already synced and is where the grouping choices live.
- *Alternative: a TID rkey.* That needs dedupe at index time, and cancelling would need a lookup.

### D2. Lapse and eligibility are derived at read time, with no stored state
`activity_invites` stores every invite that passes the D1 shape checks. Each query filters it at read time:
- the invitee `effectiveFollow`s the inviter;
- not `blocked(inviter, invitee)`;
- the activity exists;
- at most the first 20 per activity by `created_at`.

The same rules hold in the read path, so a follow coming back revives a lapsed invite with no reindex, which matches the spec.
- *Alternative: mark invites lapsed from follow and block indexers.* Rejected: it adds more write paths for the same answer.

### D3. Invite pairs get their own table, unioned into `partnersOf`
A new table `activity_invite_pairs` has `(original, copy, inviter, invitee, original_tier, copy_tier, invite_rkey, paired_at)`. `partnersOf()` unions it with `activity_group_pairs`. For invite rows it checks this rule instead of `pairRuleHolds()`:
- the invite still exists;
- the copy still has `copy_of` = original;
- no block either way;
- the original's segment track isn't `group_excluded`.

It then applies the same `visibleTo`, block and mute checks. Tiers are computed with the existing `narrowestOf(activity, START_TIME, MAP)`. The one exception: when the viewer is the row's inviter or invitee, `visibleTo` is skipped, so the two always see each other on their own activities. The end-to-end run found that without it, a Followers ride with an Only-me Map and an Only-me copy showed neither owner the other, and the copy offered Leave group with no group in sight. Skipping it reveals nothing new: the inviter already sees who joined on Add people, and adding granted the invitee the original's start time and route.
- *Alternative: a `source` column on `activity_group_pairs`.* Rejected. Every existing drop site (grouping Off, re-match, track delete) would need auditing so it doesn't drop invite pairs, and a missed site silently breaks invite pairs. A separate table means the existing drop sites can't touch invite pairs. Invite pairs are dropped only by:
  - invite delete;
  - copy/original delete or `copyOf` change;
  - `self` space delete;
  - account delete;
  - a block, which is also rechecked at read.

### D4. `copyOf` on `feed.activity` is an at-uri, verified once at index
`copyOf` is a plain `at-uri`, mirroring `recordingOf`. A strongRef isn't needed, because the copy is a snapshot and nothing is re-fetched. When the AppView indexes an activity with `copyOf`, it inserts an invite pair only if all of these hold (`feed/activity-invites` "copyOf is untrusted"):
- an `activity_invites` row names (original author, original, copy author);
- that row passes D2;
- no invite pair exists yet for that invite.

The column is `activities.copy_of`. Removing `copyOf` on edit drops the pair.

### D5. Three queries
- `feed.getActivityInvites` (auth, cursor): the caller's incoming invites that pass D2 and have no pair yet. Each item has the invite URI, the inviter's `profileViewBasic`, and a summary: sport, granted start, distance, elapsed time, title, polyline. Newest first.
- `feed.getActivityInvite(invite)` (auth, invitee only): one invite with its grant. It builds a `grantedView` from the activity record, details records and streams records of every tier, keeping only core fields and the START_TIME, MAP and PACE_SPEED categories. Streams parts use `getActivityStreams`'s part format with channels filtered by category. The AppView fetches them with its own space credential (`PdsBlobStreamSource`), as it does for tier streams. If the invite fails D2 or has already been accepted, it answers not found.
- `feed.getActivityInvitees(activity)` (auth, owner only): that activity's invites that pass D2, each Pending or Joined (Joined = an invite pair exists).

The grant is implemented as a category filter passed into `DetailsViews` and `ActivityStreamsViews` in place of `visibleTo`, reached only from `getActivityInvite`. `getActivity` and the others keep using `visibleTo` unchanged.
- *Alternative: an `invite` param on `getActivityStreams`/`getActivity`.* Rejected: it widens the access paths of existing queries.

### D6. Declines are client-only
`app.activitytracker.feed.activityInviteDecline` has `{subject: invite at-uri, createdAt}` and a TID rkey, and is stored in the invitee's `self` space. The AppView never stores it (added to the never-stored list next to `graph.followDecline`). The client lists its declines from `self` and splits `getActivityInvites` into Pending and Declined. The profile count is Pending's size. Accepting from Declined deletes the decline after publishing.

### D7. The copy goes through `ActivityRepository.publish`
`ActivityInvitesRepository` (core/data, new) builds an `ActivityDraft` from `getActivityInvite`:
- the granted summary fields;
- streams of the granted channels;
- `copyOf`;
- the invitee's chosen visibility and categories.

The route is the decoded granted polyline, passed as a *route without timestamps*. The draft gains a copy flag so that `queue()`:
- trims the route by the invitee's zones (`PublishTrim`), as for any polyline, including the no-zones-read rule;
- writes no `feed.activityTrack`, no `feed.segmentTrack` and no source file.

The outbox makes the accept survive going offline. The first copy indexed wins (D4), so a double accept on two devices yields one pair.

### D8. Add and cancel are direct writes; leaving is an edit
Adding and cancelling are direct `putRecord` and `deleteRecord` calls to `self`, like `FollowRequestsRepository.decline` and group prefs saves, so failures surface at once. Leaving the group on a copy is an ordinary edit that clears `copyOf` (through `ActivityManager`, outbox). The scope check reuses the group-prefs pattern: if the session's granted `space:` collections lack `feed.activityInvite`, the app shows "sign in again".

### D9. Client modules
- **New feature `invites`** (`:feature-invites-api/-presenter/-ui`):
  - `ActivityInvitesScreen`: Pending and Declined, modelled on `FollowRequestsScreen`.
  - `AcceptInviteScreen`: summary map, the overlap warning via `ownActivitiesStartedBetween` + `ActivityMatcher`, visibility and category pickers prefilled from `ActivityDefaults`, editable title, and Accept/Decline.
  - `AddPeopleScreen`: the followers list from `ProfileRepository.followers()` minus anyone already invited, the grant notice, and invitees with Pending/Joined and Cancel/Remove.
- **feature/detail** opens `AddPeopleScreen` through `:feature-invites-api` and shows Leave group on a copy.
- **feature/profile** shows the Activity invites entry with its count.
- Samples go in `:app-fixtures`.

## Risks / Trade-offs

- [The grant exposes Map or Start time the inviter set narrower] → This is consent by adding. The add screen states it, the grant is limited to that invite's lifetime and that invitee, and only the already-trimmed polyline is served, never the track.
- [The inviter's privacy zones protect only the polyline. Streams have no positions, so nothing else leaks location] → The copy's polyline is trimmed again by the invitee's zones. The copy never gets a full track.
- [Copies can't earn segment efforts, which users may expect] → This is a stated non-goal. If later wanted, the AppView could match the inviter's segment track on the copy's behalf.
- [The order of indexing between `self` (invite) and the copy's repo/space write could put the copy first] → In practice the invitee accepts only after the invite was served, so the invite is already indexed. A copy indexed without a valid invite stays unpaired, which is the safe failure.
- [An accepted invite whose pair the invitee then leaves is gone for good] → Accepted by the spec. Re-inviting creates a new deterministic rkey only after a cancel, so the inviter cancels and re-adds.
- [Unioning a second table into `partnersOf` adds a query per page] → It is batched over the page's URIs, the same shape as the existing matched-pair query, and both tables are indexed on both columns.

## Migration Plan

- `V48__activity_invites.sql` does three things:
  - creates `activity_invites` (PK `(inviter, rkey)`, indexes on `invitee`, `activity_uri`);
  - creates `activity_invite_pairs` (PK `(original, copy)`, indexes on `copy`, `inviter`, `invitee`, unique `(inviter, invite_rkey)`);
  - adds `activities.copy_of` (nullable, indexed).

  All of this is H2-portable.
- Add the two collections to `space/athlete.json`. Existing sessions keep working and see "sign in again" only for this feature.
- Rollback: older clients ignore `copyOf` and the new records. Dropping the tables loses only pending invites and invite pairs, and copies remain ordinary activities.
