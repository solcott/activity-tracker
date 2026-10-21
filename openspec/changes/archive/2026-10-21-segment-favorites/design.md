# Design

## Context

See proposal.md for why. What already exists:

- `SegmentRepository` reads segments and efforts from the AppView only. `SegmentPrefsRepository` is the pattern for reading the `self` space directly: `clients.spaces.getRecord`/`listRecords` returning `SpaceRead.Found`/`NotFound`/`NoAccess`, and `clients.canWriteToSpaces(Nsid…)` for sessions whose `space:` grant predates a collection.
- `BlockRepository` is the pattern for a private toggle record. It has a deterministic rkey, is written through `OutboxWriter.put`/`delete` with a `space`, shows at once from a local override map, and then calls `outbox.drain()`. `LocalLikes` shows the same overlay for views.
- `commentHideRkey` (`core/atproto/.../RecordKeys.kt`) already derives an rkey from an AT-URI: the first 20 bytes of its SHA-256, as lowercase unpadded base32.
- The AppView's space indexer keeps only an allowlist from `self` (`Indexer.kt`, `SEGMENT_COLLECTIONS` plus the tier collections), so anything else in `self` is already dropped.

## Goals / Non-Goals

**Goals:**
- Stars that survive offline use and app restarts, with one per segment across devices.
- No AppView code or schema change.

**Non-Goals:**
- Sorting or filtering the starred list beyond newest first; search within it.
- Showing stars on maps or in segment search results (no segment browse screen exists yet).
- Syncing a cached copy of stars into Room. The list reads `self` each time it opens; the segment screen reads one record.

## Decisions

### Record: `feed.segmentStar`, keyed by a hash of the segment URI
`{ subject: com.atproto.repo.strongRef, createdAt }`, key `any`. The rkey is `segmentStarRkey(uri)`, which works the same way as `commentHideRkey`. Both can share a private helper in `RecordKeys.kt`.
- *Why not a TID?* Two devices starring the same segment would write two records, and unstarring would need a list-and-search to find them.
- *Why not `<did>:<rkey>` as the key?* `did:web` identifiers can contain `%`, which isn't a valid rkey character. A hash always fits, and the subject in the record still says which segment it is.
- *strongRef vs a bare URI:* strongRef matches `feed.like` and records which version was starred. The list resolves by `subject.uri`, so a segment edited after starring still resolves.

### Client store: `SegmentStars` in `core/data/.../segments/`
SessionScope, alongside `SegmentRepository`.
- `canUse`: `clients.canWriteToSpaces(Nsid.FEED_SEGMENT_STAR)`.
- `isStarred(uri): Boolean?` does a `getRecord` on `self` by `segmentStarRkey`. It returns null when the record can't be read. A local override wins over the read.
- `starred(cursor)`: one page of `listRecords` on `self`, decoded with `AtJson`. Records that don't decode are skipped. Local overrides are laid over: starred-but-unsent entries are added and unstarred ones removed. The list is sorted by `createdAt`, newest first.
- `setStarred(segment: StrongRef, on)`: a local override first, then `OutboxWriter.put`/`delete` to `clients.tiers.ensure(AthleteSpace.SELF)` with the derived rkey, then `outbox.drain()`. Because the key is deterministic, re-queued or repeated ops collapse to the last write.

### Outbox, not a direct call
Consent (`segmentPrefs`) and privacy zones save directly because applying them silently later would be the wrong failure. A star is reversible and harmless if delayed, so it takes the same path as blocks: optimistic and queued.

### Screens
- `SegmentState` gains `starred: Boolean?` (null = unknown or unavailable) and `canStar`. `SegmentEvent` gains `ToggleStar`. The presenter loads the star in its own `LaunchedEffect`, the way efforts are loaded, so a failed read never blocks the segment.
- A new `StarredSegmentsScreen` / `StarredSegmentsState` / `StarredSegmentsEvent`, built with the `new-screen` skill. The presenter pages stars from `SegmentStars.starred` and resolves each with `SegmentRepository.segment`, at most 4 at a time. A `NotFound` becomes an unavailable row with Unstar; any other failure leaves the row retryable. The screen states are: loading, loaded, empty, a load failure with retry, and "sign in again" when `canUse` is false.
- `ProfileEvent.OpenStarredSegments`: the row shows only on the athlete's own profile.

### AppView
No code. A test in the space-sync suite feeds a `self` op for `feed.segmentStar` and asserts that no table gains a row. This pins the "never stores" requirement against a future allowlist change.

## Risks / Trade-offs

- [A large star list means one `getSegment` per entry] → page the list (30 per page) and limit concurrency. A batch `getSegments` query can come later if lists grow.
- [Sessions signed in before the declaration change can't write stars] → hide the control and say to sign in again, as segment matching does. Reads still work for the owner, so existing stars still list.
- [The AppView receives star ops while syncing `self`] → accepted with the user. The guarantee is "never stored or served", spelled out in the space declaration's description and pinned by the test.
- [An override can disagree with the PDS when a queued op fails permanently] → the outbox's existing failure handling applies. The override lasts for the session only and is dropped on the next fresh read.

## Migration Plan

Additive. Deploy the lexicon and declaration change first; the PDS fills new `space:` grants with the new collection at the next sign-in. There is no AppView migration. Rolling back the client leaves harmless records in `self`.
