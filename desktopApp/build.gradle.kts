plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// MapLibre's desktop renderer loads its native library through the FFM API. Declared before use: script vals initialize in order.
val maplibreJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")

// Desktop application shell (the JVM counterpart of :androidApp / :webApp): owns the Metro graph, main() and the OAuth loopback
// listener. Shared UI and JVM actuals live in :composeApp. Mostly a dev target: run it with hot reload (`hotRun --auto`).
dependencies {
    // The graph is generated here, so every module that contributes bindings must be on the classpath.
    implementation(projects.appPresenters)
    implementation(projects.appScreens)
    implementation(projects.composeApp)
    implementation(projects.coreData)
    implementation(projects.featureFeedfilterPresenter)
    implementation(projects.featureFeedfilterUi)
    implementation(projects.featureMapPresenter)
    implementation(projects.featureMapUi)
    implementation(projects.featureMediaPresenter)
    implementation(projects.featureMediaUi)
    implementation(projects.featureModerationPresenter)
    implementation(projects.featureModerationUi)
    implementation(projects.featureReportPresenter)
    implementation(projects.featureReportUi)
    implementation(projects.featureSearchPresenter)
    implementation(projects.featureSearchUi)
    implementation(projects.uiResources)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.maplibre.compose)

    runtimeOnly(maplibreDesktopRuntime())

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "io.github.solcott.activitytracker.desktop.MainKt"
        jvmArgs += maplibreJvmArgs
    }
}

// Compose Hot Reload launches through its own tasks (hotRun, hotRunAsync), not `run`, so configure both. `-PoauthPort=` moves the
// OAuth loopback listener off its default port, e.g. to run two instances.
tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("hotRun")) jvmArgs(maplibreJvmArgs) // `run` gets them from compose.desktop.application.
    if (name == "run" || name.startsWith("hotRun")) {
        providers.gradleProperty("oauthPort").orNull?.let { systemProperty("activitytracker.oauthPort", it) }
    }
}

/** MapLibre's native renderer for the OS building the app (like compose.desktop.currentOs): Metal on macOS, Vulkan elsewhere. */
fun maplibreDesktopRuntime(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
    val platform =
        when {
            os.startsWith("mac") -> "metal-macos-arm64"
            os.startsWith("windows") -> "vulkan-windows-$arch"
            else -> "vulkan-linux-$arch"
        }
    return "org.maplibre.compose:maplibre-compose-runtime-$platform:${libs.versions.maplibreCompose.get()}"
}
