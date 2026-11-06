plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // PostPage holds report's Reporting.
            api(projects.featureReportApi)
        }
    }
}
