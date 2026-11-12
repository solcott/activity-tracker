# Tasks

## 1. Shared steps

- [ ] 1.1 Add `PhotoCompression` (`steps`, `targetBytes`) and `PhotoStep` to `:app-common-api`, with a Kotlin test pinning them
- [ ] 1.2 Make `compressPhoto` in `:app-common-ui` read them; its JVM test still passes

## 2. libwebp on iOS

- [ ] 2.1 Add SDWebImage's `libwebp-Xcode` 1.6.0 to the Xcode project (exact pin, `Package.resolved`) for the app target
- [ ] 2.2 `PhotoCompressor`: ImageIO thumbnail (orientation, scale), sRGB RGBA drawing, unpremultiply, `WebPEncodeRGBA`, Kotlin's steps
- [ ] 2.3 `PhotoPick.app` compresses with it off the main actor; drop the HEIC retry from `PhotoPickOf`
- [ ] 2.4 The activity and gear photo pickers ask for `.current`
- [ ] 2.5 Remove `IosApp.compressPhoto` and the `compressPhoto` import from `:iosShell`

## 3. Tests

- [ ] 3.1 Swift: a large photo becomes a 2,048 × 1,536 WebP under 1.5 MB (`RIFF`/`WEBP` header)
- [ ] 3.2 Swift: an orientation-6 photo comes out upright, its colours where they show
- [ ] 3.3 Swift: no `EXIF`, `XMP ` or `ICCP` chunk after encoding a photo with GPS metadata; a HEIC compresses; junk gives nil
- [ ] 3.4 Swift: the step loop keeps the first fitting step and falls back to the last
- [ ] 3.5 Update `PhotoPickTests` for the removed retry

## 4. Docs and checks

- [ ] 4.1 Record the change in `openspec/explorations/ios-native-ui.md` and in `.agents/rules/adaptive-ui.md`
- [ ] 4.2 `./gradlew check -Pkotlin.incremental=false`
- [ ] 4.3 Ad-hoc-signed Release build
- [ ] 4.4 `xcodebuild test` on an iOS 26.0 and an iOS 18.4 simulator
