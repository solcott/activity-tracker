# Design

## Context

Activities already take their visibility from where they're stored: `Placement` (`server/appview/.../index/ActivityIndex.kt`) is `repo` → Public, or a tier space → `Visibility.known(skey)`. Streams index through the same `place()`, but the streams branch picks their visibility differently:

```
repo  : record.visibility (as is)              <- legacy, for streams from before spaces
space : narrower(record.visibility, tier)      <- the client always writes the tier
```

The client writes `visibility` equal to the place it chose (`PdsActivityStreamsStore.put`), and `move()` rewrites the field to the destination's tier. `getActivityStreams` returns the indexed row's visibility, and the client reads it back (`ActivityRepository.mergeStreams`, `have?.visibility`) to choose where merged streams go.

## Goals / Non-Goals

**Goals:**
- One source of truth for a streams record's visibility: its placement, as for activities.

**Non-Goals:**
- Changing `getActivityStreams`' output. It keeps `visibility`, now always the placement's.
- Changing the `activity_streams` table. The `visibility` column keeps the tier a row is served at, so no migration is needed.
- Changing how charts are capped by their activity. `ActivityStreamsViews.activityVisible` still hides charts from a viewer who can't see the activity.

## Decisions

- **Remove the field rather than ignore it.** Nothing outside local dev has records with it, so removing a required field breaks no one. Keeping it would leave a second answer that can disagree with the placement. Alternative: keep it optional and unused, which would leave dead data in every record.
- **The AppView ignores a stray `visibility`.** `AtJson` ignores unknown keys, so an old or hand-made record still decodes. The indexer uses `placement.visibility` for both origins. The raw JSON stays in `activity_streams.record`, untouched, as it already does for shadows.
- **`move()` copies the record unchanged.** Without a field to rewrite, the transform passed to `SessionClients.moveOwn` returns the record as is (or the parameter goes, if nothing else uses it).
- **`ActivityStreams.visibility` (the client's write model) stays.** It's how callers say where to store the streams (`clients.placeOf`); it just isn't copied into the record.
- **Tests place non-public streams in spaces.** `ActivityStreamsTest` currently commits non-public streams to the repo over Jetstream and expects them hidden. Those cases move to tier spaces, indexed the way `ActivityVisibilityTest` indexes space records. The test also gains one case where a repo record still claims `self` and is served as Public.

## Risks / Trade-offs

- [A local stack holds repo streams marked non-public, which are now served as Public] → Accepted. The data is local test data, and anyone could already read it from the repo. Re-publish or reset the stack if it matters.
- [Clients built before this change still write `visibility`] → Harmless. The AppView ignores the field. Only local builds exist.
