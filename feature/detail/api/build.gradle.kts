plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ActivityDetailState holds report's Reporting.
            api(projects.featureReportApi)
        }
    }
}
