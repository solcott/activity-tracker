# Proposal

## Why

Phase 5 of the native iOS migration removes Compose and Skia from the iOS app. A photo picked for an activity or a gear item is still
re-encoded as WebP by the shared `compressPhoto`, whose iOS `encodePhoto` draws and encodes through Skia, and iOS has no system WebP
encoder (ImageIO writes JPEG and HEIC, not WebP). The athlete chose libwebp over a JPEG fallback, so every platform keeps uploading the
same format. Skia also can't read a HEIC, which is why the native pick asks the photo picker for a compatible encoding and retries what
Kotlin refuses as UIKit's JPEG.

## What Changes

- **iOS compresses a picked photo natively** (`PhotoCompressor`, `iosApp`): ImageIO decodes the picture (a HEIC included), turns it
  upright by its EXIF orientation and scales it in one step, Core Graphics draws it into sRGB pixels, and libwebp encodes them as
  lossy WebP. Nothing in the iOS photo path uses Skia or Compose.
- **libwebp reaches the app as a Swift package**: SDWebImage's `libwebp-Xcode` (BSD-3-Clause, libwebp 1.6.0 built from source by
  Swift Package Manager), pinned to an exact version in the Xcode project as Nuke and the FIT SDK are.
- **The steps stay Kotlin's and shared** (`PhotoCompression` in `:app-common-api`): 2,048 px at quality 80, then 70, then 1,600 px at
  70, keeping the first under 1.5 MB, else 1,280 px at 60. The shared `compressPhoto` (Android, desktop, web) and the Swift compressor
  both read them, so the two can't drift.
- **The HEIC retry goes**: `PhotoPick` hands the picked bytes straight to the compressor, and the activity and gear pickers ask for the
  photo's current encoding, so a HEIC isn't transcoded to JPEG before it's compressed.
- **`IosApp.compressPhoto` goes**: nothing in Swift calls Kotlin to compress a photo any more, so `:iosShell` no longer reaches
  `compressPhoto` in `:app-common-ui`.

Out of scope: Android, desktop and web (only the step constants move); the profile photo (`compressAvatar`, FileKit's JPEG over UIKit,
which uses no Skia); removing the Compose iOS `encodePhoto` actual, which no iOS screen reaches any more and which goes with the UI
modules' iOS targets in phase 5.

## Capabilities

### New Capabilities

### Modified Capabilities

- `dev/ios-swift-interop`: a natively picked photo is compressed by Swift with Kotlin's steps, as WebP, upright and without metadata,
  a HEIC included.

## Impact

- `iosApp`: a new Swift package (`libwebp`), `PhotoCompressor`, `PhotoPick` without the retry, the pickers' encoding, tests.
- `:app-common-api`: `PhotoCompression` and `PhotoStep`. `:app-common-ui`: `compressPhoto` reads them.
- `:iosShell`: `IosApp.compressPhoto` removed.
