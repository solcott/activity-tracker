---
paths:
  - "build-logic/**"
  - "**/*.gradle.kts"
  - "gradle/libs.versions.toml"
  - "gradle.properties"
  - "config/detekt/**"
---

# Build

- Convention plugins live in `build-logic/convention`. Client modules apply `activitytracker.kmp.library` or `activitytracker.kmp.compose`, `androidApp` applies `activitytracker.android.application`, JVM modules (`server-appview`) apply `activitytracker.kotlin.jvm`, and the web shell modules apply `activitytracker.kmp.web`. The Android namespace is derived from the module path. Put shared configuration in a convention plugin, not in module scripts. There is no `subprojects {}`/`allprojects {}`, so don't add one.
- Every module gets ktfmt (kotlinlang, width 140), detekt with Compose rules and sort-dependencies from `activitytracker.quality`. Run `./gradlew format` after editing build scripts too, because dependency blocks must stay sorted.
- Project paths are flat (`core/data` → `:core-data`) to avoid empty parent projects. Register a new module in the `listOf(...)` in `settings.gradle.kts`.
- `androidApp` is a separate application module (the AGP 9 KMP split), so it can't use Metro intrinsics. Graph creation is exposed from `composeApp` (`createAndroidAppGraph`).
- Every JVM toolchain is Amazon Corretto 25. Configuration cache, build cache and parallel are on. Keep tasks configuration-cache-safe: no `project` access at execution time, and use `isolated.rootProject` for root files.
- Wasm tests are disabled repo-wide in `KmpLibraryConventionPlugin`. Client modules carry a `jvm()` target so tests run on the JVM.
- iOS test binaries link Swift libraries because cryptography-kotlin uses CryptoKit. The `dev.whyoleg.cryptography` plugin in the library convention handles this. Don't remove it.
- Plugin behaviour is covered by TestKit tests in `build-logic/convention/src/test`. Run them with `./gradlew -p build-logic :convention:test`, and add one when you change a plugin's observable behaviour.
- Dependency versions live only in `gradle/libs.versions.toml`. To find KMP library coordinates or check target support, use the `kotlin-multiplatform-libraries-expert` skill rather than guessing.
- Don't hand-edit `detekt-baseline.xml`. Fix the finding, or regenerate it with `./gradlew detektBaseline` if the user agrees.
