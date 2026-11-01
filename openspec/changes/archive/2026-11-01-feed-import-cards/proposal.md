# Proposal

## Why

The timeline sorts activities by when they were posted (`sort_at = min(createdAt, now)`). Now that batch imports exist, an athlete who imports years of history puts hundreds of old activities at the top of every follower's feed, drowning everyone else out. Within a batch, combined groups are saved before the activities that stand alone, so the batch doesn't even appear in the order the activities happened.

## What Changes

- The timeline sorts each activity at the earlier of when it was posted and 5 days after it ended. An activity posted within 5 days of ending (a live recording, a late watch sync) still goes to the top. Older ones sink to when they happened, in the order they happened, whatever order they were saved in. Existing activities are re-sorted.
- An athlete's activities (`feed.getAuthorActivities`, the profile) are listed by when they started, newest first, never by when they were posted.
- `feed.activity` gains an optional `importBatch` field: an identifier shared by every activity saved from one batch import form.
- On an unfiltered timeline, a batch's activities that ended more than 5 days before they were posted collapse into one import card when the viewer may see at least 5 of them. The card sits at the batch's latest post time and shows the athlete, how many activities, their date range, a count per sport, the total distance and a few route maps. Recent activities in the batch still show on their own. Batches the viewer sees fewer than 5 older activities of show those activities on their own.
- Tapping an import card opens a new screen listing the batch's activities the viewer may see, newest first, grouped by month. Each opens the activity page.
- **BREAKING** `feed.getTimeline`'s output gains `feed`, an array of a union of activity items and import cards, which replaces `activities`. Clients older than this change can't show the timeline's import cards.
- New query `feed.getImportBatch(actor, batch, limit, cursor)`.

## Capabilities

### New Capabilities

- `feed/timeline-order`: how the timeline and an athlete's activities are ordered, and the 5-day grace that keeps old activities out of the top of the feed.
- `feed/import-cards`: collapsing a batch import's older activities into one timeline card per viewer, and the batch screen it opens.

### Modified Capabilities

- `feed/batch-import`: saving a batch tags every activity it saves with the batch's `importBatch`.
- `feed/activity-query-filters`: results are ordered as `feed/timeline-order` says, not newest-posted first, and filtered timelines never collapse into import cards.

## Impact

- **Lexicons:** `feed.activity` (`importBatch`), `feed.defs` (`importCardView`, the timeline item union), `feed.getTimeline` (output), new `feed.getImportBatch`. These are mirrored in `core/lexicon` along with `Nsid`.
- **AppView:** the `sort_at` formula in `ActivityIndex`, a Flyway migration that adds `ended_at` and `import_batch` and recomputes `sort_at`, `Views.timeline` (it merges activities and cards and pages them together), `authorActivities` (ordered by start), a new `importBatch` view and route.
- **Client:** `BatchImport.saveBatch` and `ActivityRepository.publishBatch` / `publishAll` tag drafts. `FeedRepository` and the Room `feed_item` cache store cards (a Room migration). The feed UI renders a card. A new screen under `feature/feed` opens the batch. Sample states go in `:app-fixtures`, with screenshot references.
- **Compatibility:** this assumes no released client depends on `getTimeline`'s `activities` (the repo shows no store releases). The output change then ships with the client change and drops `activities`. If an old client must keep working, the design keeps `activities` alongside `feed` (see design.md).
- **Out of scope:** collapsing on profiles, likes or comments on a card, notifications about imports, and changing the grace or threshold per athlete.
