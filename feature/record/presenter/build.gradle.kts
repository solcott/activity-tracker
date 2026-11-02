plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailScreen, which a saved activity opens.
            implementation(projects.featureDetailApi)
            // UploadsScreen, which a saved batch opens.
            implementation(projects.featureUploadsApi)
        }
    }
}
