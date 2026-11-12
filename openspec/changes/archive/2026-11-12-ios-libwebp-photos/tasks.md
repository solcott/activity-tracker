# Tasks

## 1. Shared steps

- [x] 1.1 Add `PhotoCompression` (`steps`, `targetBytes`) and `PhotoStep` to `:app-common-api`, with a Kotlin test pinning them
- [x] 1.2 Make `compressPhoto` in `:app-common-ui` read them; its JVM test still passes

## 2. libwebp on iOS

- [x] 2.1 Add SDWebImage's `libwebp-Xcode` 1.6.0 to the Xcode project (exact pin, `Package.resolved`) for the app target
- [x] 2.2 `PhotoCompressor`: ImageIO thumbnail (orientation, scale), sRGB RGBA drawing, unpremultiply, `WebPEncodeRGBA`, Kotlin's steps
- [x] 2.3 `PhotoPick.app` compresses with it off the main actor; drop the HEIC retry from `PhotoPickOf`
- [x] 2.4 The activity and gear photo pickers ask for `.current`
- [x] 2.5 Remove `IosApp.compressPhoto` and the `compressPhoto` import from `:iosShell`

## 3. Tests

- [x] 3.1 Swift: a large photo becomes a 2,048 × 1,536 WebP under 1.5 MB (`RIFF`/`WEBP` header)
- [x] 3.2 Swift: an orientation-6 photo comes out upright, its colours where they show
- [x] 3.3 Swift: no `EXIF`, `XMP ` or `ICCP` chunk after encoding a photo with GPS metadata; a HEIC compresses; junk gives nil
- [x] 3.4 Swift: the step loop keeps the first fitting step and falls back to the last
- [x] 3.5 Update `PhotoPickTests` for the removed retry

## 4. Docs and checks

- [x] 4.1 Record the change in `openspec/explorations/ios-native-ui.md` and in `.agents/rules/compose-screens.md` (the native photo pick)
- [x] 4.2 `./gradlew check -Pkotlin.incremental=false`
- [x] 4.3 Ad-hoc-signed Release build
- [x] 4.4 `xcodebuild test` on an iOS 26.0 and an iOS 18.4 simulator

## Checks

- `./gradlew check -Pkotlin.incremental=false`: passed (9 min 12 s), after `PhotoCompression.steps` became a getter: strict stability
  refuses a `List` field in `:app-common-api`.
- `xcodebuild test` on an iOS 26.0 clone: 445 of 446 passed, every photo, pick and avatar test among them. `ImportSnapshotTests` failed
  there, and alone three more times, each time a different snapshot (`zoneSheet` "utc8" light and dark, then `wide` iPad, then
  `zoneSheet` "utc8" dark): the search field's clear button not yet drawn, or the page drawn about 20 points higher. Nothing this
  change touches draws those screens, and another session was running builds and tests on the machine at the time; not rerun on the
  base commit, so treated as load flakes, not confirmed as such.
- `xcodebuild test` on an iOS 18.4 clone, built for it: 446 tests passed, snapshots skipped. The HEIC test writes and reads its HEIC
  there too.
- Release for the simulator, ad-hoc signed: built, the stub check passed.

## Not verified

- **A real photo picked and uploaded:** the simulators' library wasn't used and no account was signed in, so picking a camera photo
  (HEIC, Display P3, a portrait one), seeing it on the form and saving it to the PDS weren't seen. The tests cover the compression with
  generated JPEG and HEIC photos, the orientation, the metadata and the pick's plumbing into `PickedPhoto`.
- **A device build:** only simulator builds were made; libwebp builds from source for arm64 the same way, but no device build ran.
