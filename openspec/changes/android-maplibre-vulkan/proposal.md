# Proposal

## Why

Android is the one target whose map still renders with OpenGL ES. iOS and macOS use Metal, and Windows and Linux use Vulkan. The build gives "wider device and emulator support" as the reason (`composeApp/build.gradle.kts`), but that has weakened:
- MapLibre Compose 0.20.0 renders on Android through `maplibre-native-ffi`, whose status page gives its OpenGL and Vulkan backends the same status.
- The classic MapLibre Android SDK has made Vulkan its default since 13.0.0.
- Android is moving OpenGL ES onto a translation layer over Vulkan.

Switching now keeps Android on the backend upstream is investing in, and means one Vulkan code path for Android and desktop.

## What Changes

- Swap the Android MapLibre runtime from `maplibre-compose-runtime-opengl-android` to `maplibre-compose-runtime-vulkan-android`.
- This is a spike: keep Vulkan only if the route map renders and scrolls cleanly on the Apple Silicon emulator and on real arm64 phones, and the APK size cost is acceptable. Otherwise revert to OpenGL.
- Either way, record the outcome in the build comment and in BACKLOG.md's renderer notes, so the question isn't reopened without the evidence.

No user-visible behavior changes: the detail map and the enlarged map (`feed/route-map-viewer`) look and behave the same.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
None. This swaps the rendering backend and changes no requirement, so the change sets `skip_specs: true`.

## Impact

- `gradle/libs.versions.toml`: a new `maplibre-compose-runtime-vulkan-android` entry (version `maplibreCompose`). The OpenGL entry is dropped if Vulkan stays.
- `composeApp/build.gradle.kts`: the `androidMain` runtime dependency and its comment.
- `androidApp/src/main/keepRules/app.keep`: check the JavaCPP keep rule still covers the Vulkan ffi runtime in release builds.
- `BACKLOG.md`: Renderer notes.
- Android release APK size: not measured yet.
- No AppView, lexicon, iOS, desktop or web changes.
