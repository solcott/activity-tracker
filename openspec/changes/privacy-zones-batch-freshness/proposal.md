# Proposal

## Why

A batch import reads the privacy zones once and keeps that read in a shared field that every publish uses for as long as the import runs. That causes two problems (review findings C7 and C8, `docs/reviews/2026-10-23-codebase-review.md`). First, a recording published from elsewhere in the app during the import, and the import's own later activities, are trimmed by that read and counted as fresh, even after the athlete saves a new zone during the import. Because they count as fresh, they are never re-trimmed, so a route the athlete just chose to hide can go out with the new zone uncut. Second, the batch field and the list of stale routes are changed outside the class's mutex, so concurrent publishes can race on them.

## What Changes

- Only publishes inside the batch use its read. A publish anywhere else reads the zones as it does now: fresh, else the last read, else no polyline.
- A zone save or a newer read made during a batch replaces the batch's read for the publishes still to come.
- An activity published with zones that a save or newer read replaced before the publish finished no longer counts as fresh. It's remembered as stale and re-trimmed like any other publish without a fresh read. Its segment track is swept the same way.
- Every access to the batch's read and the stale routes goes through the zones' lock, so concurrent publishes, re-trims and saves can't lose or corrupt an entry.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `profile/privacy-zones`: two requirements added. A batch's read of the zones applies only to its own publishes, and a zone change made during a batch reaches every publish still to come.

## Impact

- `core/data`: `PrivacyZones.kt` (`forPublish`, `withReadOnce`, `published`, `takeStale`, `hasStale`), with callers in `ActivityRepository.publish`, `publishAll` and `queue`, `ActivityManager`'s combine, and `RouteTrimmer.trimStale`. `published` and `takeStale` become `suspend`, and `hasStale` becomes a suspend function.
- `segments/SegmentTracks.put` gets the freshness that `published` decided, so a superseded segment track is swept.
- Tests: `core/data/src/jvmTest/.../PrivacyZonesTest.kt`.
- No lexicon, AppView, UI or migration changes.
