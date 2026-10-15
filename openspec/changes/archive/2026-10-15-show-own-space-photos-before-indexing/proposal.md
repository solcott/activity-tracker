# Proposal

## Why

Right after an athlete publishes or edits a Followers, Mutuals or Only-me activity with photos, the device that wrote it shows broken images until the AppView indexes the record. Until then the client points each photo at the PDS's public `com.atproto.sync.getBlob` (`ownBlobUrl`), which refuses a blob that only a space record holds. Public activities don't have this problem, so non-public posting looks broken by comparison.

## What Changes

- The writing device keeps the bytes of each photo it uploads (the compressed `NewPhoto`), keyed by blob CID, for the session.
- Until the AppView serves a photo, the views the client builds for its own activities (`mediaViews`, used by `LocalRewrites` and new-activity views) show it from those bytes instead of from a PDS URL, whatever the activity's visibility.
- A photo whose bytes aren't held (the app restarted before indexing) shows a placeholder rather than a broken load. A public photo may still fall back to `ownBlobUrl`.
- Once the AppView serves the photo, its URL wins, as today, and the held bytes can be dropped.
- Not in scope: other devices of the same athlete. The feed always comes from the AppView, so they only see the activity once it's indexed and its photos are served. Avatars are always public, so `ownBlobUrl` stays for them.

## Capabilities

### New Capabilities

### Modified Capabilities
- `feed/activity-visibility`: adds a requirement that the athlete's own non-public photos show on the writing device before the AppView indexes the activity.

## Impact

- `core/data`: `ActivityPhotos.kt` (`mediaViews`), `LocalRewrites`, `FeedRepository`, `ActivityRepository.publish` / `ActivityManager.edit` (where photos are uploaded), a new session-scoped photo-bytes holder.
- `:app-screens` / `composeApp`: the media view must be able to carry bytes (or a key Coil resolves to bytes) as well as a URL. That touches `MediaItem`, `MediaRow`, the gallery and the details form, or a Coil `Fetcher`/`Keyer` for a local scheme.
- No lexicon, AppView or migration change.
