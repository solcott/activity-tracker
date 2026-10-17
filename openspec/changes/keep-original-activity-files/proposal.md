# Proposal

## Why

An imported FIT, GPX, TCX, Shearwater, UDDF or Subsurface file holds more than we store: laps, developer fields, running dynamics, dive deco state, device events. Once parsed, it's discarded (`ActivityFileReader`). A rebuilt export (`export-activity-files`) can't bring those back, and keeping the file would also let a future import re-read it as parsers improve. Strava's and Garmin's "Export original" exist for this reason. It has to be done at import time: a file that wasn't kept can't be recovered later.

## What Changes

- New record `app.activitytracker.feed.activitySource`, same rkey as its activity, always in the athlete's `self` (Only me) space. It holds the original file as a gzipped blob plus its format, original file name and size.
- Import uploads it through the outbox with the activity, like the track.
- A logbook file (UDDF, Subsurface) is uploaded once. Each activity imported from it gets its own source record referencing the same blob (blobs are content-addressed) and naming which entry it came from.
- Merging an import into a logged activity gives the activity the import's file only when it has none, as with the track. A dive grouped as another recording keeps its own source record. An ungroup leaves both. Deleting an activity deletes its source record.
- "Original file" joins the Export sheet on the athlete's own imported activities and hands back the file byte for byte as it was imported.
- Recorded-in-app activities have no source record and offer only the rebuilt export.
- The AppView neither indexes nor serves it. The space declaration lists the collection and says so.
- Only new imports get a source record; nothing is backfilled.
- **BREAKING**: the space declaration gains a collection, so the local lexicons are republished and athletes sign in again.

## Decisions on the earlier open questions

- **Size.** The stored (gzipped) file is capped at 10 MB, like the track blob. A file over that imports without its original, and the import screen says so before saving. A PDS that refuses the blob anyway (a lower upload limit) drops just the original and doesn't hold up the outbox. The local stack raises its PDS's blob upload limit to fit the cap.
- **Opt-out.** Originals are always kept, with no setting. They can hold serial numbers and other device data we deliberately don't publish (`activity#sensor`), which is acceptable because only the athlete can read `self`.

## Capabilities

### New Capabilities
- `feed/activity-source-file`: keeping an imported activity's original file privately, sharing one logbook file across its activities, and downloading it.

### Modified Capabilities
- `feed/activity-export`: the Export sheet also offers the original file, so an imported activity with neither track nor streams still offers Export, and the "rebuilt" note covers only the rebuilt formats.

## Impact

- Lexicons: new `feed/activitySource.json`, `space/athlete.json` (collections + description), `Nsid`, `core/lexicon` model. Republish local lexicons and sign in again.
- `:core-activityfile`: report each file's format and whether it came gzipped alongside its `ImportedActivity`s, and gzip bytes (`Gzip.kt` gains a compressor next to `gunzip`).
- `core/data`: `ActivityRepository` publish, batch and merge paths, a source store like `ActivityTrackStore`, `OutboxProcessor` and Room (one blob shared by several outbox rows, a new migration), delete in `ActivityManager`, `ActivityExporter` (download).
- UI: the import screen's size notice, "Original file" in the detail screen's Export sheet, reusing the file hand-off from `export-activity-files`.
- `docker/compose.yaml`: the PDS's blob upload limit.
- Builds on `keep-full-track-only-me` (the same Only-me record pattern) and `export-activity-files`, both archived.
