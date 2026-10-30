plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailScreen, which a saved activity opens, until detail moves into its own -api module.
            implementation(projects.appScreens)
        }
    }
}
