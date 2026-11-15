plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The name scope, browse start and list choices dive site browse shares with segment browse.
            api(projects.featureSegmentApi)
        }
    }
}
