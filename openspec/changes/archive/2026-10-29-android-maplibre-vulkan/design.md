# Design

## Context

`composeApp` takes MapLibre Compose from `nonWebMain` and picks its native runtime per platform:
- **Android:** `runtimeOnly(libs.maplibre.compose.runtime.opengl.android)` in `androidMain`.
- **Desktop:** `maplibreDesktopRuntime()` in `:desktopApp` picks Metal on macOS and Vulkan elsewhere.

The Android runtime depends on `org.maplibre.nativeffi:maplibre-native-ffi-runtime-opengl`, not on the classic `org.maplibre.gl` Android SDK. MapLibre's API stays inside `ActivityMap.nonWeb.kt`, which never names a backend.

Previews, UI tests and screenshot tests draw the route without MapLibre (`ActivityMap.nonWeb.kt`), so no automated test runs the native renderer.

## Goals / Non-Goals

**Goals:**
- Run the Android map on Vulkan, or know concretely why not.
- Leave the outcome written where the next person looks: the build comment and BACKLOG.md.

**Non-Goals:**
- **Choosing the backend at runtime:** no OpenGL fallback inside the same build.
- **Changing desktop's per-OS runtime choice.**
- **Automated renderer coverage:** a device or emulator rendering test stays out of scope.

## Decisions

- **Swap the runtime dependency only.** The runtime is `runtimeOnly`, and the map code is backend-agnostic, so the switch touches only the version catalog and `androidMain`'s dependency.
  - *Rejected: shipping both runtimes and choosing per device.* MapLibre Compose asks for exactly one runtime, and two would roughly double the native libraries.
- **One build for the comparison, no flavor.** Build the same commit twice, once per runtime, and measure each by hand.
  - *Rejected: a `vulkan` product flavor.* It would add permanent build config for a one-off comparison.
- **Pass criteria, all needed to keep Vulkan.** The checks below cover both the detail map and `RouteMapScreen`:
  - The route renders on the Apple Silicon arm64 emulator.
  - It renders on at least one real arm64 phone.
  - The detail list scrolls with no clipping, flicker or black frames.
  - Navigating to the enlarged map and back shows no black frame.
  - The release APK grows by no more than about 10% per ABI.

  Any failure means reverting, and recording what failed.
- **Record the result either way.** The build comment states the backend and the evidence, and BACKLOG.md's Renderer notes says when to revisit.

## Risks / Trade-offs

- **[Risk]** The arm64 emulator on macOS lacks working Vulkan, so the map is black during local dev. → **Mitigation:** this is a fail criterion: revert, and note the emulator as the blocker.
- **[Risk]** A driver bug on older Mali or Adreno GPUs shows up only on devices we don't own. → **Mitigation:** test on the oldest device available, and note the limited device coverage in the BACKLOG.
- **[Risk]** R8 strips something the Vulkan ffi runtime loads reflectively. → **Mitigation:** test a minified release build, not only debug.
- **[Trade-off]** Vulkan brings little speed for a single line layer. The gain is staying with upstream and sharing one backend with desktop, not performance.

## Migration Plan

- **Rollback:** revert the dependency swap in a single commit.
- **No data or server migration.**
