# Proposal

## Why

Athletes expect to take their data with them. Strava and Garmin both let an athlete download any of their own activities as GPX or TCX (and Garmin as FIT), rebuilt from what the service stores. Everything needed is already stored for the athlete:

- the activity record: sport, start time, duration, distance and the other summary fields
- its streams: per-second distance, altitude, speed, heart rate, power, cadence, temperature, depth and gear changes
- its full-resolution track (`keep-full-track-only-me`): positions with timestamps

This covers activities recorded in the app as well, which have no original file.

## What Changes

- An "Export" action on the athlete's own activity (detail screen menu), offering GPX and TCX. FIT is a follow-up (see Open questions).
- The client builds the file from the activity record, its `feed.activityStreams` data (read through the AppView's `getActivityStreams`, as charts are) and its `feed.activityTrack`, read from the `self` space with the athlete's session. Track points and stream samples are joined on time.
- The file is handed to the platform: a share sheet on Android and iOS, a save dialog on desktop, a download on the web.
- Only the athlete can export, since only they can read the track. Exporting someone else's activity is not offered.
- What a rebuilt file loses compared to the original:
  - laps, FIT developer fields, running dynamics, L/R balance and dive deco state, which aren't stored
  - sub-second timing, because streams are per second
  - every other sample on multi-day activities, which are halved to fit the blob cap
  - The export screen says it's rebuilt. `keep-original-activity-files` adds "Download original" for imported activities.

## Open questions

- **FIT export.** FIT needs an encoder on every target, wasmJs included. Today the per-platform FIT decoders only read. Ship GPX/TCX first and decide FIT in design (hand-written encoder vs a KMP library).
- **Dives.** GPX/TCX have no depth semantics. A dive might export as UDDF instead, or as a GPX with a depth extension.

## Capabilities

### New Capabilities
- `feed/activity-export`: exporting the athlete's own activity as a rebuilt GPX or TCX file, what it contains, and who can do it.

### Modified Capabilities

## Impact

- `:core-activityfile`: GPX and TCX writers (extend `Gpx` from `core/location` or move it), sharing the units and mapping in `StreamBuilder` in reverse.
- `core/data`: an export use case that reads the record, streams and track.
- `:app-screens` / `:app-presenters` / `composeApp`: the detail screen action, and a platform file-save/share expect/actual (Calf may already cover the file picker).
- No lexicon or AppView change beyond `keep-full-track-only-me`, which this depends on.
