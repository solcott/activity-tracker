plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // BlockListScreen, which Settings opens, until profile moves into its own -api module.
            implementation(projects.appScreens)
            // Screens Settings opens: the gear list and moderation.
            implementation(projects.featureGearApi)
            implementation(projects.featureModerationApi)
        }
    }
}
