# Tasks

## 1. Baseline

- [x] 1.1 Build the current OpenGL release APK (`./gradlew :androidApp:assembleRelease`) and record each ABI's APK size, so there's a number to compare against

## 2. Swap to Vulkan

- [x] 2.1 Add `maplibre-compose-runtime-vulkan-android` (version `maplibreCompose`) to `gradle/libs.versions.toml`, and verify it resolves (`./gradlew :composeApp:dependencies --configuration androidRuntimeClasspath` lists `maplibre-native-ffi-runtime-vulkan`)
- [x] 2.2 In `composeApp/build.gradle.kts` `androidMain`, replace the OpenGL runtime with the Vulkan one, and verify `./gradlew :androidApp:assembleDebug` builds
- [x] 2.3 Build the release APK, record each ABI's size against 1.1, and check whether `androidApp/src/main/keepRules/app.keep` needs a rule for the Vulkan runtime (the release build's map loads without a ClassNotFound or JavaCPP Loader error in logcat)

## 3. Check rendering by hand

- [x] 3.1 On the Apple Silicon arm64 emulator (`./gradlew :androidApp:installDebug`), open a ride with a route: the detail map and the enlarged map render tiles and the route, and logcat shows no Vulkan or MapLibre errors
- [x] 3.2 On a real arm64 phone (the oldest available), install the release build and repeat 3.1
- [x] 3.3 On both, scroll the detail list over the map, and open and close the enlarged map: no clipping, flicker or black frames

## 4. Decide and record

- [x] 4.1 Apply the design's pass criteria:
  - **Pass:** keep Vulkan, drop the `maplibre-compose-runtime-opengl-android` catalog entry, and rewrite the `androidMain` comment to give the backend and why. Verify `./gradlew check` passes.
  - **Fail:** revert 2.1–2.2, and rewrite the comment to name what failed. Verify `./gradlew check` passes.
- [x] 4.2 Update BACKLOG.md's Renderer notes with the result:
  - the devices tested
  - the APK size change
  - when to revisit, for example a MapLibre Compose release that changes the Android default, or heavier map layers

  Verify the note names each device from 3.1–3.2.
