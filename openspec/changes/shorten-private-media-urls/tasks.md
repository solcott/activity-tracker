# Tasks

## 1. Token lifetime

- [x] 1.1 In `SpaceMedia.kt`, give `PrivateMediaTokens` a 15-minute default ttl and a separate rounding `step` (default 15 minutes, required positive), and round expiry up with a ceiling to the next step; verify with a `PrivateMediaTokensTest` on a fixed clock that expiry is 15 to 30 minutes out at, just after and just before a boundary, and stable within a window
- [x] 1.2 Make `verify` return the viewer with the time left until expiry (`SignedViewer`); verify in `PrivateMediaTokensTest` that a genuine URL reports its time left and an expired one (clock moved past `exp`) is rejected
- [x] 1.3 Update the `PrivateMediaTokens` KDoc to the new timing and verify it no longer mentions the hour

## 2. Media response

- [x] 2.1 In `BlobRedirects.kt` `privateMedia`, set `Cache-Control: private, max-age` to the seconds left until the URL's expiry; verify in `SpaceMediaTest` (the photo and stream tests moved out of `ActivityVisibilityTest`, which detekt's `LargeClass` had capped) that a photo served on a fixed clock carries that exact max-age
- [x] 2.2 Let `appView` take `mediaTokens` (defaulting to tokens from the signing key) and verify in `SpaceMediaTest` that a URL served after the clock passes its expiry answers 404 and fetches nothing

## 3. Verification

- [x] 3.1 Run `./gradlew :server-appview:test` and `./gradlew check` and verify both pass
- [x] 3.2 Run `openspec validate shorten-private-media-urls` and verify it passes

## Workflow follow-up

- Update the bearer-link bullet in BACKLOG.md to the 15–30 minute window.
- Archive the change after review.
