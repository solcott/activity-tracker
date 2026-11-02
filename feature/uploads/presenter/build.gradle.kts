plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailScreen, which an item opens.
            implementation(projects.featureDetailApi)
        }
    }
}
