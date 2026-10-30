plugins { id("activitytracker.feature.presenter") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // RecordScreen, ManualActivityScreen and ImportActivityScreen, which the feed opens, until record moves into its own -api
            // module.
            implementation(projects.appScreens)
        }
    }
}
