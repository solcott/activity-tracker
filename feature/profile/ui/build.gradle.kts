plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The edit form's avatar picker, and its compression off the web.
            implementation(libs.filekit.dialogs.compose)
        }
    }
}
