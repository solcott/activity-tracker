# Proposal

## Why

When an athlete deletes one of their spaces, the AppView only finds out the next time it syncs that space and gets `SpaceDeleted`, which can be up to the 15-minute reconcile. Until then it keeps serving private data the athlete deleted. The Oct 22 alpha made deletion reliable: the authority calls `com.atproto.space.notifySpaceDeleted` on each registered syncer, retrying until it's delivered or the registration expires (guide §6). Handling it purges at once.

Optional follow-up to `spaces-alpha-2026-10-22`.

## What Changes

- The AppView serves `com.atproto.space.notifySpaceDeleted` (input `{space}`) next to `notifyWrite`. It's authenticated the same way: service auth from the space's authority, addressed to the syncer service id.
- On a verified call for a synced space, the AppView stops syncing it and drops everything indexed from it in the same way as a sync that sees `SpaceDeleted`: activities, details, streams, zones, gear, segment tracks and efforts, likes, comments, hides, blocks and mutes, as `Indexer.dropSpace` does today. An unknown or already-dropped space succeeds with nothing to do, so retries are harmless.
- A space recreated at the same URI is treated as new: the athlete's next announcement joins, registers and backfills it from scratch. That's today's behavior after a drop, now covered by a test.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `spaces/appview-syncer`: the AppView accepts deletion notifications and purges a deleted space at once.

## Impact

- **`core/lexicon`:** a `Space.NotifySpaceDeleted` descriptor.
- **AppView:** `api/Routes.kt` (`spaceSyncRoutes`), `SpaceSyncer` (a public `deleted(space)` sharing `dropped()`'s work, taking the space's lock), `SpaceSyncTest`.
- **Docs:** the `.agents/rules/appview.md` syncer paragraph.
