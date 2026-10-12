---
paths:
  - "build-logic/**"
  - "**/*.gradle.kts"
  - "gradle/libs.versions.toml"
  - "gradle.properties"
  - "config/detekt/**"
  - "config/compose/**"
---

# Build

- Convention plugins live in `build-logic/convention`. Client modules apply `activitytracker.kmp.library` or `activitytracker.kmp.compose`, `androidApp` applies `activitytracker.android.application`, JVM modules (`server-appview`) apply `activitytracker.kotlin.jvm`, the web shell modules apply `activitytracker.kmp.web`, and `desktopApp` applies `activitytracker.kotlin.jvm` plus the Compose plugins. The Android namespace is derived from the module path. Put shared configuration in a convention plugin, not in module scripts. There is no `subprojects {}`/`allprojects {}`, so don't add one.
- Every module gets ktfmt (kotlinlang, width 140), detekt with Compose rules and the repo's own rules (`:detekt-rules`, the `activitytracker` rule set in `detekt.yml`) and sort-dependencies from `activitytracker.quality`. `check` runs `detektAll`: the plain task plus the type-resolution ones (`detektMainJvm`, …), which alone see rules that need types (`UnsafeCallOnNullableType`, `UnusedPrivateProperty`). A new repo rule goes in `:detekt-rules` with a test using `detekt-test`'s `lint`, and is turned on in `detekt.yml`. The Gradle daemon caches detekt's plugin classloader, so after changing a rule run `./gradlew --stop` before checking it on the modules. Don't scope a rule with `includes`/`excludes` paths: each detekt task (whole module or one source set) matches them against a different relative path, so check the file name in the rule instead (`UseSingleFlight`). Run `./gradlew format` after editing build scripts too, because dependency blocks must stay sorted.
- In modules that apply the Compose compiler plugin, `activitytracker.quality` also turns on the compiler's stability reports (`build/compose/reports`), adds `config/compose/stability.conf`, and wires `composeStabilityCheck` into `check`. The check fails on any restartable composable param that isn't `stable`. `composeStability { strictClasses.set(true) }` additionally fails on unstable class fields (on in `:app-screens`). Fix findings with `@Immutable`/`@Stable` or `ImmutableList`; the conf file is only for types we don't own.
- Project paths are flat (`core/data` → `:core-data`) to avoid empty parent projects. Register a new module in the `listOf(...)` in `settings.gradle.kts`.
- `androidApp` is a separate application module (the AGP 9 KMP split), so it can't use Metro intrinsics. Graph creation is exposed from `composeApp` (`createAndroidAppGraph`).
- Every JVM toolchain is Amazon Corretto 25. Configuration cache, build cache and parallel are on. Keep tasks configuration-cache-safe: no `project` access at execution time, and use `isolated.rootProject` for root files.
- Wasm tests are disabled repo-wide in `KmpLibraryConventionPlugin`. Client modules carry a `jvm()` target so tests run on the JVM. That target is also the desktop app's, so anything contributed to `AppScope` in a `jvmMain` ships in `:desktopApp`. Test-only helpers there are plain classes, not contributions. The hierarchy template adds `commonJvm`, `web`, `nonWeb`, `nonAndroid` and `nonMobile` (jvm + web) source sets. Module scripts configure them through the generated accessors, like any target (`nonWebMain.dependencies { ... }`), not `getByName("nonWebMain")`. An `actual` that would be identical on jvm and Android belongs in `commonJvmMain` (or `nonWebMain` if iOS matches too), not in copies.
- iOS test binaries link Swift libraries because cryptography-kotlin uses CryptoKit. The `dev.whyoleg.cryptography` plugin in the library convention handles this. Don't remove it.
- Plugin behaviour is covered by TestKit tests in `build-logic/convention/src/test`. Run them with `./gradlew -p build-logic :convention:test`, and add one when you change a plugin's observable behaviour.
- Dependency versions live only in `gradle/libs.versions.toml`. To find KMP library coordinates or check target support, use the `kotlin-multiplatform-libraries-expert` skill rather than guessing.
- Don't hand-edit `detekt-baseline.xml`. Fix the finding, or regenerate it with `./gradlew detektBaseline` if the user agrees.
