plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens a site opens: a dive at it and a Regular's profile.
            implementation(projects.featureDetailApi)
            implementation(projects.featureProfileApi)
        }
    }
}
