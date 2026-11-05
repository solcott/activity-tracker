plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // SegmentBrowseScreen, which the tab shows in its Segments mode.
            implementation(projects.featureSegmentApi)
        }
    }
}
