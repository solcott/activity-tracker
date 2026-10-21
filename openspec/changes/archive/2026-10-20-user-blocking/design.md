# Design

## Context

Every tier decision in the AppView already goes through one place. `visibleTo` / `admits` in `api/TierAccess.kt` builds the SQL predicate, and `effectiveFollow()` in `api/ActorDetails.kt` defines which follow rows count. Callers include:

- The timeline, author feeds, single activity, streams, zones and details (`Views.kt`, `ActivityStreamsViews.kt`, `DetailsViews.kt`).
- Likes (`visibleTo(Likes.authorDid)`).
- Comments (`commentsVisibleTo` in `CommentQueries.kt`, which already requires that the commenter may see the activity).
- Private media (`BlobRedirects.kt`).

`SpaceAccess.canRead` answers the PDS for tier spaces through `follows` / `isMutual`. The interactions space is synced like a tier space (`SpaceSyncer`), and only the athlete and the AppView read it. Follows are written directly to the PDS, and `ProfileRepository` overlays them with `pendingFollows` until the AppView catches up. Writes to the interactions space go through `SessionClients.requireSpacesFor` / `canWriteToSpaces`, which check that the `space:` scope covers the collection. The scope's collections come from `space/athlete.json`, which the PDS resolves at sign-in.

## Goals / Non-Goals

**Goals:**
- Enforce blocks mostly by changing the two shared predicates, not each query.
- Treat a block and a Bluesky block as one relation, so enforcement never cares where a block came from.
- Keep both directions of a block, and every mute, invisible to everyone but the athlete who made it.

**Non-Goals:**
- Rejecting records at index time. Hiding happens when reading, so ordering (a block indexed after a comment) doesn't matter and an unblock restores everything.
- Client-side filtering beyond the optimistic overlay. The AppView is the source of truth.

## Decisions

### One `blocks` table, keyed by source
Columns: `author_did`, `subject_did`, `source` (`space` | `bsky`), `uri`, `created_at`. The primary key is (`author_did`, `subject_did`, `source`). An athlete can hold both kinds for one subject, and deleting one keeps the other. Enforcement asks only whether any row exists for the pair in either direction. Indexes on (`author_did`, `subject_did`) and (`subject_did`, `author_did`) keep that lookup cheap. A separate `mutes` table has (`author_did`, `subject_did`) as its primary key.
- *Alternative:* one row per pair, merging sources. Rejected because deleting one source would need reference counting.

### Enforcement in the shared predicates
- `admits(viewer, visibility, author)` becomes `(existing) and not blocked(viewer, author)`. This also covers the public branch, which today returns true before any lookup. A null viewer is unchanged.
- `effectiveFollow()` adds `not blocked(Follows.authorDid, Follows.subjectDid)`. Tier spaces, mutuals, the timeline's authors, counts, `viewerState.followedBy` and follow lists all follow from it.
- `visibleTo(Likes.authorDid)` and `commentsVisibleTo` already require the giver to see the activity. Through `admits`, that removes interactions between the activity's athlete and someone they block, for everyone.
- A third-party activity needs one more clause: the viewer against the giver. Add `not blocked(viewer, Likes.authorDid)` and `not blocked(viewer, Comments.authorDid)`, plus `not muted(viewer, Comments.authorDid)` for comments. Put them in `commentsVisibleTo` and in the like queries, so threads, placeholders and counts stay consistent (the existing `The count matches the pages` rule).
- `SpaceAccess.canRead` checks for a block before the tier `when`, which covers `public` as well.
- Search (`ActorSearch`), `getFollowers` / `getFollows` / `getFollowRequests` (`GraphViews.kt`) and `getProfile` each add a block filter on the listed or requested DID. `getProfile` throws `notFound` when the athlete blocks the viewer. When only the viewer blocks the athlete, it returns the profile with `viewer.blocking`, and author feeds return nothing.
- Mutes add `not muted(viewer, Activities.authorDid)` to the timeline only.

### Bluesky blocks: Jetstream plus an athlete filter plus backfill
- Add `app.bsky.graph.block` to `Nsid.indexedCollections`. `JetstreamConsumer` builds `wantedCollections` from that list.
- An athlete is an account with an `actors` row. The indexer creates that row (`ensureActor`) only for authors of `app.activitytracker.*` records, in repos or spaces, so it marks everyone who uses the app. A Bluesky block is handled before `ensureActor` and kept only when its author already has a row, so a non-athlete never gains one. A delete is applied by `uri`, which is a no-op for dropped ones.
- `actors.bsky_blocks_synced_at` (nullable) drives the backfill. A background `BskyBlockBackfill` periodically picks actors where it's null, which covers new athletes and, after the migration, everyone already indexed. It pages `com.atproto.repo.listRecords` for `app.bsky.graph.block` on the athlete's PDS (`PdsLookup`), upserts each record and stamps the column. A failure leaves the column null and backs that actor off in memory before retrying. Later Jetstream events keep the table current.
- The app never writes `app.bsky.*`, so a Bluesky-only block is read-only in Settings.
- *Alternative:* an opt-in toggle for honoring Bluesky blocks. Rejected for now because a block is a safety signal and the user chose to honor it. A toggle can come later without changing the data.

### Records keyed by subject DID
`graph.block` and `graph.mute` use the subject DID as rkey, as `followApproval` and `followDecline` already do. This makes block and mute idempotent and lets unblock and unmute delete without a lookup. The syncer ignores any record whose rkey isn't its `subject`, like the hide rkey rule.

### Silent by construction
The blocked side gets the same `notFound` and empty results as for missing data, because the filters sit in the same predicates that hide non-public data. `viewerState` gains `blocking` and `muting` only. A `blockedBy` field is never added.

### Client
- A `ModerationGraphRepository` in `core/data/.../profile` (the name is open; it sits next to `ProfileRepository`) writes and deletes the records in the interactions space through the existing space writer, after `requireSpacesFor(graph.block | graph.mute)`.
- Its `pendingBlocks` / `pendingMutes` overlay works like `pendingFollows`. Presenters apply it to profiles, timelines and comment pages, so a block shows at once and survives a reload until the AppView has indexed it.
- `getBlocks` / `getMutes` return `profileView` entries plus `source` (`app` | `bluesky`) for blocks, newest first, with a cursor.
- New screens `BlockedAccountsScreen` and `MutedAccountsScreen` are reached from `SettingsScreen`. Profile and comment menus gain the actions through the `ui/adaptive` wrappers.

## Risks / Trade-offs

- [Jetstream delivers every Bluesky block on the network] → Drop by an in-memory set of athlete DIDs, refreshed as profiles are indexed, before touching the DB. Measure the rate on the local relay and in staging.
- [`not exists` on `blocks` in every visibility predicate costs query time] → Use the two composite indexes and keep the subquery correlated on indexed columns. Check `EXPLAIN` on the timeline and comment queries with realistic data. This matches the `pg_trgm` check in BACKLOG.md. Checked on the local Postgres (with sequential scans off, since the local tables are tiny): the timeline and comment queries run the block term as a nested-loop anti join over a `BitmapOr` of two `blocks_subject_idx` scans, and the mute term as an index-only scan of `mutes_pair_idx`; nothing scans `blocks` sequentially. `blocks_pair_idx` serves the author-first lookups (`ownBlock`, `getBlocks`). Re-check with realistic row counts before a shared deploy.
- [Public records remain readable from the PDS and from any other AppView] → State this in the block confirmation (spec). It's the same limit Bluesky has.
- [Sessions from before this change lack the scope] → Block and mute refuse and ask the athlete to sign in again, as hides do. Bluesky blocks still apply without the scope.
- [Backfill can miss blocks deleted during the backfill] → A Jetstream delete arriving later still removes the row by `uri`, and upserts are idempotent.

## Migration Plan

1. Flyway `V26__blocks_and_mutes.sql` creates `blocks` and `mutes` with their indexes, in H2-portable SQL.
2. On deploy, existing actors have a null `bsky_blocks_synced_at`, so the backfill covers them with no separate job.
3. Rollback: the old AppView ignores the new tables and records. The new lexicon collections stay declared and harmless.

## Open Questions

- Whether `getProfile` for a blocked pair should still serve the avatar URL route (`/img/avatar`). Today it serves only the current avatar, which is public anyway. This can be settled during implementation without changing the specs.
