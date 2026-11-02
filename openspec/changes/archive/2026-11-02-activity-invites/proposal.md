# Proposal: activity-invites

## Why

Friends often ride or run together when only one of them recorded: someone forgot to start their watch, their battery died, or they have no device. Group activities (`feed/group-activities`) link activities only when both athletes recorded a track and opted in, so the athlete who didn't record ends up with nothing on their profile. They have to enter it by hand, and it is never linked to the ride they shared.

## What Changes

- An athlete can add up to 20 people who effectively follow them, and with whom neither has a block, to one of their own activities. Each person added gets an invite.
- The invite is private. It is stored in the inviter's `self` space, and the AppView serves it only to the inviter and the invitee. Nothing public records it.
- An invitee sees their pending invites in an in-app Activity invites list, reached from their own profile with a count. Push notifications and email are out of scope.
- Accepting creates the invitee's own activity: a snapshot copy that holds the original's sport, start, times, distance, elevation, route polyline and speed/elevation charts. It does not copy the inviter's heart rate, power, cadence, calories, physiology, dive data, gear, devices, photos or description. The invitee chooses the copy's visibility and category visibilities, prefilled from their own defaults. The invitee's own privacy zones trim the route again, and the copy names the original in a new `copyOf` field.
- Adding someone grants them, through the invite only, the original's start time, trimmed route and speed data, even where those categories are narrower than the invitee could otherwise see. The add screen says so.
- Declining stays private in the invitee's `self` space, and the inviter keeps seeing the invite as pending. The inviter can cancel a pending invite. An invite lapses when the follow ends or either athlete blocks the other.
- An accepted copy and its original are paired as group partners whatever either athlete's grouping setting says. Viewer visibility, blocks, mutes and Leave group still apply. The copy is independent after that: editing either side doesn't change the other, and deleting either one only drops the pair.

## Capabilities

### New Capabilities
- `feed/activity-invites`: adding followers to an activity, the private invite and its lapse rules, the invitee's inbox, accept (the copy and what it holds) and decline, the access the invite grants, and cancelling.

### Modified Capabilities
- `feed/group-activities`: an accepted copy is paired with its original by invite, regardless of grouping prefs or tracks. Leave group and the pair-change rules cover invite pairs.
- `spaces/appview-syncer`: the AppView also indexes `feed.activityInvite` (and `actor.groupPrefs`) from the `self` space. An activity in a tier space is also served, in part, to a pending invitee through the invite query.

## Impact

- **Lexicons**: new `feed.activityInvite` (self-space record), `feed.activityInviteDecline` (self-space record, never indexed), `feed.getActivityInvites` (the invitee's inbox), `feed.getActivityInvite` (one invite with what it grants) and `feed.getActivityInvitees` (the owner's list) queries, and a `copyOf` field on `feed.activity`. Each is mirrored in `core/lexicon` and `Nsid`.
- **AppView**: indexing invites from `self`, eligibility checks at index and read time, the invite queries and their partial grant, invite-based pairs in the grouping tables and views, and a Flyway migration.
- **Client**: an Add people picker on the owner's activity page, built from their followers, with the pending invites and cancel; an Activity invites list and accept screen (new feature modules); the copy written through the outbox; the decline written directly to `self`; and the `space:` scope gaining the two new collections.
- **No breaking changes.** Old clients ignore `copyOf` and the new records.
