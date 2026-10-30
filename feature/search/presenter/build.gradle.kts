plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ProfileScreen, which a result opens, until profile moves into its own -api module.
            implementation(projects.appScreens)
        }
    }
}
