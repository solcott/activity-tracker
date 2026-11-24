plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // TrailReportsState and TrailPhotosState hold report's Reporting.
            api(projects.featureReportApi)
        }
    }
}
