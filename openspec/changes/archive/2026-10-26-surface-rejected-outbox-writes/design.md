# Design

## Context

`OutboxWriter` queues each write as an `outbox` row (`OutboxEntity`: did, operation, collection, rkey, record JSON, an inline blob or a shared `blobKey`, space, `attempts`, `lastError`). `OutboxProcessor.drain(only)` takes the account's rows in id order, filtered by `only`, applies each and deletes it, and on any failure calls `markFailed` and returns the number left. The one failure it drops is a `feed.activitySource` whose blob the PDS refuses as too large. Four callers drain a subset (`RouteTrimmer` activities, `ActivityZonesStore` zones, `SegmentTrackSweeper` segment tracks) or everything (`ActivityRepository.sync()`, run by the feed on first load and on refresh), and some read the returned count. `ActivityRepository.pendingWrites` counts every row, and the feed shows it as "Uploading N changes…".

The app shows its own writes before the AppView has them through session-scoped, in-memory overlays: `LocalRewrites` (rewritten activity records), `LocalDeletes`, `LocalLikes` and `LocalComments`, plus `FeedRepository.updateCached` on the Room feed cache. Several readers also treat a queued `feed.activity` put as an existing activity (`OutboxEntity.pendingActivity`, the track, source and devices stores).

An activity's other records (details, streams, track, original file, devices, zones, segment track) use the activity's rkey. Likes, comments, hides, segments and stars get their own TIDs. Blocks and mutes are keyed by the subject's DID.

## Goals / Non-Goals

**Goals:**
- A write that can't succeed no longer blocks unrelated writes, and the athlete always has a way out that keeps their other writes.
- Erring is safe: a transient failure taken for a rejection only shows the athlete an item they can retry, while a rejection taken for a transient failure would block the queue again. So the classification leans towards rejecting.
- Room stays the single source of truth for what's queued and what's rejected, across restarts.

**Non-Goals:**
- Telling the athlete *why* the PDS refused a write. `ui/error-messages` forbids raw text, and the PDS's error names are too generic to map to anything the athlete can act on.
- Editing a rejected write before retrying it.
- Undoing every overlay on discard (see Risks).

## Decisions

**Transient is the short list, and everything else is a rejection.** A failure is transient when it is a network failure (`IOException`, Ktor timeouts, a rejected web `fetch`, the same test `Throwable.toDataError` uses for `Network`), an `XrpcException` with status 5xx, 401, 408 or 429, an `OAuthException` (an expired session or a refresh that failed, which are about the session and not the write), or a `CancellationException` (which propagates, as now). Every other failure, including any other 4xx and a non-network exception such as a missing shared blob or a stored record that no longer parses, rejects the write. The too-large original keeps its existing drop, checked first. *Alternative:* reject only a list of known XRPC errors (`InvalidRecord`, `InvalidRequest`…). Rejected: an unknown permanent error would block the queue again, which is the bug.

**A nullable `rejectedAt` column marks a rejected row.** Room 5 → 6 adds `rejectedAt INTEGER` (epoch millis) to `outbox` with a hand-written `ALTER TABLE` migration in `addAppMigrations`, and the regenerated schema JSON is committed. `lastError` keeps the failure text for the log only. Rows queued before the upgrade have no mark, so a write already stuck is sent once more and then rejected, which unblocks every athlete who hit the bug. *Alternative:* a separate `outbox_rejected` table. Rejected: retry and discard would move rows between tables, and the ordering by id would need care.

**The group is the account's rows with the rejected row's rkey.** It matches how an activity's records are keyed, without listing collections. A block and a mute of the same athlete share an rkey and so hold each other back. That is acceptable: both are about the same athlete, and the item shows both.

**Drain skips what's held.** `drain` first collects the rkeys of the account's rejected rows (from all rows, not only those `only` accepts, so a filtered drain never sends a write held behind a rejected write it didn't select). It then walks the selected rows in order: a rejected row or one whose rkey is held is skipped; a successful write is deleted; a rejection sets `rejectedAt`, adds the rkey to the held set and goes on; a transient failure calls `markFailed` and returns. The returned count keeps its meaning: the selected writes not applied, rejected and held ones included. Callers rely on zero meaning "all sent". `RouteTrimmer` marks the route repair done only then, so a refused re-trim must not count as sent, or an athlete's old route would stay published with the repair marked done. *Alternative:* count only writes still to be retried. Rejected for that reason. `ActivityZonesStore.drainOrThrow` then throws `PendingWritesException` while a zones write is rejected, which is right: the zones aren't saved.

**Counts and items come from Room flows.** `OutboxDao` gains a `pendingCount(did)` flow (rows that aren't rejected and whose rkey isn't held) for the existing "Uploading" row, and a `rejected(did)` flow of the rejected rows. A new `RejectedChanges` class in `core/data` (session-scoped, beside `OutboxProcessor`) turns those into items, one per rejected rkey. The item's kind comes from the rejected row's collection, and an activity's name from its group's `feed.activity` put record when there is one. It also exposes `retry(item)` and `discard(item)`. `ActivityRepository.pendingWrites` switches to the pending count.

**Retry clears the mark and drains.** `retry` sets `rejectedAt` to null for the group's rejected row and runs a full `drain()`, so the write goes out at its original place in the queue, behind anything older still pending. If it's rejected again it's marked again.

**Discard deletes the group, then shows what the PDS holds.** `discard` deletes every row of the group and unreferenced shared blobs in one transaction. It then clears what this session laid over the AppView for the record: for an activity, its `LocalRewrites` and `LocalDeletes` entries; for a like, the `LocalLikes` entry pointing at it; for a comment, its `LocalComments` entry. It also drops the activity's cached feed items and refreshes the feeds that held them, so a never-published activity disappears and a refused edit shows the published version. The overlay classes gain the small `forget` methods this needs.

**One new screen, reached from the feed.** An `UnsentChangesScreen` (`:app-screens`, presenter in `:app-presenters`, UI in `composeApp`, samples in `:app-fixtures`, built with the `new-screen` skill) lists the items, with Retry and Discard on each. Discard asks for confirmation, since it can't be undone. `FeedState` gains `rejectedChanges: Int`, and the feed shows a tappable row "N changes couldn't be published" that navigates there, beside the existing "Uploading N changes…" row. The screen shows the activity's name, or its kind and date when it has none, for activities, and a kind label for anything else ("A like", "A comment", "A block"…), with "A change" for an unknown collection.

## Risks / Trade-offs

- [A transient error outside the short list, such as a 4xx a proxy answers while it's down, rejects a write] → The athlete sees it and retries; nothing is lost.
- [A space write fails in `tiers.ensure` with an AppView 4xx and is rejected, though the record itself was fine] → Retry covers it once the AppView is fixed. Splitting the PDS's answers from the AppView's isn't worth it for this change.
- [Discarding an unlike, uncomment or unhide can't find the overlay entry, which is keyed by the activity and not stored in a delete row] → The overlay lasts until the session ends, and these are rare. The athlete's next sign-in or app start shows the PDS's state.
- [A writer that reads queued `feed.activity` puts as existing activities (`pendingActivity`, imports' duplicate check) sees a rejected one as existing] → Intended until it's discarded: the athlete still has it on this device and can retry it.

## Migration Plan

Ships with the app. The Room migration adds a nullable column, so it is safe on every platform, wasm included. Rows already stuck behind a permanent error are rejected on the first drain after the upgrade and show in the new list. Rolling back to a build without the column fails Room's schema check, as every downgrade does.
