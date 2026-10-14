# Design

## Context

See proposal.md for why. The current state that shapes the approach:

- **Non-public activities live in tier spaces.** They are synced by the AppView as a member (`SpaceSyncer`) and served through `visibleTo` (`TierAccess.kt`). The AppView indexes a space record under its repo-style URI (`at://{did}/{collection}/{rkey}`) and tells copies apart by an `origin` column (`Placement`), so an activity has one URI in every tier.
- **Likes are repo records indexed from Jetstream** into the likes table (renamed from `kudos` by `rename-kudos-to-likes`). That table has no `origin` column. `Views.hydrate` and `getLikes` gate everything on `acceptsInteractions(visibility)`, so they serve nothing for non-public activities.
- **The pinned Spaces-alpha PDS limits what an authority can do with other members' records.** We read its source while exploring:
  - A member writes to their own repo in someone else's space with only their OAuth `space:` scope.
  - `checkUserAccess(write)` runs only when the space host receives the member's `notifyWrite`. A denial only stops tracking and fan-out; the record stays stored.
  - The authority can't delete a member's records. Deleting a space leaves members' records where they are.
  - Each member repo lives on its owner's PDS, so syncing a multi-writer space means `listRepos` plus `listRepoOps` per writer.

## Goals / Non-Goals

**Goals:**
- Likes on non-public activities that nobody but the giver and the AppView can read where they're stored.
- Who sees a like always follows the activity's current tier, with no client action when the tier changes.
- Reuse the existing tier-space machinery: `AthleteSpaces`, the outbox's space target, `SpaceSyncer` and `Placement`.

**Non-Goals:**
- Comments. Comments on non-public activities, and the comment UI and `getComments` for any visibility, are a later change that can store comments the same way.
- Notifications of likes.
- Reading likes without the AppView. A client can't list who liked a non-public activity from the PDSes alone.
- Migrating existing repo likes.

## Decisions

### 1. Likes on non-public activities live in the giver's own `interactions` space

They do not go in the activity's tier space as members' repos, as the backlog first planned. That plan fails on PDS behaviour we can't change:

- Moving an activity leaves members' likes in the old tier's space, readable by that tier's current and future members, and only each giver's client could clean them up.
- Every follower of the tier could list who interacted.
- Each like costs a `checkUserAccess(write)` callout.
- The syncer would need multi-writer sync.

In the giver's own space, the record reveals nothing to anyone else. It never needs cleaning up after a move, and the syncer keeps one repo per space.

The trade-off: only the AppView can show these likes to anyone but the giver. That's already true of how non-public activities reach viewers.

`interactions` is its own key, not `self`. `self` promises that no other person sees what's in it, and these likes are meant to be seen by the activity's audience. A separate key also keeps indexing rules per key: activities only from tiers, likes only from `interactions`.

### 2. The client places a like by the activity's visibility as it sees it, and never moves it

`toggleLike` writes to the repo for a Public view and to `interactions` otherwise, through the outbox (`OutboxEntity.space`). The first such write ensures and announces the space through `AthleteSpaces`.

If the view is stale (the activity just changed tier), the like lands in the "wrong" place. It's still shown correctly, because decision 4 doesn't care where a like is stored. The one cost is that a like on a now-Public activity stays off the firehose.

Alternative considered: always write likes to `interactions`. Rejected because then every liker would need the `space:` scope, and Public likes would disappear from the firehose and from any other AppView.

### 3. Unlike deletes from both places

`viewer.like` is a repo-style URI wherever the like is stored, so the client can't tell where it lives. Unlike queues a delete of the rkey in the repo and in `interactions`. Both deletes are idempotent: deleting a missing record succeeds, and `SpaceNotFound` for a never-created `interactions` space counts as done, as `ActivityManager.delete` already treats deletes from absent places.

Alternatives considered:
- A lexicon field saying where a like is stored. That's a lexicon change for a client-side detail.
- Remembering placement locally. That fails across devices.

### 4. Visibility is checked at serve time, for the viewer and the giver

A like is shown to a viewer only if both the viewer and the giver pass the activity's tier check now. In SQL terms, `visibleTo` is applied twice: once with the viewer, and once with the like's `author_did` as the viewer, correlated per row.

`visibleTo` today takes a constant viewer DID. It gains a form that takes a viewer column, so the giver check can join on `likes.author_did`.

- Public activities skip the giver check, because every giver is admitted, so their counts stay a plain grouped count.
- Self-likes stay rejected at index time, as today.

Alternative considered: checking at index time. Rejected because the follow and the like reach the AppView through different pipes (Jetstream, space sync). A like indexed before its giver's follow would be dropped for good, and an ex-follower's like would stay forever.

### 5. The likes table gets `origin`

A Flyway migration adds `origin`, defaulting to the repo origin for existing rows.

- Like writes and deletes from a space carry `Placement.of(space).origin`.
- `Indexer.dropSpace` removes likes by origin as it does activities.
- A space delete removes a like only if it came from that origin.

Likes don't use the placement's visibility, so `Visibility.known("interactions")` never matters.

Unlike activities, likes need no shadow handling (`RecordShadows`). A like is never moved, so one URI never exists in two places, apart from a client's best-effort double delete.

### 6. Space collections are allowed per key

`Indexer.SPACE_COLLECTIONS` becomes a lookup by space key:
- `followers`, `mutuals` and `self` allow activities and their streams.
- `interactions` allows `feed.like`.

`SpaceSyncer.isSyncable` adds `interactions`. `SpaceSyncer.backfill` lists the allowed collections for the space's key instead of a fixed pair.

### 7. Access

`SpaceAccess` adds `interactions` to the keys the AppView may read. Everyone else gets the existing "every other key admits no one" answer, and writes stay denied.

`AthleteSpaces` creates `interactions` with the AppView as managing app for reads, the same policy as the tiers. It repoints a stale managing app the same way, so the space can't end up readable more widely.

## Risks / Trade-offs

- **Counts cost more on non-public activities.** A correlated follow check runs per like. → It applies only to rows whose visibility isn't Public, on a page of at most `Paging.MAX_LIMIT` activities, and uses the follow indexes `visibleTo` already relies on. Check with `EXPLAIN` in the AppView tests' H2 and on the local Postgres. If needed, count distinct admitted givers in one grouped query per page rather than per activity.
- **Widening reveals likes given under a narrower tier.** → This is the accepted behaviour ("Likes follow the activity both ways"). A like reveals only that the giver liked an activity that everyone can now see.
- **The AppView becomes the only reader of non-public likes.** If it's lost, the likes remain in givers' spaces but nothing shows them. → They can be re-indexed: a new AppView that's admitted to `interactions` spaces rebuilds them by backfill.
- **Stale views put Public likes in a space.** → They're still shown correctly. Only firehose visibility of that like is lost.
- **Re-sign-in.** Non-public likes need the new declaration. → The client already surfaces `ScopeMissingError` as "sign in again". Public likes keep working meanwhile.
- **Spaces alpha churn.** → Nothing new reaches the wire beyond `putRecord`/`deleteRecord` on an existing space type. All Spaces wire types stay in `Space.kt`.

## Migration Plan

1. Land `rename-kudos-to-likes`.
2. Deploy the AppView. The new migration and the `interactions` access, sync and indexing are harmless before any client writes.
3. Publish the updated space declaration (`docker/scripts/publish-lexicons.sh` locally, and the real namespace's authority later).
4. Ship the clients. Athletes sign in again before liking non-public activities.

Rollback: revert the clients so the action is hidden again, then revert the AppView's serve-time rule to `acceptsInteractions`. Likes already in `interactions` spaces stay there, harmless and unread.
