# Design

## Context

`PrivateMediaTokens` (`server/appview/.../api/SpaceMedia.kt`) signs `viewer`, `exp` and `sig` query parameters onto a non-public photo's URL, and the media route (`BlobRedirects.kt`, `privateMedia`) checks the signature and expiry, then the viewer's current access, before serving the photo with a fixed `Cache-Control: private, max-age=3600`. Expiry is `now + 1h` rounded up past the next whole hour. Nothing is deployed, so URLs signed under the old timing need no support.

## Goals / Non-Goals

**Goals:**
- Signed URLs valid for 15 to 30 minutes (spec: "Signed photo URLs are short-lived").
- A cached response never outlives its URL.
- The timing is testable with a fixed clock, end to end through the route.

**Non-Goals:**
- Revoking a URL before it expires beyond today's per-request access check, or binding it to a device.
- Any client change.

## Decisions

**Ttl and rounding step as two settings, both 15 minutes.** `exp = ceil((now + ttl) / step) * step`. Rounding keeps a viewer's URL for a photo identical across view loads within one step, so Coil's URL-keyed cache still hits. With ttl = step = 15 min, validity is in [15, 30) minutes. Alternatives: no rounding (exact 15 min) changes the URL on every load and defeats the image cache; a 5-minute step gives tighter bounds but more cache misses. Rounding is now a ceiling rather than "floor + 1", so an expiry landing exactly on a boundary isn't pushed a whole step further. The step must be positive; the constructor requires it.

**Max-age from the verified expiry.** `verify` returns the viewer together with the time left until `exp` (a small `SignedViewer` result), and `privateMedia` sets `Cache-Control: private, max-age=<seconds left>`. Alternatives: a fixed `max-age` of 15 minutes could still outlive a URL issued near the end of its window; reparsing `exp` in the route duplicates the token's checks.

**Tokens injectable into `appView`.** `appView` takes a `mediaTokens` parameter, defaulting to today's construction from the signing key, so route tests can pass tokens on a fixed clock and assert the exact max-age and an expired URL's rejection.

## Risks / Trade-offs

- [More image cache misses: a URL changes every 15 minutes instead of every hour] → Accepted (proposal). Blobs stay cached in the AppView's memory (`MemberSpaceBlobs`), so a miss doesn't refetch from the PDS.
- [A view held open past expiry shows broken images] → Clients reload views on navigation and refresh; the 15-minute floor leaves time to load what a screen shows.
- [Clock skew between the AppView and a client's cache] → Max-age is relative, so client clock skew doesn't extend it.
