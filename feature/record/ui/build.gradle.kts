plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The import screen's file picker.
            implementation(libs.filekit.dialogs.compose)
        }
    }
}
