# Proposal

## Why

A `feed.activityStreams` record says who may see it twice: once by where it's stored (the repo or a tier space) and again in its own `visibility` field. The field exists only for streams published to the repo before spaces, and the AppView still honours it there, although anyone who reads the repo can see those records. Nothing has been deployed beyond local dev, so there are no such records to support. Streams can take their visibility from where they're stored, as activities already do.

## What Changes

- **BREAKING** Remove `visibility` from the `feed.activityStreams` record (lexicon JSON and `ActivityStreamsRecord`). Clients stop writing it, and moving streams between places no longer rewrites it.
- The AppView serves a streams record at its placement's visibility: Public in the repo, the space's tier in a tier space (an unknown tier is Only me). It ignores any `visibility` a record still carries.
- `feed.getActivityStreams` keeps its `visibility` output, now derived from the placement, so clients still learn the charts' visibility from it.
- Remove the activity-visibility requirement "Existing activities stay Public". There are no activities from before visibility to keep.
- Remove the "Older records" item from `BACKLOG.md`'s streams section, and stop describing the record's own field in the AppView rule and code comments.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `feed/activity-visibility`: the charts storage requirement now also says that where a streams record is stored is its only visibility. The "Existing activities stay Public" requirement is removed.

## Impact

- **Lexicon:** `lexicons/app/activitytracker/feed/activityStreams.json`, `ActivityStreamsRecord` in `core/lexicon/.../app/Records.kt`, and `ActivityStreamsSerializationTest`.
- **Client:** `PdsActivityStreamsStore` (`put`, `move`) in `core/data`. `ActivityStreams.visibility` stays: it picks the place.
- **AppView:** `ActivityIndex.kt`'s streams branch uses `placement.visibility`. `ActivityStreamsTest` places non-public streams in tier spaces instead of the repo. The `activity_streams.visibility` column stays, so there's no migration.
- **Docs:** `.agents/rules/appview.md`, `BACKLOG.md`.
- **Local dev data:** a repo streams record that still says Followers, Mutuals or Only me will be served as Public. Re-publish or reset the local stack if that matters.
