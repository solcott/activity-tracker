# Proposal

## Why

An imported FIT, GPX, TCX, Shearwater, UDDF or Subsurface file holds more than we store: laps, developer fields, running dynamics, dive deco state, device events. Once parsed, it's discarded (`ActivityFileReader`). A rebuilt export (`export-activity-files`) can't bring those back, and keeping the file would also let a future import re-read it as parsers improve. Strava's and Garmin's "Export original" exist for this reason. It has to be done at import time: a file that wasn't kept can't be recovered later.

## What Changes

- New record `app.activitytracker.feed.activitySource`, same rkey as its activity, always in the athlete's `self` (Only me) space. It holds the original file as a gzipped blob plus its format, original file name and size.
- Import uploads it through the outbox with the activity, like the track.
- A logbook file (UDDF, Subsurface, a multi-dive Shearwater export) is uploaded once. Each activity imported from it gets its own source record referencing the same blob (blobs are content-addressed) and naming which entry it came from.
- A merge keeps the primary's source, and the merged recording keeps its own record. An ungroup leaves both. Deleting an activity deletes its source record.
- "Download original" sits beside Export on the athlete's own imported activities and hands back the file as it was imported, un-gzipped.
- Recorded-in-app activities have no source record and offer only the rebuilt export.
- The AppView neither indexes nor serves it. The space declaration lists the collection and says so.
- Only new imports get a source record; nothing is backfilled.

## Open questions

- **Size.** FIT files are small, but a logbook or a long GPX can be tens of MB before gzip. The local PDS caps blobs at `PDS_BLOB_UPLOAD_LIMIT`. Decide the lexicon `maxSize`, and what happens over it (import without the original, with a notice).
- **Opt-out.** Whether to always keep originals, or offer a setting. Originals can hold serial numbers and other device data we deliberately don't publish (`activity#sensor`). That's acceptable in `self`, but worth stating.

## Capabilities

### New Capabilities
- `feed/activity-source-file`: keeping an imported activity's original file privately, sharing one logbook file across its activities, and downloading it.

### Modified Capabilities

## Impact

- Lexicons: new `feed/activitySource.json`, `space/athlete.json` (collections + description), `Nsid`, `core/lexicon` model. Republish local lexicons and sign in again.
- `:core-activityfile`: keep the raw bytes and format alongside each `ImportedActivity`, and gzip them (`Gzip.kt`).
- `core/data`: `ActivityRepository` import path, `OutboxProcessor` (a shared blob across rows), merge/ungroup/delete in `ActivityManager`.
- UI: "Download original" on the detail screen, reusing the file hand-off from `export-activity-files`.
- Depends on `keep-full-track-only-me` (the same Only-me record pattern) and shares UI with `export-activity-files`.
