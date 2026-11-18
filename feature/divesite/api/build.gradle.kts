plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // PlaceTexts: a site's creator, credit, licence and status are worded as a segment's.
            api(projects.featureSegmentApi)
        }
    }
}
