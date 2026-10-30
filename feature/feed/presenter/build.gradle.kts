plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens the feed opens: recording, manual entry and import.
            implementation(projects.featureRecordApi)
        }
    }
}
