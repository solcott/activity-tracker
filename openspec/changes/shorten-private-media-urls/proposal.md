# Proposal

## Why

A non-public activity's photo URL is signed for one viewer and rechecked against their access on every request. But anyone holding the URL can use it until it expires. `PrivateMediaTokens` sets a 1-hour lifetime and rounds expiry up to the next hour, so a URL that gets passed on keeps working for 1 to 2 hours. Shortening that window limits how far a leaked link travels, and nothing has been deployed yet that depends on the current timing.

## What Changes

- `PrivateMediaTokens` signs URLs with a 15-minute lifetime, and rounds expiry up to the next 15-minute boundary (a separate step setting, no longer a hard-coded hour). A URL then stays valid for 15 to 30 minutes after it's issued.
- The private media response's `Cache-Control` max-age is no longer than the URL's remaining lifetime, so a cached response never outlives its link.
- A viewer whose URL has expired gets a fresh one the next time a view (feed, activity) is loaded. Clients need no change: they already reload views, and Coil caches by URL.
- Tradeoff, accepted: a viewer's image cache misses up to four times as often, since the URL changes every 15 minutes rather than every hour.

## Capabilities

### New Capabilities

### Modified Capabilities
- `spaces/appview-syncer`: adds a requirement bounding how long a signed URL for a non-public photo stays valid (at most 30 minutes) and how long its response may be cached.

## Impact

- `server/appview`: `SpaceMedia.kt` (`PrivateMediaTokens`), `BlobRedirects.kt` (`privateMedia`'s `Cache-Control`), and their tests (`ActivityVisibilityTest` or the tokens' own tests, with a fixed clock).
- Update the bearer-link bullet in BACKLOG.md once shipped.
- No lexicon, client or migration change.
