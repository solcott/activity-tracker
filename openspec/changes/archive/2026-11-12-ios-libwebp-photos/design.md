# Design

## Context

`compressPhoto` (`:app-common-ui` commonMain) runs a fixed list of steps through a platform `encodePhoto` and keeps the first result
under 1.5 MB. Android encodes with `Bitmap.compress(WEBP_LOSSY)`; desktop, web and iOS share a Skia actual (`nonAndroidMain`) that reads
the EXIF origin, draws the picture upright and scaled onto a raster surface, and encodes the snapshot as WebP. Drawing pixels and
encoding only them is what strips the metadata (GPS included): no EXIF, XMP or ICC chunk is written.

Every iOS screen that picks a photo is native now (the gear editor and the activity forms; the profile photo has its own path), and
each hands the bytes to `IosApp.compressPhoto`, which calls the shared `compressPhoto`. Phase 5 removes Skia from the iOS app, and ImageIO
can read WebP but not write it.

## Goals / Non-Goals

**Goals:** WebP from libwebp on iOS, with ImageIO and Core Graphics doing the decoding, orientation and scaling; the same steps,
qualities and target; metadata still stripped; HEIC read directly; a reproducible iOS build.

**Non-Goals:** changing Android, desktop or web output; the avatar's JPEG; deleting the Compose iOS actual before phase 5.

## Decisions

### libwebp as a Swift package, called from Swift

Two routes were weighed:

- **A Swift package**: SDWebImage's `libwebp-Xcode` packages Google's libwebp sources for Swift Package Manager (a `libwebp` C target
  over `src/` and `sharpyuv/`, public headers in `include/`). It is BSD-3-Clause like libwebp, maintained by SDWebImage, whose image
  stack depends on it, and tracks upstream: 1.6.0 (libwebp 1.6.0) was released on 2026-07-31, 1.5.0 in February 2025. SPM builds it from
  source for every architecture and simulator, so nothing prebuilt is checked in, and the exact pin plus `Package.resolved` keep the
  build reproducible, as for Nuke and the FIT SDK. Swift calls the C API directly (`WebPEncodeRGBA`, `WebPFree`).
- **A Kotlin/Native cinterop**: a `.def` over libwebp's headers needs libwebp's static libraries built per Kotlin target
  (`iosArm64`, `iosSimulatorArm64`) by CMake or a Gradle task, either checked in or built by the Gradle build, which then needs CMake
  on every machine and in CI. It is a larger build change for the same C calls.

The Swift package is simpler and better maintained. Swift-WebP (ainame, MIT) wraps the same `libwebp-Xcode` in a Swift API, but the
encoder needs one C call, so the thinner package is enough.

### Swift runs the steps; Kotlin owns them

On iOS nothing in Kotlin needs to compress a photo any more: the pickers are Swift. So `PhotoCompressor` (Swift) runs the whole pick:
for each of Kotlin's steps it decodes, scales and encodes, keeping the first result under the target, else the last step's. The steps and
target move from private constants in `:app-common-ui` to `PhotoCompression` in `:app-common-api` (`steps`, a list of `PhotoStep`, and
`targetBytes`), where the shared loop and Swift both read them. `:app-common-api` is exported to Swift whole, and the step is a plain
class of two `Int`s, which Swift export carries. The last step is the fallback (`compressPhoto` used to name it apart as
`LAST_RESORT_*`); the list keeps the same four pairs.

Rejected: a Kotlin `PhotoEncoder` interface Swift implements (as `SwiftFitDecoder` implements `FitDecoder`), with the loop staying in
Kotlin behind `IosApp`. It keeps one loop, but each step would cross to Swift and back with the bytes, the loop would have to move out of
`:app-common-ui` anyway before phase 5, and the shared UI's `ByteArray`-based `compressPhoto` would need a second, `ByteString`-based
entry for Swift. The loop is four lines in each language; the policy is what must not drift, and it lives once.

### Decoding, orientation and scaling: one ImageIO call

`CGImageSourceCreateThumbnailAtIndex` with `kCGImageSourceCreateThumbnailFromImageAlways`, `kCGImageSourceCreateThumbnailWithTransform`
and `kCGImageSourceThumbnailMaxPixelSize` set to the step's pixels decodes the picture (JPEG, HEIC, PNG, WebP and the rest ImageIO
reads), applies its EXIF orientation and scales its long edge to at most that size, never up. It also decodes at a reduced size where the
codec can, as Android's `inSampleSize` does. This replaces Skia's `EncodedOrigin` matrix.

The result is drawn into an 8-bit RGBA bitmap context in sRGB, so a Display P3 photo is converted rather than written with its P3 values
and no profile (Skia's raster surface had no colour space, so it wrote the values unconverted; viewers read an untagged WebP as sRGB).
The context's pixels are premultiplied, which `WebPEncodeRGBA` doesn't take, so `vImageUnpremultiplyData_RGBA8888` (Accelerate)
unpremultiplies them in place first; for an opaque photo it changes nothing, and libwebp writes no alpha for it.

### Encoding and metadata

`WebPEncodeRGBA(pixels, width, height, stride, quality)` is libwebp's simple lossy encoder at the default preset, as Skia's encoder used
it. Only pixels reach it, so the output is a bare `RIFF … WEBP VP8 ` file with no `EXIF`, `XMP ` or `ICCP` chunk: the metadata is
stripped as before.

### HEIC and the pickers

ImageIO reads HEIC, so `PhotoPick` no longer retries what the compressor refuses as UIKit's JPEG (bytes ImageIO can't read aren't a
picture at all), and the activity and gear `PhotosPicker`s ask for `.current` instead of `.compatible`, so a HEIC isn't transcoded to a
JPEG first. `AvatarPick` shares `PhotoPickOf`, and FileKit's avatar compression reads a HEIC through UIKit already
(`ios-native-edit-profile`), so it loses nothing; its picker keeps `.compatible`.

### Off the main actor

The app's default isolation is the main actor. The compression is `@concurrent`, so it runs on the cooperative pool as Kotlin's ran on
`Dispatchers.Default`, and returns a `Sendable` Swift value that `PhotoPick.app` turns into Kotlin's `PickedPhoto` on the caller's actor.

## Risks / Trade-offs

- **Pixel-exact output differs from Skia's.** ImageIO's scaler and libwebp's version differ, and colours are now converted to sRGB; the
  size, format, steps and target are the same. Uploads are lossy re-encodes either way.
- **Debug builds compile libwebp unoptimised**, so encoding in a Debug build and in tests is slower than in Release. The tests encode a
  few photos only.
- **A new package to keep current.** It is pinned exactly; a libwebp security release means bumping the pin.

## Migration

None: the format, sizes and lexicon are unchanged, and old photos are untouched.
