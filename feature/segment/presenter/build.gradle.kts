plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailScreen and ProfileScreen, which an effort and a leaderboard row open, until detail and profile move into their
            // own -api modules.
            implementation(projects.appScreens)
        }
    }
}
