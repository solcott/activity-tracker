plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Screens a segment opens: an effort's activity and a leaderboard row's profile.
            implementation(projects.featureDetailApi)
            implementation(projects.featureProfileApi)
        }
    }
}
