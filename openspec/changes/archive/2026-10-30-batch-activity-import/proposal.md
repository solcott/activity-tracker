# Proposal

## Why

Import takes one picked file. Only a dive logbook (UDDF, Subsurface) offers a batch to pick from. Athletes moving to the app bring years of history: a folder of watch files, or a whole Strava or Garmin Connect account export (one zip holding thousands of activities). Today they would import those one file at a time, and a zip doesn't import at all. The batch form, in-file duplicate handling and once-per-file originals already exist for logbooks. This change makes them the import screen's general path for every format and for many files at once.

## What Changes

- **Picking:**
  - The import screen accepts several files in one pick.
  - Each file may be an activity file, a gzipped one, or a zip (including zips nested in zips) of any of them.
  - Members that aren't activity files are skipped and counted. Files that can't be read are listed with the reason.
- **Account exports:**
  - A Strava export (`activities.csv` + `activities/`) and a Garmin Connect export (`DI_CONNECT/…`) are recognized.
  - Each activity's title, description and type come from the export's metadata.
  - Its visibility comes from the export's privacy, never wider than the batch's.
- **Scale:**
  - Archives are read without loading them whole.
  - A first pass keeps only a summary of each activity.
  - Full activity data is read again only to preview one, and at save, in chunks.
  - A batch of thousands of activities stays within memory on phones and the web.
- **Batch review:**
  - Any import of two or more activities, from one file or many, shows the batch form.
  - It is no longer dive-only:
    - one row per activity (type, start, duration, distance, or depth for a dive), grouped by month
    - filters for all, new and duplicates
    - select all or none
    - a per-row checkbox to decline
  - Tapping a row opens a read-only preview (map, stats, charts) where only the title and type can be changed.
  - Visibility, detail categories and gear stay batch-wide.
  - Each file whose times have no zone gets its own zone choice.
- **Duplicates across the batch:**
  - Every activity is checked against earlier ones in the whole batch (not just its own file) and against the athlete's log over the batch's whole span.
  - The log search runs month by month so a span of years is covered.
  - Ignore, Import anyway and Combine work as they do for a logbook today, so a watch file and a bike-computer file picked together combine.
- **Originals:**
  - Each picked file, and each activity file inside a zip, is kept once as its own original, shared by the activities read from it.
  - A zip is never stored whole.
- **Single activities are unchanged:** a single picked activity still opens the existing preview form.

## Capabilities

### New Capabilities
- `feed/batch-import`: importing many activities at once. Covers picking several files, zip and gzip archives, recognizing Strava and Garmin Connect exports and applying their metadata, reviewing and declining items in the batch form, the per-item preview with title and type overrides, and saving the batch.

### Modified Capabilities
- `feed/import-duplicates`:
  - "Duplicates in one file" widens to the whole batch.
  - The log search must cover a batch spanning years.
  - Combine within a file becomes combine within a batch.
- `feed/activity-source-file`: "A logbook file is stored once for all its dives" generalizes. Each picked file or archive member is stored once for all activities read from it, archives aren't stored whole, and the 10 MB limit applies per stored file.
- `feed/activity-visibility`: a batch's visibility applies to every activity in it, except where an export's own privacy is narrower.
- `feed/activity-page-layout`: the batch form replaces the preview for any import of several activities, not only a dive logbook.

## Impact

- **`core/activityfile`:**
  - a random-access `ImportSource`
  - a common-code zip reader
  - a streaming raw-deflate inflater in common code
  - export metadata readers for Strava CSV and Garmin JSON
  - a summary-only scan result
- **`feature/record` (api, presenter, ui):**
  - `DiveBatch` and `DiveRow` become `ImportBatch` and `ImportRow`, with events renamed to match.
  - The FileKit picker switches to multiple selection.
  - A preview sheet is added.
  - The save path re-reads and publishes in chunks.
- **`core/data`:**
  - `ActivityMatcher` in-batch matching.
  - `FeedRepository.ownActivitiesStartedBetween` called in month windows.
  - `SourceFile` per member.
- **Other modules:** `:app-fixtures` samples, screenshot references, and `.agents/rules/activity-files.md`.
- **No lexicon or AppView changes:** `feed.activitySource` already has `entry`, `fileName` and `compressed`.
- **No new dependencies.**
- **Out of scope:** pacing the outbox against PDS rate limits and draining it in the background. That's a follow-up change (`paced-outbox`). Until then, a large batch uploads while the app is open, using today's retry on 429. Gear named in an export is not matched to the athlete's gear: the batch's gear applies.
