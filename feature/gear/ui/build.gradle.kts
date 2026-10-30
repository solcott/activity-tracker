plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The edit form's photo picker.
            implementation(libs.filekit.dialogs.compose)
        }
    }
}
