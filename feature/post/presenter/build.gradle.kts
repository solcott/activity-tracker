plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The post's comments, on their own screen.
            implementation(projects.featureCommentsApi)
        }
    }
}
