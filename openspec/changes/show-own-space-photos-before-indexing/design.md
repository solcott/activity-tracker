# Design

## Context

A photo reaches the UI as a URL string end to end: `ActivityMediaView.Image.fullsize` (a lexicon view) becomes `MediaItem.Photo.url` in `:app-screens`, and `MediaRow`, the gallery and the details form hand it to Coil's `AsyncImage`. The client builds views for its own records only in `LocalRewrites.rewritten` (an edit, a merge or an ungroup laid over the AppView's view until it catches up), through `mediaViews`; a new activity isn't shown until the AppView serves it. Today `mediaViews` matches a served photo by `fullsize.endsWith("/$cid")` and falls back to `ownBlobUrl` (public `sync.getBlob`). The edit form (`existingMedia`) matches the same way.

A non-public photo's AppView URL is signed for the viewer (`/img/media/{did}/{cid}?…`), so `endsWith("/$cid")` never matches it: an edit of a non-public activity falls back to `ownBlobUrl` for every photo, and the edit form drops photos it can't match, so saving removes them.

## Goals / Non-Goals

**Goals:** own photos show on the writing device before indexing at any visibility (see specs); non-public photos never hit `sync.getBlob`; served photos are matched by CID whether signed or not.

**Non-Goals:** persisting the bytes across restarts; other devices; avatars (always public, so `ownBlobUrl` stays for them); a new-activity view before indexing.

## Decisions

### Bytes reach Coil through a local URL scheme, not a byte-carrying model

`mediaViews` gives a held photo the URL `heldphoto://blob/{cid}`. `composeApp` registers a Coil `Fetcher.Factory<Uri>` for that scheme on the singleton `ImageLoader` (`setSingletonImageLoaderFactory` in `App`), which answers from the session's held bytes and fails fast when they aren't held.

- *Alternative: a byte-carrying media model.* `ActivityMediaView` is a lexicon view and `MediaItem.Photo` is a stable `:app-screens` type carrying a `String`; carrying bytes would touch the lexicon mirror (or add a parallel client-only view), `MediaItem`, `MediaRow`, the gallery, the details form and their stability, and put megabytes into every `ActivityView` copy and the Room feed cache JSON. The scheme touches none of `:app-screens`.
- *Alternative: `data:` URIs.* Same zero-UI-change appeal, but base64 photos would live in the cached feed JSON and Compose state, and can't end with `/{cid}`, so the edit form couldn't name them.
- No custom `Keyer` is needed: Coil keys a `Uri` by its string, which is unique per CID, and only the network fetcher writes the disk cache.

### One CID parser for every photo URL

`photoCid(url)` in `ActivityPhotos.kt` returns the last path segment of a photo URL, ignoring its query: it names the blob in an AppView URL (public or signed) and in a held URL. `mediaViews` and `existingMedia` match through it instead of `endsWith("/$cid")`. This fixes the signed-URL mismatch described in Context.

### The holder lives in `FeedRepository`, filled by `upload`

`HeldPhotos` is a plain class holding `NewPhoto`s by CID in a `MutableStateFlow<Map>` (thread-safe for Coil's fetcher threads and the writing coroutines). `FeedRepository` owns one (`feed.heldPhotos`), like `rewrites` and `deletes`, so it is session-scoped with no new DI binding or constructor parameter at the many test sites. `SessionClients.upload(photo, held)` holds every photo it uploads, so `publish`, `mergeInto` and `ActivityManager.edit` all fill it. `SessionGraph` exposes `feedRepository` so `App` can point the app-wide fetcher at the current session's holder, and clears it when the session leaves composition.

### Which URL `mediaViews` picks

For each image in the record, in order: the AppView's served view with the same CID (and drop the held bytes); else the held URL if the bytes are held; else `ownBlobUrl` for a Public activity; else the held URL anyway. In that last case the fetcher fails without a network request, so the tile shows its empty placeholder background, and the photo stays in the list so the edit form keeps it (it's named by CID) and a later save doesn't drop it.

`FeedRepository` also drops held bytes for photos the AppView serves in any view it reads (`activity`, feed refreshes), so photos of an activity that's never rewritten (a plain publish) are let go once indexed.

## Risks / Trade-offs

- [Held bytes use memory for the session] → bounded by what the athlete uploads in one session (at most `ActivityMedia.MAX` compressed photos per write), and dropped as the AppView serves each.
- [The singleton `ImageLoader` is app-wide but the holder is per session] → `App` swaps the holder reference with the session's `key(did)` composition; with no session the fetcher reports every held URL as missing.
- [A held URL cached in Room outlives the process] → after a restart its bytes aren't held, so it shows the placeholder until the AppView catches up and the next read replaces the view (spec: "Bytes not held").

## Migration Plan

Client-only; local environment, no backwards compatibility needed.
