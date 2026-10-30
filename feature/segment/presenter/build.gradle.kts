plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailScreen, which an effort opens, until detail moves into its own -api module.
            implementation(projects.appScreens)
            // ProfileScreen, which a leaderboard row opens.
            implementation(projects.featureProfileApi)
        }
    }
}
