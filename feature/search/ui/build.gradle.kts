plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // SegmentBrowseScreen and DiveSiteBrowseScreen, which the tab shows in its Segments and Dive sites modes.
            implementation(projects.featureDivesiteApi)
            implementation(projects.featureSegmentApi)
        }
    }
}
