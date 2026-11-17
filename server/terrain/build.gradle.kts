plugins { id("activitytracker.kotlin.jvm") }

// Terrain heights from elevation tiles (segments/segment-elevation), shared by the AppView, which gives segments terrain elevation, and the
// segment importer, which names two-way trails by their climb (segments/segment-direction), so both read tiles the same way.
dependencies {
    // Polyline sampling, smoothing and gain and loss, shared with clients.
    api(projects.coreLocation)
    implementation(libs.caffeine)

    testImplementation(kotlin("test"))
}
