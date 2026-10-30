plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailScreen, which a saved activity opens.
            implementation(projects.featureDetailApi)
        }
    }
}
