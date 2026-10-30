plugins { id("activitytracker.feature.api") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The gallery's State holds report's Reporting.
            api(projects.featureReportApi)
        }
    }
}
