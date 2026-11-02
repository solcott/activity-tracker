plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens the feed opens: recording, manual entry and import, and the upload queue.
            implementation(projects.featureRecordApi)
            implementation(projects.featureUploadsApi)
        }
    }
}
