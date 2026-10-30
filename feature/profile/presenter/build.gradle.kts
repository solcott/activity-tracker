plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens the profile opens: starred segments and settings.
            implementation(projects.featureSegmentApi)
            implementation(projects.featureSettingsApi)
        }
    }
}
