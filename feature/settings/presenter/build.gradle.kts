plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens Settings opens: the gear list, moderation and the block lists.
            implementation(projects.featureGearApi)
            implementation(projects.featureModerationApi)
            implementation(projects.featureProfileApi)
        }
    }
}
