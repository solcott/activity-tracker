plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // CommentsScreenState holds report's Reporting.
            api(projects.featureReportApi)
        }
    }
}
