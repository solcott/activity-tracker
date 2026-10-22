plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

// Turns public trail data into candidate segments (segments/trail-candidates), outside the AppView, which stays an indexer.
application { mainClass.set("io.github.solcott.activitytracker.segmentimporter.MainKt") }

// Relative paths in --args resolve from the repo root, where an operator runs ./gradlew. The service account's settings come from the
// environment, filled from docker/.env when unset there, as the AppView's are; read at execution, not during configuration.
tasks.named<JavaExec>(ApplicationPlugin.TASK_RUN_NAME) {
    val dockerEnv = rootDir.resolve("docker/.env")
    workingDir = rootDir
    doFirst {
        with(this as JavaExec) {
            val lines = dockerEnv.takeIf { it.isFile }?.readLines()?.map(String::trim).orEmpty()
            for (key in
                listOf(
                    "SEGMENT_IMPORTER_IDENTIFIER",
                    "SEGMENT_IMPORTER_PASSWORD",
                    "SEGMENT_IMPORTER_PDS_URL",
                    "APPVIEW_URL",
                    "APPVIEW_DID",
                )) {
                if (key in environment) continue
                lines.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.takeIf(String::isNotBlank)?.let { environment(key, it) }
            }
        }
    }
}

dependencies {
    // XRPC on Ktor and service auth for the AppView: `stage` and `publish` act as the importer's service account.
    implementation(projects.coreAtproto)
    // Sport groups and segment sources.
    implementation(projects.coreLexicon)
    // Segment geometry (polyline encoding, length, the duplicate test, cells) and simplification, shared with the AppView.
    implementation(projects.coreLocation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.osmpbf)
    // Ktor's and core-atproto's logs go through SLF4J; warnings only, so a command prints just its own summary.
    runtimeOnly(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
