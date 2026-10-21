plugins {
    id("activitytracker.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

// Turns public trail data into candidate segments (segments/trail-candidates), outside the AppView, which stays an indexer.
application { mainClass.set("io.github.solcott.activitytracker.segmentimporter.MainKt") }

// Relative paths in --args resolve from the repo root, where an operator runs ./gradlew.
tasks.named<JavaExec>(ApplicationPlugin.TASK_RUN_NAME) { workingDir = rootDir }

dependencies {
    // Sport groups and segment sources.
    implementation(projects.coreLexicon)
    // Segment geometry (polyline encoding, length, the duplicate test, cells) and simplification, shared with the AppView.
    implementation(projects.coreLocation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.osmpbf)

    testImplementation(kotlin("test"))
}
