# Proposal

## Why

Athletes can't stop someone from seeing their activities, liking them or commenting on them, and they can't quiet someone without unfollowing. That is the first safety tool a social app needs. It also comes before content moderation (reports and labels), which is a separate change. Bluesky already has a blocking model and many athletes already hold Bluesky blocks in the same repo, so we can match its behavior and honor those blocks. We can also do better than Bluesky on privacy: the AppView is the only enforcer and already reads a private per-athlete space, so our own blocks don't need to be public.

## What Changes

- New record `app.activitytracker.graph.block`. Its `subject` is the blocked DID and its rkey is that DID. It is stored only in the athlete's `interactions` space, never in the repo, so only the athlete and the AppView know about it.
- New record `app.activitytracker.graph.mute` with the same shape and storage. A mute quiets an account for the muter only.
- `app.activitytracker.space.athlete` declares both collections. The `space:` scope therefore covers them, and sessions from before this change are asked to sign in again before blocking or muting.
- The AppView syncs blocks and mutes from `interactions` spaces. It also honors public `app.bsky.graph.block` records:
  - It adds them to the Jetstream collections it indexes.
  - It drops those whose author isn't an athlete.
  - It backfills an athlete's existing Bluesky blocks the first time it indexes their profile.
- Blocks are two-way and silent:
  - While either athlete blocks the other, follows between them don't count. Neither one is admitted to the other's tier spaces, `public` included.
  - Neither sees the other's activities, profile, likes or comments, or finds the other in search or follow lists.
  - The blocked athlete gets "not found", never "blocked".
  - Existing likes and comments between them drop out for everyone through the existing rule that the giver must still be able to see the activity.
- Mutes are one-way. The muter's timeline leaves out the muted account's activities, and the muter isn't shown that account's comments and replies. Nothing changes for the muted account or anyone else, and follows still count.
- `getProfile` gains `viewer.blocking` and `viewer.muting` for the viewer's own block and mute of the athlete. It never says whether the athlete blocks the viewer.
- New authenticated queries `app.activitytracker.graph.getBlocks` and `graph.getMutes` list the caller's own blocks and mutes. Bluesky blocks are listed and marked as such.
- Clients:
  - Block, Unblock, Mute and Unmute on a profile, and Block and Mute on a comment's author.
  - The UI changes at once, without waiting for the AppView, as follows do today.
  - A Blocked accounts list and a Muted accounts list in Settings.
  - Bluesky blocks show read-only, because the app never writes `app.bsky.*` records.
- Not covered: reports, labels and takedowns (`content-moderation`), Bluesky mutes and mute or block lists (`app.bsky.graph.listblock`), and stopping anyone from reading public repo records straight from a PDS. That last one isn't possible on atproto.

## Capabilities

### New Capabilities
- `graph/blocks`: How athletes block and unblock accounts, where blocks are stored, how Bluesky blocks are honored, and what a block hides in both directions across the AppView and clients.
- `graph/mutes`: How athletes mute and unmute accounts, where mutes are stored, and what a mute hides from the muter.

### Modified Capabilities
- `graph/follow-requests`: Effective follows. A follow is not effective while either athlete blocks the other.
- `spaces/managing-app`: Read access by tier. A user in a block with the authority is denied every tier, `public` included.
- `spaces/appview-syncer`: Only activity data is indexed from spaces. `interactions` spaces also yield `graph.block` and `graph.mute`.

## Impact

- Lexicons and the Kotlin mirror:
  - New `graph/block.json`, `graph/mute.json`, `graph/getBlocks.json` and `graph/getMutes.json`.
  - `space/athlete.json` collections, and `actor/defs.json#viewerState` (`blocking`, `muting`).
  - `Nsid` (including `app.bsky.graph.block` in `indexedCollections`) and `Records.kt`.
- AppView:
  - Blocks and mutes tables, with a Flyway migration.
  - `Indexer` and `JetstreamConsumer` for `app.bsky.graph.block`, with an athlete filter and a profile-first backfill through `com.atproto.repo.listRecords`.
  - `SpaceSyncer` for the interactions space.
  - `TierAccess.visibleTo` and `effectiveFollow()`, `SpaceAccess.canRead`, and the queries in `Views.kt`, `CommentQueries.kt`, `GraphViews.kt` and `ActorSearch.kt`.
  - New routes.
- Client:
  - `core/data`: a block and mute repository beside `ProfileRepository`, with a pending overlay like `pendingFollows`, and `SessionClients.requireSpacesFor`.
  - `:app-screens`, `:app-presenters` and `composeApp`: profile, comments, Settings, and new Blocked and Muted screens.
- Jetstream volume: every Bluesky block on the network arrives and most are dropped. This is a modest, steady stream.
