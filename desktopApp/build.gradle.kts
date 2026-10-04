plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

// Desktop application shell (the JVM counterpart of :androidApp / :webApp): owns the Metro graph, main() and the OAuth loopback
// listener. Shared UI and JVM actuals live in :composeApp. Mostly a dev target: run it with hot reload (`hotRun --auto`).
dependencies {
    // The graph is generated here, so every module that contributes bindings must be on the classpath.
    implementation(projects.appPresenters)
    implementation(projects.appScreens)
    implementation(projects.composeApp)
    implementation(projects.coreData)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop { application { mainClass = "io.github.solcott.activitytracker.desktop.MainKt" } }

// Compose Hot Reload launches through its own tasks (hotRun, hotRunAsync), not `run`, so configure both. `-PoauthPort=` moves the
// OAuth loopback listener off its default port, e.g. to run two instances.
tasks.withType<JavaExec>().configureEach {
    if (name == "run" || name.startsWith("hotRun")) {
        providers.gradleProperty("oauthPort").orNull?.let { systemProperty("activitytracker.oauthPort", it) }
    }
}
