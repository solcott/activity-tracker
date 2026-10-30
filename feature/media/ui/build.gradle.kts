plugins { id("activitytracker.feature.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Pinch, double-tap, wheel and keyboard zoom on the gallery's photos.
            implementation(libs.zoomimage.compose.coil3)
        }
    }
}
