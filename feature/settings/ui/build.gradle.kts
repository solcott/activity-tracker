plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The list's rows open the gear list and the blocked and muted lists, and mark the one shown beside it.
            implementation(projects.featureGearApi)
            implementation(projects.featureProfileApi)
        }
    }
}
