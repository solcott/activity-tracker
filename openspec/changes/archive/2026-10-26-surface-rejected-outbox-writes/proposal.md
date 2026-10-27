# Proposal

## Why

The outbox applies queued writes in order and stops at the first one that fails, whatever the reason. A write the athlete's PDS will never accept, such as a record it refuses as invalid (`400 InvalidRecord`), therefore fails on every drain. Every write queued after it waits behind it for good, and nothing in the app says so: the feed shows "Uploading N changes…" forever, and the only way out is clearing the app's data, which also loses the writes that were fine. This was C4 in the 2026-10-23 codebase review, and with C3 and P7 fixed it is the remaining finding that hurts athletes most.

## What Changes

- **The outbox tells a rejected write from a failed one.** A write the PDS refuses with a client error (4xx) is *rejected*, except for 401 (an expired session, which the session already handles), 408 and 429 (try again later). A rejected write is marked as such and kept, not retried on every drain. Network errors, 5xx answers and those three statuses still stop the drain and are retried, as now.
- **A rejected write blocks only its own record's writes.** Later writes with the same rkey (an activity's details, streams, track, original file, devices, zones and segment track all share its rkey) wait behind it. Every other write goes on.
- **The athlete sees what couldn't be published.** The feed shows how many changes couldn't be published, apart from those still uploading, and links to a new screen that lists them. Each item says what it is (the activity by its name, or the kind of change, such as a like or a comment) and that the server refused it, without raw error text.
- **Retry or discard each item.** Retry sends the rejected write and the writes held behind it again. Discard removes them from the device, and the app then shows what the PDS actually holds: an activity that was never published disappears, and an edit that was refused falls back to the last published version.
- Unchanged: an original file the PDS refuses as too large is still dropped and logged, and the activity just offers no download (`feed/activity-source-file`).

Out of scope: retrying a write that keeps failing with a transient error, which still waits behind its retries, and a size or age limit on the outbox.

## Capabilities

### New Capabilities
- `data/outbox`: How the client applies the athlete's queued writes to their PDS: in order, which failures are retried and which are rejected, what a rejected write holds back, and how the athlete sees, retries and discards rejected writes.

### Modified Capabilities
None. `feed/activity-source-file` already covers the too-large original, and this change keeps that behavior.

## Impact

- **core/data**: `OutboxEntity` gains a rejection marker, so a Room migration (5 → 6) and the regenerated schema JSON. `OutboxProcessor.drain` classifies failures, marks rejected writes and skips the writes they hold back. `OutboxDao` gains queries for pending and rejected counts and for a group's rows. `ActivityRepository` (or a new `OutboxRepository`) exposes the rejected items, retry and discard, and discard clears the local overlays (`LocalRewrites`, `LocalLikes`, `LocalComments`, `LocalDeletes`) and the cached feed item for the discarded record.
- **app/screens, app/presenters, composeApp, app/fixtures**: a new screen listing rejected changes, with its presenter, UI, sample states and tests. `FeedState` splits pending from rejected writes, and the feed shows a tappable "couldn't publish" row. New strings.
- **androidApp**: new screenshot references for the new screen and the feed's new row.
- No lexicon, AppView or PDS changes.
