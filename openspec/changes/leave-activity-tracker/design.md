# Design

## Context

See proposal.md for why. What shapes the approach:

- The AppView never writes repos or spaces. Only the athlete's client, the PDS (account deletion) and the segment importer write, so the handoff has to be AppView state plus an importer step.
- The AppView already purges on every path that matters. A record delete reaches `deleteSegment`. A deleted account reaches `deleteActor`, which calls `deleteSegmentsBy` and `deleteDiveSitesBy`. A deleted space reaches `notifySpaceDeleted` and then `Indexer.dropSpace`. Segment removal goes through one function (`deleteSegments` in `SegmentRecordIndex.kt`), and dive sites through its counterpart in `DiveSiteIndex.kt`.
- Removing a segment already re-checks its duplicates (`recheckDuplicatesOf`). A duplicate that is left duplicating nothing becomes live and is queued for matching. Importer segments start `active` (`liveStatus`).
- Efforts, leaderboards, Mayors boards and dive-site visits are all derived from stored segment tracks. Only names (adopted names and their history) are held per segment URI.
- On the client, `AthleteSpaces.ensure` creates a missing tier space on the next write. Sign-out keeps queued writes, which upload when the account signs in again. The outbox drains under `AccountPacing`, shared per DID.
- The Spaces alpha PDS is now pinned to the 2026-11-05 build (atproto `704b9e2`). `com.atproto.space.deleteSpace` was in the 2026-10-29 lexicons (`spaces-alpha-2026-10-29`), a deleted space notifies registered services, and the AppView handles that notification (`spaces-deletion-callout`). Whether the current pin still has `deleteSpace` and accepts `manage=delete` has not been checked; task 1.1 does that first.
- `data-export` has shipped. Exports run in the background, wait on the Account page to be saved, and are deleted on sign-out. `AppCollections` (`core/lexicon`) lists every `app.activitytracker` record collection.
- The app has no in-app delete for segments or dive sites, only for name proposals. So the leave screen is the only place in the app where athletes are told about handoffs.

## Goals / Non-Goals

**Goals:**
- One handoff rule at the AppView's segment and site removal functions, so every delete path is covered without a client call.
- Leaving needs nothing new from the AppView. It sees ordinary deletes.
- Other devices fail closed: they never recreate data after a leave without the athlete choosing to.

**Non-Goals:**
- Remote sign-out of other devices. The OAuth scope gives no handle on their sessions.
- Closing the gap between a delete and the republication. The segment is missing until an operator runs `handoff`.
- Handing off `proposed` segments or sites. Nobody but their creator relies on them.

## Decisions

### 1. Handoffs are staged inside the removal functions
`deleteSegments` and the dive-site equivalent write a handoff row for each `active`, non-importer, not-taken-down subject before deleting it. They run inside the transaction they're already in, so `deleteActor` gets handoffs for free. *Alternative:* a client call (`releaseSegments`) before deleting. Rejected: it misses accounts deleted at the PDS and gives the client a "leaving" mode the AppView has to trust.

### 2. Handoffs have their own table, not candidates
`segment_handoffs` (Flyway migration) has these columns:
- `id`: deterministic from the old URI, a TID-safe hash, so a repeated delete or a crash replays to the same id;
- `kind`: `segment` or `site`;
- `old_uri`, `sport`, `segment_kind`, `line`, or `centre`/`radius`;
- `original_name`, `current_name`;
- `successor_uri`, null while pending;
- `staged_at`.

Name-history rows are re-keyed from the old URI to the handoff, then to the successor, instead of being deleted. *Alternative:* stage as segment candidates in a reserved region. Rejected: candidates count riders, `finishSegmentCandidates` replaces them by region, they have no dive-site form, and they don't carry names or the old URI.

### 3. Successor linking
- When the importer's record keyed by a handoff id is indexed, the AppView sets `successor_uri`, sets the new segment's `current_name` from the handoff, moves its history rows across, and queues matching. Importer segments are `active` already. Importer sites under a handoff id are `active` too, a new rule in the site status code.
- When `recheckDuplicatesOf` makes a duplicate of the removed segment live, that segment becomes the successor. That happens in the same transaction, right after the recheck. Names move to it the same way, and its record name stays its original name.
- When a successor is itself later handed off, handoffs pointing at it are re-pointed to the new handoff, so resolving an old URI is always one hop.
- A record written again under an `old_uri` whose handoff is still pending drops the handoff. The original is back.

### 4. Old URIs resolve on read
`getSegment` and `getDiveSite` look up `segment_handoffs.old_uri` when the URI isn't indexed, and return the successor's view. The view's `uri` is the successor's, so clients learn the new URI from the view they already parse. There's no new view field. *Alternative:* a `successor` field on a `NotFound`-like view. Rejected: every caller would need a second fetch.

### 5. Importer `handoff` command
`importer.getHandoffs` (new lexicon query, importer-only, cursor by id) runs the duplicate check itself. `importer.checkSegmentCandidates` and `importer.checkDiveSites` take staged ids or a file's sites, not a handoff, so they don't fit. For each pending handoff, the AppView applies the segment or site duplicate test against live subjects. A handoff that a live subject duplicates is linked to it (Decision 3) and left out; the rest are listed. `handoff` writes each listed one with `putRecord` under the handoff id, only where no record exists, and is exempt from creation limits like other importer writes. If something live appears between the listing and the write, the importer's copy is indexed as `duplicate`. The AppView then links the handoff to the copy's `duplicateOf` instead of to the copy.

### 6. The leave job
`LeaveJob` lives in `core/data` and is scoped per account, with a `leaving` row in Room that holds the DID and the current step. It won't start while the account's data export runs: the export would read records as they're deleted. An export waiting to be saved is deleted with the account's local data, so the leave screen offers Save first. In order:
1. Write `leaving(did, step = DISCARD)`.
2. Cancel the account's outbox entries, imports and recording.
3. `deleteSpace` for each athlete space key, `self` first, then the tiers and `interactions`. NotFound counts as done.
4. Take the union of `AppCollections.records` and the `app.activitytracker.*` collections `describeRepo` reports, so collections since retired from the app are deleted too. For each, `listRecords` pages, then `applyWrites` deletes in batches of `ApplyWrites.MAX_WRITES`, under the account's `AccountPacing` (429 waits, and the pause shown).
5. Clear the account's Room rows, its export files, the cached space credentials and the session secrets.
6. Delete the `leaving` row and sign out with a "left" login note.

The app shell checks for a `leaving` row before building the `SessionGraph`'s normal UI, and shows the leave progress screen instead. Every step lists before deleting, so a replay is idempotent.

Spaces go before the repo so that other devices are fenced off as early as possible (Decision 7). Public records left in the repo for a few minutes are already public.

### 7. Detecting a leave elsewhere
Room keeps `known_spaces(did, skey)`, written when this device first reads or writes a space successfully. The detection rules:
- `AthleteSpaces.ensure` refuses to create a space that is in `known_spaces`, and raises `AccountLeft` instead. Rejoin clears the rows and lets `ensure` create again.
- At session start, and before each drain, if `self` is known the outbox's drain loop calls `getSpace(self)`. Only once per session is enough, plus again after any write that fails with `SpaceNotFound`.
- `AccountLeft` stops the drain and shows the sign-out-or-rejoin prompt.

*Alternative:* a "left" marker in the AppView. Rejected: it adds state the AppView holds about the athlete, and a device that drains straight to the PDS never asks the AppView.

### 8. Scope
`OAuthClientConfig.DEFAULT_SCOPE` and the hosted client metadata add `&manage=delete`. The Account page's grant check already compares the granted scope with what the app asks for, so older grants show as not current with no new code, and the leave screen reuses that check.

## Risks / Trade-offs

- [The 2026-11-05 pin rejects `manage=delete`, or its `deleteSpace` doesn't notify] → Task 1.1 checks this first on the local stack, before any other work. If it fails, bump the alpha pin in its own change first.
- [The athlete leaves with an export they never saved] → The leave screen offers Save first and says leaving deletes it, and leaving can't start while an export is still running.
- [Gap between delete and republication: links and stars briefly hit `NotFound`] → Stars are kept and shown as unavailable. Operators run `handoff` regularly; it's cheap when nothing is pending.
- [Deleting a mistaken active segment no longer removes it] → A `!takedown` label still removes it with no handoff, and that is documented in the proposal as **BREAKING**.
- [A device offline for weeks drains public repo writes before it ever reaches `getSpace`] → The check runs before the first drain of every session, not after one, so no write goes out before it.
- [Successor chains or cycles] → Re-pointing keeps every handoff one hop from a live subject. An old URI that is a live record again drops its handoff.
- [Large repos make leaving slow] → Batched deletes under `AccountPacing`, with visible progress, and resumable.
- [A former leaver's identity stays visible through carried name history] → The history's proposer comes from other athletes' proposals. The leaver's own proposals are deleted with their repo, so their name drops off as existing rules already say.

## Migration Plan

1. Ship the AppView migration and handoff code. It's harmless before any client leaves, since ordinary deletes start staging handoffs at once.
2. Ship the importer `handoff` command, and add it to `scripts/segment-import.sh` as an optional step.
3. Ship clients with the new scope. Existing sessions show "sign in again" only on the Account page and Leave. Everything else keeps working.

Rollback: clients without the leave feature are unaffected by the table. Dropping the handoff code leaves pending rows inert.

## Open Questions

- The `handoff` command's schedule in production: cron versus manual. This doesn't affect the specs.
