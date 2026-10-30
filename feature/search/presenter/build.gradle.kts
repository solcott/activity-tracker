plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ProfileScreen, which a result opens.
            implementation(projects.featureProfileApi)
        }
    }
}
